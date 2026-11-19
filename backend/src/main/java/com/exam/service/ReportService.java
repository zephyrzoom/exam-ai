package com.exam.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
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

    /** 编组模式：组平均成绩。平均分为组内已评分考生总分的平均值，缺考不计入；未分组考生归入「未分组」 */
    public List<Map<String, Object>> groupScores(Long projectId) {
        ExamProject project = projectService.get(projectId);
        if (!Consts.MODE_GROUP.equals(project.getMode())) return Collections.emptyList();
        List<ExamCandidate> candidates = candidateMapper.selectList(new LambdaQueryWrapper<ExamCandidate>()
                .eq(ExamCandidate::getProjectId, projectId));
        List<ExamRecord> records = recordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getProjectId, projectId));
        Map<Long, ExamRecord> byCandidate = records.stream()
                .collect(Collectors.toMap(ExamRecord::getCandidateId, r -> r, (a, b) -> a));
        Map<String, List<ExamCandidate>> groups = new LinkedHashMap<>();
        for (ExamCandidate c : candidates) {
            String g = StrUtil.isBlank(c.getGroupName()) ? "未分组" : c.getGroupName();
            groups.computeIfAbsent(g, k -> new ArrayList<>()).add(c);
        }
        List<Map<String, Object>> res = new ArrayList<>();
        for (Map.Entry<String, List<ExamCandidate>> e : groups.entrySet()) {
            List<BigDecimal> totals = new ArrayList<>();
            int absent = 0;
            for (ExamCandidate c : e.getValue()) {
                ExamRecord r = byCandidate.get(c.getId());
                if (r == null) continue;
                if (r.getAbsent() != null && r.getAbsent() == 1) { absent++; continue; }
                if (Consts.REC_GRADED.equals(r.getStatus())) totals.add(r.getTotalScore());
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("groupName", e.getKey());
            row.put("memberCount", e.getValue().size());
            row.put("gradedCount", totals.size());
            row.put("absentCount", absent);
            if (totals.isEmpty()) {
                row.put("avgScore", null);
                row.put("maxScore", null);
                row.put("minScore", null);
            } else {
                BigDecimal sum = totals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                row.put("avgScore", sum.divide(new BigDecimal(totals.size()), 2, RoundingMode.HALF_UP));
                row.put("maxScore", totals.stream().max(BigDecimal::compareTo).get());
                row.put("minScore", totals.stream().min(BigDecimal::compareTo).get());
            }
            res.add(row);
        }
        res.sort((a, b) -> {
            BigDecimal av = (BigDecimal) a.get("avgScore"), bv = (BigDecimal) b.get("avgScore");
            if (av == null) return bv == null ? 0 : 1;
            if (bv == null) return -1;
            return bv.compareTo(av);
        });
        return res;
    }

    public void exportScores(Long projectId, HttpServletResponse response) throws IOException {
        ExamProject project = projectService.get(projectId);
        boolean groupMode = Consts.MODE_GROUP.equals(project.getMode());
        List<ExamRecord> records = scores(projectId);
        List<List<String>> head = new ArrayList<>();
        head.add(Collections.singletonList("排名"));
        head.add(Collections.singletonList("姓名"));
        if (groupMode) head.add(Collections.singletonList("组名"));
        head.add(Collections.singletonList("身份证号"));
        head.add(Collections.singletonList("供应商"));
        head.add(Collections.singletonList("客观题得分"));
        head.add(Collections.singletonList("主观题得分"));
        head.add(Collections.singletonList("总分"));
        head.add(Collections.singletonList("及格线"));
        head.add(Collections.singletonList("是否及格"));
        head.add(Collections.singletonList("是否入围"));
        head.add(Collections.singletonList("缺考"));
        head.add(Collections.singletonList("交卷方式"));
        head.add(Collections.singletonList("切屏次数"));
        head.add(Collections.singletonList("状态"));
        List<List<Object>> data = new ArrayList<>();
        for (ExamRecord r : records) {
            List<Object> row = new ArrayList<>();
            row.add(r.getRankNo() == null ? "-" : r.getRankNo());
            row.add(r.getCandidateName());
            if (groupMode) row.add(StrUtil.nullToEmpty(r.getGroupName()));
            row.add(r.getIdCard());
            row.add(r.getVendor());
            row.add(r.getObjectiveScore());
            row.add(r.getSubjectiveScore());
            row.add(r.getTotalScore());
            row.add(project.getPassScore());
            row.add(r.getPassed() == null ? "-" : (r.getPassed() == 1 ? "是" : "否"));
            row.add(r.getShortlisted() != null && r.getShortlisted() == 1 ? "是" : "否");
            row.add(r.getAbsent() != null && r.getAbsent() == 1 ? "是" : "否");
            row.add("AUTO".equals(r.getSubmitType()) ? "自动交卷" : "主动交卷");
            row.add(r.getSwitchCount());
            row.add(Consts.REC_GRADED.equals(r.getStatus()) ? "已评分" : "待复核");
            data.add(row);
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment;filename="
                + URLEncoder.encode(project.getName() + "-成绩表", "UTF-8") + ".xlsx");
        if (!groupMode) {
            EasyExcel.write(response.getOutputStream()).head(head).sheet("成绩").doWrite(data);
            return;
        }
        List<List<String>> groupHead = Arrays.asList(
                Collections.singletonList("组名"), Collections.singletonList("考生数"),
                Collections.singletonList("已评分人数"), Collections.singletonList("缺考人数"),
                Collections.singletonList("平均分"), Collections.singletonList("最高分"),
                Collections.singletonList("最低分"));
        List<List<Object>> groupData = new ArrayList<>();
        for (Map<String, Object> g : groupScores(projectId)) {
            groupData.add(Arrays.asList(g.get("groupName"), g.get("memberCount"), g.get("gradedCount"),
                    g.get("absentCount"),
                    g.get("avgScore") == null ? "-" : g.get("avgScore"),
                    g.get("maxScore") == null ? "-" : g.get("maxScore"),
                    g.get("minScore") == null ? "-" : g.get("minScore")));
        }
        ExcelWriter writer = EasyExcel.write(response.getOutputStream()).build();
        writer.write(data, EasyExcel.writerSheet(0, "成绩").head(head).build());
        writer.write(groupData, EasyExcel.writerSheet(1, "组平均成绩").head(groupHead).build());
        writer.finish();
    }
}