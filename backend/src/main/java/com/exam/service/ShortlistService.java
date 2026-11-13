package com.exam.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.BusinessException;
import com.exam.common.Consts;
import com.exam.config.LoginUser;
import com.exam.entity.*;
import com.exam.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ShortlistService {

    private final ShortlistPlanMapper planMapper;
    private final ExamRecordMapper recordMapper;
    private final ExamProjectMapper projectMapper;
    private final ExamCandidateMapper candidateMapper;
    private final SysUserMapper userMapper;
    private final HrClient hrClient;
    private final HrSyncLogMapper syncLogMapper;
    private final ProjectService projectService;
    private final GradingService gradingService;

    public ShortlistService(ShortlistPlanMapper planMapper, ExamRecordMapper recordMapper,
                            ExamProjectMapper projectMapper, ExamCandidateMapper candidateMapper,
                            SysUserMapper userMapper, HrClient hrClient, HrSyncLogMapper syncLogMapper,
                            ProjectService projectService, GradingService gradingService) {
        this.planMapper = planMapper;
        this.recordMapper = recordMapper;
        this.projectMapper = projectMapper;
        this.candidateMapper = candidateMapper;
        this.userMapper = userMapper;
        this.hrClient = hrClient;
        this.syncLogMapper = syncLogMapper;
        this.projectService = projectService;
        this.gradingService = gradingService;
    }

    public ShortlistPlan getPlan(Long projectId) {
        projectService.get(projectId);
        ShortlistPlan plan = planMapper.selectOne(new LambdaQueryWrapper<ShortlistPlan>()
                .eq(ShortlistPlan::getProjectId, projectId).orderByDesc(ShortlistPlan::getId).last("LIMIT 1"));
        if (plan != null && plan.getConfirmedBy() != null) {
            SysUser u = userMapper.selectById(plan.getConfirmedBy());
            plan.setConfirmedByName(u == null ? null : u.getRealName());
        }
        return plan;
    }

    @Transactional
    public ShortlistPlan savePlan(Long projectId, ShortlistPlan plan) {
        ExamProject project = projectService.get(projectId);
        projectService.checkWrite(project);
        ShortlistPlan exist = getPlan(projectId);
        if (exist != null && "CONFIRMED".equals(exist.getStatus())) {
            throw new BusinessException("入围方案已确认，如需调整请先撤销确认");
        }
        if (StrUtil.isBlank(plan.getRuleType())) throw new BusinessException("请选择入围规则");
        switch (plan.getRuleType()) {
            case "TOP_N":
                if (plan.getTopN() == null || plan.getTopN() <= 0) throw new BusinessException("请填写入围名额");
                break;
            case "RATIO":
                if (plan.getRatio() == null || plan.getRatio().signum() <= 0 || plan.getRatio().compareTo(new BigDecimal("100")) > 0)
                    throw new BusinessException("入围比例须在 0~100 之间");
                break;
            case "SCORE_LINE":
                if (plan.getMinScore() == null) throw new BusinessException("请填写入围分数线");
                break;
            default:
                throw new BusinessException("未知规则类型");
        }
        if (exist != null) {
            exist.setName(plan.getName());
            exist.setRuleType(plan.getRuleType());
            exist.setTopN(plan.getTopN());
            exist.setRatio(plan.getRatio());
            exist.setMinScore(plan.getMinScore());
            planMapper.updateById(exist);
            return exist;
        }
        plan.setId(null);
        plan.setProjectId(projectId);
        plan.setStatus("DRAFT");
        planMapper.insert(plan);
        return plan;
    }

    /** 预览入围名单：按总分排名，须达到项目及格线；SCORE_LINE 规则使用方案分数线 */
    public Map<String, Object> preview(Long projectId) {
        ExamProject project = projectService.get(projectId);
        ShortlistPlan plan = getPlan(projectId);
        if (plan == null) throw new BusinessException("请先配置入围方案");
        List<ExamRecord> graded = recordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getProjectId, projectId)
                .eq(ExamRecord::getStatus, Consts.REC_GRADED)
                .eq(ExamRecord::getAbsent, 0));
        graded.sort((a, b) -> b.getTotalScore().compareTo(a.getTotalScore()));

        BigDecimal line = "SCORE_LINE".equals(plan.getRuleType()) && plan.getMinScore() != null
                ? plan.getMinScore() : project.getPassScore();
        List<ExamRecord> eligible = graded.stream()
                .filter(r -> line == null || r.getTotalScore().compareTo(line) >= 0)
                .collect(Collectors.toList());
        int limit;
        switch (plan.getRuleType()) {
            case "TOP_N":
                limit = Math.min(plan.getTopN(), eligible.size());
                break;
            case "RATIO":
                limit = Math.min((int) Math.ceil(graded.size() * plan.getRatio().doubleValue() / 100), eligible.size());
                break;
            default:
                limit = eligible.size();
        }
        List<ExamRecord> selected = eligible.subList(0, limit);
        Set<Long> selectedIds = selected.stream().map(ExamRecord::getId).collect(Collectors.toSet());
        gradingService.fillRecordNames(graded);
        List<Map<String, Object>> rows = new ArrayList<>();
        int rank = 0;
        for (ExamRecord r : graded) {
            rank++;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recordId", r.getId());
            row.put("rank", rank);
            row.put("name", r.getCandidateName());
            row.put("idCard", r.getIdCard());
            row.put("vendor", r.getVendor());
            row.put("totalScore", r.getTotalScore());
            row.put("passed", r.getPassed());
            row.put("shortlisted", selectedIds.contains(r.getId()));
            rows.add(row);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("plan", plan);
        res.put("line", line);
        res.put("gradedCount", graded.size());
        res.put("shortlistCount", selected.size());
        res.put("rows", rows);
        return res;
    }

    @Transactional
    public Map<String, Object> confirm(Long projectId) {
        ExamProject project = projectService.get(projectId);
        projectService.checkWrite(project);
        ShortlistPlan plan = getPlan(projectId);
        if (plan == null) throw new BusinessException("请先配置入围方案");
        Map<String, Object> preview = preview(projectId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) preview.get("rows");
        Set<Long> selectedIds = rows.stream().filter(r -> Boolean.TRUE.equals(r.get("shortlisted")))
                .map(r -> Long.valueOf(r.get("recordId").toString())).collect(Collectors.toSet());
        List<ExamRecord> all = recordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getProjectId, projectId));
        for (ExamRecord r : all) {
            int flag = selectedIds.contains(r.getId()) ? 1 : 0;
            if (r.getShortlisted() == null || r.getShortlisted() != flag) {
                r.setShortlisted(flag);
                recordMapper.updateById(r);
            }
        }
        plan.setStatus("CONFIRMED");
        plan.setConfirmedBy(LoginUser.get().getId());
        plan.setConfirmedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        Map<String, Object> res = new HashMap<>();
        res.put("shortlistCount", selectedIds.size());
        res.put("planId", plan.getId());
        return res;
    }

    @Transactional
    public void revoke(Long projectId) {
        projectService.checkWrite(projectService.get(projectId));
        ShortlistPlan plan = getPlan(projectId);
        if (plan == null) throw new BusinessException("方案不存在");
        plan.setStatus("DRAFT");
        plan.setConfirmedBy(null);
        plan.setConfirmedAt(null);
        planMapper.updateById(plan);
        List<ExamRecord> all = recordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getProjectId, projectId));
        for (ExamRecord r : all) {
            r.setShortlisted(0);
            recordMapper.updateById(r);
        }
    }

    /** 回传人事系统：成绩、及格线、是否及格、排名、入围结果、方案标识、缺考标识 */
    @Transactional
    public Map<String, Object> pushHr(Long projectId) {
        ExamProject project = projectService.get(projectId);
        projectService.checkWrite(project);
        ShortlistPlan plan = getPlan(projectId);
        if (plan == null || !"CONFIRMED".equals(plan.getStatus())) {
            throw new BusinessException("请先确认入围名单后再回传");
        }
        List<ExamRecord> records = recordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getProjectId, projectId));
        gradingService.fillRecordNames(records);
        Map<Long, ExamCandidate> candidates = candidateMapper.selectList(new LambdaQueryWrapper<ExamCandidate>()
                .eq(ExamCandidate::getProjectId, projectId)).stream()
                .collect(Collectors.toMap(ExamCandidate::getId, c -> c));

        List<Map<String, Object>> results = new ArrayList<>();
        for (ExamRecord r : records) {
            ExamCandidate c = candidates.get(r.getCandidateId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("hrId", c == null ? null : c.getHrId());
            item.put("name", r.getCandidateName());
            item.put("idCard", r.getIdCard());
            item.put("vendor", r.getVendor());
            item.put("totalScore", r.getTotalScore());
            item.put("objectiveScore", r.getObjectiveScore());
            item.put("subjectiveScore", r.getSubjectiveScore());
            item.put("passScore", project.getPassScore());
            item.put("passed", r.getPassed() != null && r.getPassed() == 1);
            item.put("rank", r.getRankNo());
            item.put("shortlisted", r.getShortlisted() != null && r.getShortlisted() == 1);
            item.put("absent", r.getAbsent() != null && r.getAbsent() == 1);
            item.put("switchCount", r.getSwitchCount());
            results.add(item);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", project.getId());
        payload.put("projectName", project.getName());
        payload.put("planId", plan.getId());
        payload.put("planName", plan.getName());
        payload.put("ruleType", plan.getRuleType());
        payload.put("passScore", project.getPassScore());
        payload.put("pushTime", LocalDateTime.now().toString());
        payload.put("results", results);
        String json = JSONUtil.toJsonStr(payload);
        hrClient.pushResults(project, json);

        Map<String, Object> res = new HashMap<>();
        res.put("count", results.size());
        res.put("mock", !hrClient.realEnabled());
        res.put("payload", payload);
        return res;
    }

    public List<HrSyncLog> syncLogs(Long projectId) {
        projectService.get(projectId);
        return syncLogMapper.selectList(new LambdaQueryWrapper<HrSyncLog>()
                .eq(HrSyncLog::getProjectId, projectId).orderByDesc(HrSyncLog::getId));
    }
}