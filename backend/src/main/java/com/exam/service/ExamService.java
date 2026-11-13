package com.exam.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.BusinessException;
import com.exam.common.Consts;
import com.exam.config.LoginUser;
import com.exam.entity.*;
import com.exam.mapper.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ExamService {

    private final ExamProjectMapper projectMapper;
    private final ExamCandidateMapper candidateMapper;
    private final ExamRecordMapper recordMapper;
    private final AnswerRecordMapper answerMapper;
    private final PaperQuestionMapper pqMapper;
    private final QuestionMapper questionMapper;
    private final CheatEventMapper cheatMapper;
    private final GradingService gradingService;
    private final ProjectService projectService;

    public ExamService(ExamProjectMapper projectMapper, ExamCandidateMapper candidateMapper,
                       ExamRecordMapper recordMapper, AnswerRecordMapper answerMapper,
                       PaperQuestionMapper pqMapper, QuestionMapper questionMapper,
                       CheatEventMapper cheatMapper, GradingService gradingService,
                       ProjectService projectService) {
        this.projectMapper = projectMapper;
        this.candidateMapper = candidateMapper;
        this.recordMapper = recordMapper;
        this.answerMapper = answerMapper;
        this.pqMapper = pqMapper;
        this.questionMapper = questionMapper;
        this.cheatMapper = cheatMapper;
        this.gradingService = gradingService;
        this.projectService = projectService;
    }

    @Transactional
    public Map<String, Object> enter(Long projectId) {
        ExamCandidate candidate = projectCandidate(projectId);
        Long candidateId = candidate.getId();
        ExamProject project = projectMapper.selectById(projectId);
        if (project == null || !Consts.PROJECT_PUBLISHED.equals(project.getStatus())) {
            throw new BusinessException("项目不存在或未发布");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(project.getStartTime())) throw new BusinessException("考试尚未开始");
        if (now.isAfter(project.getEndTime())) throw new BusinessException("考试已结束");

        ExamRecord record = recordMapper.selectOne(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getCandidateId, candidateId));
        if (record == null) {
            record = new ExamRecord();
            record.setProjectId(projectId);
            record.setCandidateId(candidateId);
            record.setPaperId(project.getPaperId());
            record.setStartTime(now);
            record.setDeadline(min(now.plusMinutes(project.getDurationMin()), project.getEndTime()));
            record.setStatus(Consts.REC_IN_PROGRESS);
            record.setAbsent(0);
            record.setSwitchCount(0);
            record.setAiDone(0);
            record.setReviewDone(0);
            record.setShortlisted(0);
            recordMapper.insert(record);
            initAnswers(record);
            candidate.setStatus("ATTENDED");
            candidateMapper.updateById(candidate);
        } else if (!Consts.REC_IN_PROGRESS.equals(record.getStatus())) {
            throw new BusinessException("您已交卷，无法再次进入");
        } else if (now.isAfter(record.getDeadline())) {
            gradingService.submit(record, "AUTO");
            throw new BusinessException("考试时间已结束，系统已自动交卷");
        }

        return buildExamPaper(record, project);
    }

    private LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private void initAnswers(ExamRecord record) {
        List<PaperQuestion> pqs = pqMapper.selectList(new LambdaQueryWrapper<PaperQuestion>()
                .eq(PaperQuestion::getPaperId, record.getPaperId()).orderByAsc(PaperQuestion::getSortOrder));
        for (PaperQuestion pq : pqs) {
            Question q = questionMapper.selectById(pq.getQuestionId());
            AnswerRecord ar = new AnswerRecord();
            ar.setRecordId(record.getId());
            ar.setQuestionId(pq.getQuestionId());
            ar.setType(q.getType());
            ar.setFullScore(pq.getScore());
            ar.setSortOrder(pq.getSortOrder());
            ar.setStatus(Consts.ANS_PENDING);
            ar.setAnswer("");
            answerMapper.insert(ar);
        }
    }

    private Map<String, Object> buildExamPaper(ExamRecord record, ExamProject project) {
        List<AnswerRecord> answers = answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getRecordId, record.getId()).orderByAsc(AnswerRecord::getSortOrder));
        List<Map<String, Object>> questions = new ArrayList<>();
        Map<Long, String> saved = new HashMap<>();
        for (AnswerRecord ar : answers) {
            Question q = questionMapper.selectById(ar.getQuestionId());
            Map<String, Object> item = new HashMap<>();
            item.put("answerId", ar.getId());
            item.put("questionId", q.getId());
            item.put("type", q.getType());
            item.put("content", q.getContent());
            item.put("options", q.getOptionsJson() == null ? Collections.emptyList()
                    : JSONUtil.toList(q.getOptionsJson(), String.class));
            item.put("score", ar.getFullScore());
            item.put("sortOrder", ar.getSortOrder());
            questions.add(item);
            saved.put(ar.getQuestionId(), ar.getAnswer());
        }
        Map<String, Object> res = new HashMap<>();
        res.put("recordId", record.getId());
        res.put("projectId", project.getId());
        res.put("projectName", project.getName());
        res.put("durationMin", project.getDurationMin());
        res.put("deadline", record.getDeadline());
        res.put("serverTime", LocalDateTime.now());
        res.put("remainSeconds", Math.max(0, Duration.between(LocalDateTime.now(), record.getDeadline()).getSeconds()));
        res.put("switchCount", record.getSwitchCount());
        res.put("questions", questions);
        res.put("savedAnswers", saved);
        return res;
    }

    @Transactional
    public Map<String, Object> save(Long recordId, List<Map<String, Object>> answers) {
        ExamRecord record = ownRecord(recordId);
        if (!Consts.REC_IN_PROGRESS.equals(record.getStatus())) {
            throw new BusinessException("已交卷，无法保存");
        }
        if (LocalDateTime.now().isAfter(record.getDeadline())) {
            gradingService.submit(record, "AUTO");
            Map<String, Object> res = new HashMap<>();
            res.put("autoSubmitted", true);
            return res;
        }
        if (answers != null) {
            for (Map<String, Object> item : answers) {
                Long questionId = Long.valueOf(item.get("questionId").toString());
                String answer = item.get("answer") == null ? "" : item.get("answer").toString();
                AnswerRecord ar = answerMapper.selectOne(new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getRecordId, recordId).eq(AnswerRecord::getQuestionId, questionId));
                if (ar != null) {
                    ar.setAnswer(answer);
                    answerMapper.updateById(ar);
                }
            }
        }
        Map<String, Object> res = new HashMap<>();
        res.put("autoSubmitted", false);
        res.put("serverTime", LocalDateTime.now());
        res.put("remainSeconds", Math.max(0, Duration.between(LocalDateTime.now(), record.getDeadline()).getSeconds()));
        return res;
    }

    @Transactional
    public Map<String, Object> submit(Long recordId) {
        ExamRecord record = ownRecord(recordId);
        if (!Consts.REC_IN_PROGRESS.equals(record.getStatus())) {
            throw new BusinessException("请勿重复交卷");
        }
        gradingService.submit(record, "MANUAL");
        Map<String, Object> res = new HashMap<>();
        res.put("submitTime", record.getSubmitTime());
        res.put("recordId", recordId);
        return res;
    }

    public Map<String, Object> status(Long recordId) {
        ExamRecord record = ownRecord(recordId);
        if (Consts.REC_IN_PROGRESS.equals(record.getStatus())
                && LocalDateTime.now().isAfter(record.getDeadline())) {
            gradingService.submit(record, "AUTO");
        }
        Map<String, Object> res = new HashMap<>();
        res.put("status", record.getStatus());
        res.put("remainSeconds", Math.max(0, Duration.between(LocalDateTime.now(), record.getDeadline()).getSeconds()));
        res.put("serverTime", LocalDateTime.now());
        return res;
    }

    public void reportCheat(Long recordId, String eventType, String detail) {
        ExamRecord record = ownRecord(recordId);
        if (!Consts.REC_IN_PROGRESS.equals(record.getStatus())) return;
        CheatEvent event = new CheatEvent();
        event.setRecordId(recordId);
        event.setEventType(eventType);
        event.setDetail(detail);
        cheatMapper.insert(event);
        if ("BLUR".equals(eventType) || "VISIBILITY".equals(eventType)) {
            record.setSwitchCount((record.getSwitchCount() == null ? 0 : record.getSwitchCount()) + 1);
            recordMapper.updateById(record);
        }
    }

    /** token 只绑定登录时的一条考生记录，同一身份证在项目中有独立记录，需按身份证定位 */
    private ExamCandidate projectCandidate(Long projectId) {
        ExamCandidate login = candidateMapper.selectById(LoginUser.get().getId());
        if (login != null) {
            ExamCandidate candidate = candidateMapper.selectOne(new LambdaQueryWrapper<ExamCandidate>()
                    .eq(ExamCandidate::getProjectId, projectId)
                    .eq(ExamCandidate::getIdCard, login.getIdCard()));
            if (candidate != null) return candidate;
        }
        throw new BusinessException(403, "您不在该项目考生名单中");
    }

    private ExamRecord ownRecord(Long recordId) {
        ExamRecord record = recordMapper.selectById(recordId);
        if (record == null) throw new BusinessException(403, "考试记录不存在");
        ExamCandidate login = candidateMapper.selectById(LoginUser.get().getId());
        ExamCandidate owner = candidateMapper.selectById(record.getCandidateId());
        if (login == null || owner == null || !Objects.equals(login.getIdCard(), owner.getIdCard())) {
            throw new BusinessException(403, "考试记录不存在");
        }
        return record;
    }

    /** 每 30 秒：超时自动交卷；到期项目记缺考并结束 */
    @Scheduled(fixedDelay = 30000)
    public void autoSubmitTask() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<ExamRecord> expired = recordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                    .eq(ExamRecord::getStatus, Consts.REC_IN_PROGRESS)
                    .lt(ExamRecord::getDeadline, now));
            for (ExamRecord r : expired) {
                try {
                    gradingService.submit(r, "AUTO");
                    log.info("自动交卷 record={}", r.getId());
                } catch (Exception e) {
                    log.error("自动交卷失败 record={}", r.getId(), e);
                }
            }
            List<ExamProject> ended = projectMapper.selectList(new LambdaQueryWrapper<ExamProject>()
                    .eq(ExamProject::getStatus, Consts.PROJECT_PUBLISHED)
                    .lt(ExamProject::getEndTime, now));
            for (ExamProject p : ended) {
                try {
                    projectAutoFinish(p);
                } catch (Exception e) {
                    log.error("项目自动结束失败 project={}", p.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("自动交卷任务异常", e);
        }
    }

    @Transactional
    public void projectAutoFinish(ExamProject p) {
        projectService.markAbsent(p);
        p.setStatus(Consts.PROJECT_FINISHED);
        projectMapper.updateById(p);
    }
}