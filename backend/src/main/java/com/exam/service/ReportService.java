package com.exam.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.BusinessException;
import com.exam.common.Consts;
import com.exam.entity.*;
import com.exam.mapper.*;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final ExamProjectMapper projectMapper;
    private final ExamRecordMapper recordMapper;
    private final AnswerRecordMapper answerMapper;
    private final ExamCandidateMapper candidateMapper;
    private final QuestionMapper questionMapper;
    private final PaperMapper paperMapper;
    private final SysUserMapper userMapper;
    private final GradingService gradingService;
    private final ProjectService projectService;

    public ReportService(ExamProjectMapper projectMapper, ExamRecordMapper recordMapper,
                         AnswerRecordMapper answerMapper, ExamCandidateMapper candidateMapper,
                         QuestionMapper questionMapper, PaperMapper paperMapper,
                         SysUserMapper userMapper, GradingService gradingService, ProjectService projectService) {
        this.projectMapper = projectMapper;
        this.recordMapper = recordMapper;
        this.answerMapper = answerMapper;
        this.candidateMapper = candidateMapper;
        this.questionMapper = questionMapper;
        this.paperMapper = paperMapper;
        this.userMapper = userMapper;
        this.gradingService = gradingService;
        this.projectService = projectService;
    }

    public List<ExamRecord> scores(Long projectId) {
        projectService.get(projectId);
        List<ExamRecord> records = recordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getProjectId, projectId)
                .orderByAsc(ExamRecord::getRankNo).orderByDesc(ExamRecord::getTotalScore));
        gradingService.fillRecordNames(records);
        return records;
    }

    /** 整体统计分析：基础统计 + 分数分布 + 及格率 + 逐题难度与区分度 */
    public Map<String, Object> analysis(Long projectId) {
        ExamProject project = projectService.get(projectId);
        Paper paper = paperMapper.selectById(project.getPaperId());
        List<ExamCandidate> candidates = candidateMapper.selectList(new LambdaQueryWrapper<ExamCandidate>()
                .eq(ExamCandidate::getProjectId, projectId));
        List<ExamRecord> records = recordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getProjectId, projectId));
        gradingService.fillRecordNames(records);

        List<ExamRecord> attended = records.stream()
                .filter(r -> r.getAbsent() == null || r.getAbsent() == 0).collect(Collectors.toList());
        List<ExamRecord> graded = attended.stream()
                .filter(r -> Consts.REC_GRADED.equals(r.getStatus())).collect(Collectors.toList());
        long absent = candidates.size() - attended.size();
        long submitted = attended.stream().filter(r -> r.getSubmitTime() != null).count();
        long shortlisted = records.stream().filter(r -> r.getShortlisted() != null && r.getShortlisted() == 1).count();

        Map<String, Object> basic = new LinkedHashMap<>();
        basic.put("candidateCount", candidates.size());
        basic.put("attendedCount", attended.size());
        basic.put("absentCount", absent);
        basic.put("submittedCount", submitted);
        basic.put("gradedCount", graded.size());
        basic.put("shortlistedCount", shortlisted);
        basic.put("attendanceRate", rate(attended.size(), candidates.size()));
        basic.put("passScore", project.getPassScore());
        long passCount = graded.stream().filter(r -> r.getPassed() != null && r.getPassed() == 1).count();
        basic.put("passCount", passCount);
        basic.put("passRate", rate(passCount, graded.size()));

        List<BigDecimal> totals = graded.stream().map(ExamRecord::getTotalScore).collect(Collectors.toList());
        if (!totals.isEmpty()) {
            BigDecimal sum = totals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = sum.divide(new BigDecimal(totals.size()), 2, RoundingMode.HALF_UP);
            BigDecimal variance = totals.stream().map(t -> t.subtract(avg).pow(2))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(totals.size()), 4, RoundingMode.HALF_UP);
            basic.put("avgScore", avg);
            basic.put("maxScore", totals.stream().max(BigDecimal::compareTo).get());
            basic.put("minScore", totals.stream().min(BigDecimal::compareTo).get());
            basic.put("stdDev", BigDecimal.valueOf(Math.sqrt(variance.doubleValue())).setScale(2, RoundingMode.HALF_UP));
        }

        // 分数分布（按得分率分桶）
        String[] labels = {"<60%", "60-69%", "70-79%", "80-89%", "90-100%"};
        int[] buckets = new int[5];
        BigDecimal paperTotal = paper.getTotalScore() == null || paper.getTotalScore().signum() == 0
                ? new BigDecimal("100") : paper.getTotalScore();
        for (ExamRecord r : graded) {
            double pct = r.getTotalScore().doubleValue() / paperTotal.doubleValue() * 100;
            int idx = pct < 60 ? 0 : pct < 70 ? 1 : pct < 80 ? 2 : pct < 90 ? 3 : 4;
            buckets[idx]++;
        }
        List<Map<String, Object>> distribution = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("label", labels[i]);
            b.put("count", buckets[i]);
            distribution.add(b);
        }

        // 逐题难度(得分率)与区分度(高分组27%与低分组27%得分率差)
        List<Map<String, Object>> questionStats = new ArrayList<>();
        List<ExamRecord> ranked = new ArrayList<>(graded);
        ranked.sort((a, b) -> b.getTotalScore().compareTo(a.getTotalScore()));
        int groupSize = Math.max(1, (int) Math.ceil(ranked.size() * 0.27));
        List<ExamRecord> high = ranked.subList(0, Math.min(groupSize, ranked.size()));
        List<ExamRecord> low = ranked.subList(Math.max(0, ranked.size() - groupSize), ranked.size());
        Map<Long, List<AnswerRecord>> answersByRecord = new HashMap<>();
        if (!graded.isEmpty()) {
            List<Long> recordIds = graded.stream().map(ExamRecord::getId).collect(Collectors.toList());
            List<AnswerRecord> allAnswers = answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                    .in(AnswerRecord::getRecordId, recordIds));
            for (AnswerRecord ar : allAnswers) {
                answersByRecord.computeIfAbsent(ar.getRecordId(), k -> new ArrayList<>()).add(ar);
            }
        }
        List<AnswerRecord> sample = answersByRecord.values().stream().findFirst().orElse(Collections.emptyList());
        sample.sort(Comparator.comparing(AnswerRecord::getSortOrder));
        for (AnswerRecord template : sample) {
            Question q = questionMapper.selectById(template.getQuestionId());
            BigDecimal full = template.getFullScore();
            double avgAll = avgQuestionScore(graded, answersByRecord, template.getQuestionId(), full);
            double avgHigh = avgQuestionScore(high, answersByRecord, template.getQuestionId(), full);
            double avgLow = avgQuestionScore(low, answersByRecord, template.getQuestionId(), full);
            Map<String, Object> qs = new LinkedHashMap<>();
            qs.put("questionId", q.getId());
            qs.put("content", StrUtil.maxLength(q.getContent(), 60));
            qs.put("type", QuestionService.typeName(q.getType()));
            qs.put("fullScore", full);
            qs.put("difficulty", full.signum() == 0 ? null : BigDecimal.valueOf(avgAll).setScale(3, RoundingMode.HALF_UP));
            qs.put("discrimination", full.signum() == 0 || graded.size() < 2 ? null
                    : BigDecimal.valueOf(avgHigh - avgLow).setScale(3, RoundingMode.HALF_UP));
            questionStats.add(qs);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("project", project);
        res.put("paper", paper);
        res.put("basic", basic);
        res.put("distribution", distribution);
        res.put("questionStats", questionStats);
        return res;
    }

    private double avgQuestionScore(List<ExamRecord> records, Map<Long, List<AnswerRecord>> map,
                                    Long questionId, BigDecimal full) {
        if (records.isEmpty() || full.signum() == 0) return 0;
        double sum = 0;
        int n = 0;
        for (ExamRecord r : records) {
            List<AnswerRecord> ars = map.get(r.getId());
            if (ars == null) continue;
            for (AnswerRecord ar : ars) {
                if (ar.getQuestionId().equals(questionId)) {
                    sum += (ar.getScore() == null ? 0 : ar.getScore().doubleValue()) / full.doubleValue();
                    n++;
                }
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    private BigDecimal rate(long part, long total) {
        if (total == 0) return BigDecimal.ZERO;
        return new BigDecimal(part).multiply(new BigDecimal("100"))
                .divide(new BigDecimal(total), 1, RoundingMode.HALF_UP);
    }

    /** 个人成绩报告 */
    public Map<String, Object> report(Long recordId) {
        ExamRecord record = recordMapper.selectById(recordId);
        if (record == null) throw new BusinessException("考试记录不存在");
        ExamProject project = projectMapper.selectById(record.getProjectId());
        projectService.checkRead(project);
        gradingService.fillRecordNames(Collections.singletonList(record));
        Paper paper = paperMapper.selectById(record.getPaperId());
        List<AnswerRecord> answers = answerMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getRecordId, recordId).orderByAsc(AnswerRecord::getSortOrder));
        Map<Long, String> userNames = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName));
        for (AnswerRecord ar : answers) {
            ar.setQuestion(questionMapper.selectById(ar.getQuestionId()));
            ar.setReviewerName(userNames.get(ar.getReviewerId()));
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("record", record);
        res.put("project", project);
        res.put("paper", paper);
        res.put("answers", answers);
        return res;
    }

    public void exportScores(Long projectId, HttpServletResponse response) throws IOException {
        ExamProject project = projectService.get(projectId);
        List<ExamRecord> records = scores(projectId);
        List<List<String>> head = Arrays.asList(
                Collections.singletonList("排名"), Collections.singletonList("姓名"),
                Collections.singletonList("身份证号"), Collections.singletonList("供应商"),
                Collections.singletonList("客观题得分"), Collections.singletonList("主观题得分"),
                Collections.singletonList("总分"), Collections.singletonList("及格线"),
                Collections.singletonList("是否及格"), Collections.singletonList("是否入围"),
                Collections.singletonList("缺考"), Collections.singletonList("交卷方式"),
                Collections.singletonList("切屏次数"), Collections.singletonList("状态"));
        List<List<Object>> data = new ArrayList<>();
        for (ExamRecord r : records) {
            data.add(Arrays.asList(
                    r.getRankNo() == null ? "-" : r.getRankNo(),
                    r.getCandidateName(), r.getIdCard(), r.getVendor(),
                    r.getObjectiveScore(), r.getSubjectiveScore(), r.getTotalScore(),
                    project.getPassScore(),
                    r.getPassed() == null ? "-" : (r.getPassed() == 1 ? "是" : "否"),
                    r.getShortlisted() != null && r.getShortlisted() == 1 ? "是" : "否",
                    r.getAbsent() != null && r.getAbsent() == 1 ? "是" : "否",
                    "AUTO".equals(r.getSubmitType()) ? "自动交卷" : "主动交卷",
                    r.getSwitchCount(),
                    Consts.REC_GRADED.equals(r.getStatus()) ? "已评分" : "待复核"));
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment;filename="
                + URLEncoder.encode(project.getName() + "-成绩表", "UTF-8") + ".xlsx");
        EasyExcel.write(response.getOutputStream()).head(head).sheet("成绩").doWrite(data);
    }
}