package com.exam.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.BusinessException;
import com.exam.common.Consts;
import com.exam.config.LoginUser;
import com.exam.entity.*;
import com.exam.mapper.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GradingService {

    private final ExamRecordMapper recordMapper;
    private final AnswerRecordMapper answerMapper;
    private final QuestionMapper questionMapper;
    private final ReviewLogMapper reviewLogMapper;
    private final ExamCandidateMapper candidateMapper;
    private final ExamProjectMapper projectMapper;
    private final SysUserMapper userMapper;
    private final AiService aiService;
    private final ProjectService projectService;

    public GradingService(ExamRecordMapper recordMapper, AnswerRecordMapper answerMapper,
                          QuestionMapper questionMapper, ReviewLogMapper reviewLogMapper,
                          ExamCandidateMapper candidateMapper, ExamProjectMapper projectMapper,
                          SysUserMapper userMapper, AiService aiService, @Lazy ProjectService projectService) {
        this.recordMapper = recordMapper;
        this.answerMapper = answerMapper;
        this.questionMapper = questionMapper;
        this.reviewLogMapper = reviewLogMapper;
        this.candidateMapper = candidateMapper;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
        this.aiService = aiService;
        this.projectService = projectService;
    }

    /** 交卷：客观题立即判分，主观题异步 AI 初评 */
    @Transactional
    public void submit(ExamRecord record, String submitType) {
        record.setSubmitTime(LocalDateTime.now());
        record.setSubmitType(submitType);
        record.setStatus(Consts.REC_SUBMITTED);

        List<AnswerRecord> answers = answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getRecordId, record.getId()));
        BigDecimal objective = BigDecimal.ZERO;
        boolean hasEssay = false;
        for (AnswerRecord ar : answers) {
            Question q = questionMapper.selectById(ar.getQuestionId());
            if (Consts.Q_ESSAY.equals(q.getType())) {
                hasEssay = true;
                ar.setStatus(Consts.ANS_PENDING);
                answerMapper.updateById(ar);
            } else {
                BigDecimal score = gradeObjective(q, ar.getAnswer(), ar.getFullScore());
                ar.setScore(score);
                ar.setStatus(Consts.ANS_REVIEWED);
                answerMapper.updateById(ar);
                objective = objective.add(score);
            }
        }
        record.setObjectiveScore(objective);
        if (!hasEssay) {
            record.setSubjectiveScore(BigDecimal.ZERO);
            record.setTotalScore(objective);
            record.setAiDone(1);
            record.setReviewDone(1);
            record.setStatus(Consts.REC_GRADED);
        }
        recordMapper.updateById(record);

        ExamCandidate candidate = candidateMapper.selectById(record.getCandidateId());
        if (candidate != null) {
            candidate.setStatus("SUBMITTED");
            candidateMapper.updateById(candidate);
        }
        if (!hasEssay) {
            recomputeProject(record.getProjectId());
        } else {
            aiGradeAsync(record.getId());
        }
    }

    /** 客观题判分：单选/判断精确匹配；多选全对满分、漏选半分、错选0分 */
    public BigDecimal gradeObjective(Question q, String answer, BigDecimal fullScore) {
        if (StrUtil.isBlank(answer)) return BigDecimal.ZERO;
        String correct = StrUtil.nullToEmpty(q.getAnswer()).trim().toUpperCase();
        String given = answer.trim().toUpperCase();
        if (Consts.Q_MULTI.equals(q.getType())) {
            String c = sortChars(correct);
            String g = sortChars(given);
            if (c.equals(g)) return fullScore;
            if (!g.isEmpty() && c.contains(g) && g.chars().distinct().count() == g.length()) {
                return fullScore.multiply(new BigDecimal("0.5")).setScale(1, RoundingMode.HALF_UP);
            }
            return BigDecimal.ZERO;
        }
        return correct.equals(given) ? fullScore : BigDecimal.ZERO;
    }

    private String sortChars(String s) {
        char[] chars = s.replaceAll("[^A-Z]", "").toCharArray();
        Arrays.sort(chars);
        return new String(chars);
    }

    @Async("aiExecutor")
    public void aiGradeAsync(Long recordId) {
        try {
            // 等待事务提交
            Thread.sleep(500);
            ExamRecord record = recordMapper.selectById(recordId);
            if (record == null) return;
            List<AnswerRecord> essays = answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                    .eq(AnswerRecord::getRecordId, recordId)
                    .eq(AnswerRecord::getType, Consts.Q_ESSAY));
            for (AnswerRecord ar : essays) {
                if (Consts.ANS_REVIEWED.equals(ar.getStatus())) continue;
                Question q = questionMapper.selectById(ar.getQuestionId());
                AiService.AiScore score = aiService.scoreEssay(q, ar.getAnswer(), ar.getFullScore());
                ar.setAiScore(score.getScore());
                ar.setAiComment(score.getComment());
                ar.setStatus(Consts.ANS_AI_DONE);
                answerMapper.updateById(ar);
                writeReviewLog(ar, null, "AI_SCORE", null, score.getScore(), score.getComment());
            }
            record.setAiDone(1);
            recordMapper.updateById(record);
        } catch (Exception e) {
            log.error("AI 初评失败 record={}", recordId, e);
        }
    }

    /** 阅卷人复核单题 */
    @Transactional
    public AnswerRecord review(Long answerId, BigDecimal score, String comment) {
        LoginUser u = LoginUser.get();
        AnswerRecord ar = answerMapper.selectById(answerId);
        if (ar == null) throw new BusinessException("答题记录不存在");
        if (!Consts.Q_ESSAY.equals(ar.getType())) throw new BusinessException("客观题由系统自动判分，无需复核");
        ExamRecord record = recordMapper.selectById(ar.getRecordId());
        ExamProject project = projectMapper.selectById(record.getProjectId());
        projectService.checkRead(project);
        if (Consts.REC_GRADED.equals(record.getStatus())) throw new BusinessException("该记录已完成总分合成，不可再修改");
        if (score == null || score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(ar.getFullScore()) > 0) {
            throw new BusinessException("分数须在 0 ~ " + ar.getFullScore() + " 之间");
        }
        BigDecimal old = ar.getReviewScore() != null ? ar.getReviewScore() : ar.getAiScore();
        boolean confirm = ar.getAiScore() != null && score.compareTo(ar.getAiScore()) == 0;
        ar.setReviewScore(score);
        ar.setScore(score);
        ar.setReviewerId(u.getId());
        ar.setReviewTime(LocalDateTime.now());
        ar.setReviewComment(comment);
        ar.setStatus(Consts.ANS_REVIEWED);
        answerMapper.updateById(ar);
        writeReviewLog(ar, u.getId(), confirm ? "CONFIRM" : "ADJUST", old, score, comment);

        Long pending = answerMapper.selectCount(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getRecordId, record.getId())
                .eq(AnswerRecord::getType, Consts.Q_ESSAY)
                .ne(AnswerRecord::getStatus, Consts.ANS_REVIEWED));
        if (pending == null || pending == 0) {
            record.setReviewDone(1);
            recordMapper.updateById(record);
        }
        return ar;
    }

    private void writeReviewLog(AnswerRecord ar, Long reviewerId, String action,
                                BigDecimal oldScore, BigDecimal newScore, String comment) {
        ReviewLog l = new ReviewLog();
        l.setAnswerId(ar.getId());
        l.setRecordId(ar.getRecordId());
        l.setReviewerId(reviewerId);
        l.setAction(action);
        l.setOldScore(oldScore);
        l.setNewScore(newScore);
        l.setComment(StrUtil.maxLength(comment, 500));
        reviewLogMapper.insert(l);
    }

    /** 合成总分：客观自动分 + 主观复核分 */
    @Transactional
    public ExamRecord finalizeRecord(Long recordId) {
        ExamRecord record = recordMapper.selectById(recordId);
        if (record == null) throw new BusinessException("考试记录不存在");
        projectService.checkRead(projectMapper.selectById(record.getProjectId()));
        if (record.getAbsent() != null && record.getAbsent() == 1) throw new BusinessException("缺考记录无需合成");
        if (!Consts.REC_SUBMITTED.equals(record.getStatus()) && !Consts.REC_GRADED.equals(record.getStatus())) {
            throw new BusinessException("考生尚未交卷");
        }
        List<AnswerRecord> essays = answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getRecordId, recordId).eq(AnswerRecord::getType, Consts.Q_ESSAY));
        for (AnswerRecord ar : essays) {
            if (!Consts.ANS_REVIEWED.equals(ar.getStatus())) {
                throw new BusinessException("仍有主观题未完成人工复核（题 #" + ar.getQuestionId() + "）");
            }
        }
        BigDecimal subjective = essays.stream().map(a -> a.getScore() == null ? BigDecimal.ZERO : a.getScore())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        record.setSubjectiveScore(subjective);
        record.setTotalScore(record.getObjectiveScore().add(subjective));
        record.setReviewDone(1);
        record.setStatus(Consts.REC_GRADED);
        recordMapper.updateById(record);
        recomputeProject(record.getProjectId());
        return recordMapper.selectById(recordId);
    }

    /** 项目内排名与及格判定 */
    public void recomputeProject(Long projectId) {
        ExamProject project = projectMapper.selectById(projectId);
        List<ExamRecord> records = recordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getProjectId, projectId)
                .eq(ExamRecord::getStatus, Consts.REC_GRADED));
        records.sort((a, b) -> b.getTotalScore().compareTo(a.getTotalScore()));
        int rank = 0;
        for (ExamRecord r : records) {
            rank++;
            r.setRankNo(rank);
            r.setPassed(project.getPassScore() != null
                    && r.getTotalScore().compareTo(project.getPassScore()) >= 0 ? 1 : 0);
            recordMapper.updateById(r);
        }
    }

    /** 阅卷工作台列表 */
    public Page<ExamRecord> gradingPage(int pageNum, int pageSize, Long projectId, String state) {
        LoginUser u = LoginUser.get();
        List<Long> projectIds;
        if (projectId != null) {
            projectService.checkRead(projectMapper.selectById(projectId));
            projectIds = Collections.singletonList(projectId);
        } else if (u.isPm()) {
            projectIds = projectMapper.selectList(null).stream().map(ExamProject::getId).collect(Collectors.toList());
        } else {
            projectIds = projectMapper.selectList(new LambdaQueryWrapper<ExamProject>()
                    .eq(ExamProject::getDeptId, u.getDeptId())).stream()
                    .map(ExamProject::getId).collect(Collectors.toList());
        }
        if (projectIds.isEmpty()) return new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ExamRecord> w = new LambdaQueryWrapper<ExamRecord>()
                .in(ExamRecord::getProjectId, projectIds)
                .in(ExamRecord::getStatus, Arrays.asList(Consts.REC_SUBMITTED, Consts.REC_GRADED))
                .eq(ExamRecord::getAbsent, 0);
        if ("pending".equals(state)) w.eq(ExamRecord::getReviewDone, 0);
        if ("done".equals(state)) w.eq(ExamRecord::getStatus, Consts.REC_GRADED);
        w.orderByDesc(ExamRecord::getSubmitTime);
        Page<ExamRecord> page = recordMapper.selectPage(new Page<>(pageNum, pageSize), w);
        fillRecordNames(page.getRecords());
        return page;
    }

    public void fillRecordNames(List<ExamRecord> records) {
        if (records.isEmpty()) return;
        Map<Long, ExamCandidate> candidates = candidateMapper.selectList(null).stream()
                .collect(Collectors.toMap(ExamCandidate::getId, c -> c));
        Map<Long, String> projects = projectMapper.selectList(null).stream()
                .collect(Collectors.toMap(ExamProject::getId, ExamProject::getName));
        for (ExamRecord r : records) {
            ExamCandidate c = candidates.get(r.getCandidateId());
            if (c != null) {
                r.setCandidateName(c.getName());
                r.setIdCard(c.getIdCard());
                r.setVendor(c.getVendor());
            }
            r.setProjectName(projects.get(r.getProjectId()));
        }
    }

    /** 阅卷详情：含题目、参考答案、评分要点、AI 初评、复核信息 */
    public Map<String, Object> gradingDetail(Long recordId) {
        ExamRecord record = recordMapper.selectById(recordId);
        if (record == null) throw new BusinessException("考试记录不存在");
        projectService.checkRead(projectMapper.selectById(record.getProjectId()));
        fillRecordNames(Collections.singletonList(record));
        List<AnswerRecord> answers = answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getRecordId, recordId).orderByAsc(AnswerRecord::getSortOrder));
        Map<Long, String> userNames = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName));
        for (AnswerRecord ar : answers) {
            ar.setQuestion(questionMapper.selectById(ar.getQuestionId()));
            ar.setReviewerName(userNames.get(ar.getReviewerId()));
        }
        List<ReviewLog> logs = reviewLogMapper.selectList(new LambdaQueryWrapper<ReviewLog>()
                .eq(ReviewLog::getRecordId, recordId).orderByAsc(ReviewLog::getId));
        logs.forEach(l -> l.setReviewerName(userNames.get(l.getReviewerId())));
        Map<String, Object> res = new HashMap<>();
        res.put("record", record);
        res.put("answers", answers);
        res.put("reviewLogs", logs);
        return res;
    }
}