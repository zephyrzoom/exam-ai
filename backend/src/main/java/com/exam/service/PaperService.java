package com.exam.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.BusinessException;
import com.exam.common.Consts;
import com.exam.config.LoginUser;
import com.exam.entity.*;
import com.exam.mapper.*;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PaperService {

    private final PaperMapper paperMapper;
    private final PaperQuestionMapper pqMapper;
    private final PaperLogMapper logMapper;
    private final QuestionMapper questionMapper;
    private final ExamProjectMapper projectMapper;
    private final DeptMapper deptMapper;
    private final SysUserMapper userMapper;

    public PaperService(PaperMapper paperMapper, PaperQuestionMapper pqMapper, PaperLogMapper logMapper,
                        QuestionMapper questionMapper, ExamProjectMapper projectMapper,
                        DeptMapper deptMapper, SysUserMapper userMapper) {
        this.paperMapper = paperMapper;
        this.pqMapper = pqMapper;
        this.logMapper = logMapper;
        this.questionMapper = questionMapper;
        this.projectMapper = projectMapper;
        this.deptMapper = deptMapper;
        this.userMapper = userMapper;
    }

    @Data
    public static class TypeRule {
        private String type;
        private Integer count;
        /** 难度1-5的比例(百分比)，如 {1:20,2:30,3:30,4:15,5:5} */
        private Map<Integer, Integer> difficultyRatio;
    }

    @Data
    public static class AiComposeRequest {
        private String name;
        private String description;
        private String jobRequirement;
        private String assessTarget;
        private String skill;
        private String knowledgePoint;
        private List<TypeRule> rules;
    }

    public Page<Paper> page(int pageNum, int pageSize, String name, String status, Long deptId,
                            String createdBegin, String createdEnd) {
        LoginUser u = LoginUser.get();
        LambdaQueryWrapper<Paper> w = new LambdaQueryWrapper<>();
        if (!u.isPm()) {
            w.eq(Paper::getDeptId, u.getDeptId());
        } else {
            w.eq(deptId != null, Paper::getDeptId, deptId);
        }
        w.like(StrUtil.isNotBlank(name), Paper::getName, name)
                .eq(StrUtil.isNotBlank(status), Paper::getStatus, status)
                .ge(StrUtil.isNotBlank(createdBegin), Paper::getCreatedAt,
                        StrUtil.isNotBlank(createdBegin) ? LocalDateTime.parse(createdBegin.replace(" ", "T")) : null)
                .le(StrUtil.isNotBlank(createdEnd), Paper::getCreatedAt,
                        StrUtil.isNotBlank(createdEnd) ? LocalDateTime.parse(createdEnd.replace(" ", "T")) : null)
                .orderByDesc(Paper::getId);
        Page<Paper> page = paperMapper.selectPage(new Page<>(pageNum, pageSize), w);
        fillNames(page.getRecords());
        return page;
    }

    private void fillNames(List<Paper> list) {
        if (list.isEmpty()) return;
        Map<Long, String> deptNames = deptMapper.selectList(null).stream()
                .collect(Collectors.toMap(Dept::getId, Dept::getName));
        Map<Long, String> userNames = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName));
        for (Paper p : list) {
            p.setDeptName(deptNames.get(p.getDeptId()));
            p.setCreatorName(userNames.get(p.getCreatorId()));
            p.setPledgeUserName(userNames.get(p.getPledgeUserId()));
        }
    }

    public Paper get(Long id) {
        Paper p = paperMapper.selectById(id);
        if (p == null) throw new BusinessException("试卷不存在");
        LoginUser u = LoginUser.get();
        if (!u.isPm() && !Objects.equals(p.getDeptId(), u.getDeptId())) {
            throw new BusinessException(403, "无权访问其他部门试卷");
        }
        fillNames(Collections.singletonList(p));
        return p;
    }

    private void checkWrite(Paper p) {
        LoginUser u = LoginUser.get();
        if (u.isPm()) throw new BusinessException(403, "试卷由各部门招聘负责人维护");
        if (!Objects.equals(p.getDeptId(), u.getDeptId())) {
            throw new BusinessException(403, "无权操作其他部门试卷");
        }
    }

    public Map<String, Object> detail(Long id) {
        Paper p = get(id);
        List<PaperQuestion> pqs = pqMapper.selectList(new LambdaQueryWrapper<PaperQuestion>()
                .eq(PaperQuestion::getPaperId, id).orderByAsc(PaperQuestion::getSortOrder));
        if (!pqs.isEmpty()) {
            List<Long> qids = pqs.stream().map(PaperQuestion::getQuestionId).collect(Collectors.toList());
            Map<Long, Question> qmap = questionMapper.selectBatchIds(qids).stream()
                    .collect(Collectors.toMap(Question::getId, q -> q));
            pqs.forEach(pq -> pq.setQuestion(qmap.get(pq.getQuestionId())));
        }
        Map<String, Object> res = new HashMap<>();
        res.put("paper", p);
        res.put("questions", pqs);
        return res;
    }

    @Transactional
    public Paper create(Paper paper) {
        LoginUser u = LoginUser.get();
        if (u.isPm()) throw new BusinessException(403, "试卷由各部门招聘负责人创建");
        if (StrUtil.isBlank(paper.getName())) throw new BusinessException("试卷名称不能为空");
        paper.setId(null);
        paper.setDeptId(u.getDeptId());
        paper.setStatus(Consts.PAPER_DRAFT);
        paper.setTotalScore(BigDecimal.ZERO);
        paper.setQuestionCount(0);
        paper.setCreatorId(u.getId());
        paper.setDeleted(0);
        paperMapper.insert(paper);
        log(paper.getId(), "CREATE", "创建试卷「" + paper.getName() + "」");
        return paper;
    }

    @Transactional
    public Paper aiCompose(AiComposeRequest req) {
        LoginUser u = LoginUser.get();
        if (u.isPm()) throw new BusinessException(403, "试卷由各部门招聘负责人创建");
        if (req.getRules() == null || req.getRules().isEmpty()) throw new BusinessException("请设置题型构成");
        Paper paper = new Paper();
        paper.setName(StrUtil.blankToDefault(req.getName(), "AI组卷-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd-HHmm"))));
        paper.setDescription(req.getDescription());
        paper.setJobRequirement(req.getJobRequirement());
        paper.setAssessTarget(req.getAssessTarget());
        create(paper);

        List<String> shortage = new ArrayList<>();
        int sort = 0;
        for (TypeRule rule : req.getRules()) {
            if (rule.getCount() == null || rule.getCount() <= 0) continue;
            List<Question> pool = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                    .eq(Question::getBankType, Consts.BANK_PUBLIC)
                    .eq(Question::getStatus, Consts.STATUS_NORMAL)
                    .eq(Question::getType, rule.getType())
                    .eq(StrUtil.isNotBlank(req.getSkill()), Question::getSkill, req.getSkill())
                    .like(StrUtil.isNotBlank(req.getKnowledgePoint()), Question::getKnowledgePoint, req.getKnowledgePoint()));
            if (pool.isEmpty()) {
                shortage.add(QuestionService.typeName(rule.getType()) + ": 公共题库无可用试题");
                continue;
            }
            Map<Integer, List<Question>> byDiff = pool.stream()
                    .collect(Collectors.groupingBy(Question::getDifficulty));
            byDiff.values().forEach(Collections::shuffle);

            Map<Integer, Integer> ratio = rule.getDifficultyRatio();
            List<Question> picked = new ArrayList<>();
            Set<Long> pickedIds = new HashSet<>();
            if (ratio != null && !ratio.isEmpty()) {
                int totalRatio = ratio.values().stream().mapToInt(Integer::intValue).sum();
                if (totalRatio <= 0) ratio = null;
                else {
                    int assigned = 0;
                    List<Integer> diffs = new ArrayList<>(ratio.keySet());
                    Collections.sort(diffs);
                    for (int i = 0; i < diffs.size(); i++) {
                        int d = diffs.get(i);
                        int want = i == diffs.size() - 1 ? rule.getCount() - assigned
                                : (int) Math.round(rule.getCount() * ratio.get(d) * 1.0 / totalRatio);
                        assigned += want;
                        take(byDiff.get(d), want, picked, pickedIds);
                    }
                }
            }
            if (ratio == null) {
                take(pool, rule.getCount(), picked, pickedIds);
            }
            // 数量不足：从同题型其他难度补齐
            if (picked.size() < rule.getCount()) {
                List<Question> rest = pool.stream()
                        .filter(q -> !pickedIds.contains(q.getId())).collect(Collectors.toList());
                Collections.shuffle(rest);
                take(rest, rule.getCount() - picked.size(), picked, pickedIds);
            }
            if (picked.size() < rule.getCount()) {
                shortage.add(QuestionService.typeName(rule.getType()) + ": 需要 " + rule.getCount()
                        + " 题，题库仅 " + picked.size() + " 题");
            }
            for (Question q : picked) {
                addQuestionInternal(paper.getId(), q.getId(), q.getDefaultScore(), ++sort);
            }
        }
        recalc(paper.getId());
        log(paper.getId(), "AI_COMPOSE", "AI组卷：技能=" + StrUtil.blankToDefault(req.getSkill(), "不限")
                + "，规则=" + cn.hutool.json.JSONUtil.toJsonStr(req.getRules())
                + (shortage.isEmpty() ? "" : "；缺口=" + String.join("，", shortage)));
        Paper result = paperMapper.selectById(paper.getId());
        if (!shortage.isEmpty()) {
            result.setDescription(StrUtil.nullToEmpty(result.getDescription()) + " [提示] " + String.join("；", shortage));
        }
        return result;
    }

    private void take(List<Question> pool, int want, List<Question> picked, Set<Long> pickedIds) {
        if (pool == null || want <= 0) return;
        int added = 0;
        for (Question q : pool) {
            if (added >= want) break;
            if (pickedIds.add(q.getId())) {
                picked.add(q);
                added++;
            }
        }
    }

    private void addQuestionInternal(Long paperId, Long questionId, BigDecimal score, int sort) {
        PaperQuestion pq = new PaperQuestion();
        pq.setPaperId(paperId);
        pq.setQuestionId(questionId);
        pq.setScore(score);
        pq.setSortOrder(sort);
        pqMapper.insert(pq);
    }

    @Transactional
    public void addQuestions(Long paperId, List<Map<String, Object>> items) {
        Paper p = get(paperId);
        checkWrite(p);
        List<PaperQuestion> exist = pqMapper.selectList(new LambdaQueryWrapper<PaperQuestion>()
                .eq(PaperQuestion::getPaperId, paperId));
        Set<Long> existIds = exist.stream().map(PaperQuestion::getQuestionId).collect(Collectors.toSet());
        int sort = exist.stream().mapToInt(PaperQuestion::getSortOrder).max().orElse(0);
        List<String> added = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Long qid = Long.valueOf(item.get("questionId").toString());
            if (existIds.contains(qid)) continue;
            Question q = questionMapper.selectById(qid);
            if (q == null) continue;
            LoginUser u = LoginUser.get();
            if (Consts.BANK_PRIVATE.equals(q.getBankType()) && !Objects.equals(q.getDeptId(), u.getDeptId())) {
                throw new BusinessException(403, "不能选择其他部门的私有试题");
            }
            BigDecimal score = item.get("score") != null ? new BigDecimal(item.get("score").toString()) : q.getDefaultScore();
            addQuestionInternal(paperId, qid, score, ++sort);
            existIds.add(qid);
            added.add("#" + qid);
        }
        recalc(paperId);
        log(paperId, "ADD", "补充试题: " + String.join(",", added));
    }

    @Transactional
    public void removeQuestion(Long paperId, Long questionId) {
        Paper p = get(paperId);
        checkWrite(p);
        pqMapper.delete(new LambdaQueryWrapper<PaperQuestion>()
                .eq(PaperQuestion::getPaperId, paperId).eq(PaperQuestion::getQuestionId, questionId));
        recalc(paperId);
        log(paperId, "REMOVE", "移除试题 #" + questionId);
    }

    @Transactional
    public void updateQuestionScore(Long paperId, Long questionId, BigDecimal score) {
        Paper p = get(paperId);
        checkWrite(p);
        PaperQuestion pq = pqMapper.selectOne(new LambdaQueryWrapper<PaperQuestion>()
                .eq(PaperQuestion::getPaperId, paperId).eq(PaperQuestion::getQuestionId, questionId));
        if (pq == null) throw new BusinessException("试卷中不存在该试题");
        BigDecimal old = pq.getScore();
        pq.setScore(score);
        pqMapper.updateById(pq);
        recalc(paperId);
        log(paperId, "UPDATE", "试题 #" + questionId + " 分值 " + old + " → " + score);
    }

    @Transactional
    public void reorder(Long paperId, List<Long> questionIds) {
        Paper p = get(paperId);
        checkWrite(p);
        int sort = 0;
        for (Long qid : questionIds) {
            PaperQuestion pq = pqMapper.selectOne(new LambdaQueryWrapper<PaperQuestion>()
                    .eq(PaperQuestion::getPaperId, paperId).eq(PaperQuestion::getQuestionId, qid));
            if (pq != null) {
                pq.setSortOrder(++sort);
                pqMapper.updateById(pq);
            }
        }
        log(paperId, "REORDER", "调整题目顺序");
    }

    @Transactional
    public Paper update(Long id, Paper paper) {
        Paper p = get(id);
        checkWrite(p);
        p.setName(paper.getName());
        p.setDescription(paper.getDescription());
        p.setJobRequirement(paper.getJobRequirement());
        p.setAssessTarget(paper.getAssessTarget());
        paperMapper.updateById(p);
        log(id, "EDIT", "编辑试卷基本信息");
        return p;
    }

    @Transactional
    public Paper copy(Long id) {
        Paper src = get(id);
        LoginUser u = LoginUser.get();
        if (u.isPm()) throw new BusinessException(403, "试卷由各部门招聘负责人维护");
        Paper copy = new Paper();
        copy.setName(src.getName() + "-副本");
        copy.setDescription(src.getDescription());
        copy.setJobRequirement(src.getJobRequirement());
        copy.setAssessTarget(src.getAssessTarget());
        create(copy);
        List<PaperQuestion> pqs = pqMapper.selectList(new LambdaQueryWrapper<PaperQuestion>()
                .eq(PaperQuestion::getPaperId, id).orderByAsc(PaperQuestion::getSortOrder));
        for (PaperQuestion pq : pqs) {
            addQuestionInternal(copy.getId(), pq.getQuestionId(), pq.getScore(), pq.getSortOrder());
        }
        recalc(copy.getId());
        log(copy.getId(), "COPY", "复制自试卷 #" + id);
        return paperMapper.selectById(copy.getId());
    }

    @Transactional
    public void delete(Long id) {
        Paper p = get(id);
        checkWrite(p);
        Long used = projectMapper.selectCount(new LambdaQueryWrapper<ExamProject>()
                .eq(ExamProject::getPaperId, id));
        if (used != null && used > 0) throw new BusinessException("试卷已被笔试项目引用，无法删除");
        paperMapper.deleteById(id);
    }

    public static final String PLEDGE_TEMPLATE =
            "本人作为本次笔试命题/组卷责任人，郑重承诺：\n" +
            "1. 严格遵守公司保密制度，对试卷内容、参考答案及评分标准承担保密责任；\n" +
            "2. 不以任何形式向考生或无关人员泄露试题内容；\n" +
            "3. 试卷仅用于本次招聘笔试，考试结束后按规定归档或销毁；\n" +
            "4. 如违反承诺，愿承担相应纪律与法律责任。";

    @Transactional
    public Paper pledge(Long id, String signerName) {
        Paper p = get(id);
        checkWrite(p);
        LoginUser u = LoginUser.get();
        if (StrUtil.isBlank(signerName) || !signerName.trim().equals(u.getName())) {
            throw new BusinessException("请输入本人姓名「" + u.getName() + "」完成签署");
        }
        p.setPledgeUserId(u.getId());
        p.setPledgeTime(LocalDateTime.now());
        p.setPledgeText(PLEDGE_TEMPLATE + "\n承诺人：" + signerName.trim()
                + "\n签署时间：" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        paperMapper.updateById(p);
        log(id, "PLEDGE", u.getName() + " 签署保密承诺书");
        return p;
    }

    @Transactional
    public Paper publish(Long id) {
        Paper p = get(id);
        checkWrite(p);
        if (p.getQuestionCount() == null || p.getQuestionCount() == 0) {
            throw new BusinessException("试卷无试题，无法发布");
        }
        if (p.getPledgeUserId() == null) {
            throw new BusinessException("发布前须签署保密承诺书");
        }
        p.setStatus(Consts.PAPER_PUBLISHED);
        p.setPublishedAt(LocalDateTime.now());
        paperMapper.updateById(p);
        log(id, "PUBLISH", "发布试卷，共 " + p.getQuestionCount() + " 题，总分 " + p.getTotalScore());
        return p;
    }

    public List<PaperLog> logs(Long paperId) {
        get(paperId);
        List<PaperLog> logs = logMapper.selectList(new LambdaQueryWrapper<PaperLog>()
                .eq(PaperLog::getPaperId, paperId).orderByDesc(PaperLog::getId));
        Map<Long, String> userNames = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName));
        logs.forEach(l -> l.setOperatorName(userNames.get(l.getOperatorId())));
        return logs;
    }

    private void recalc(Long paperId) {
        List<PaperQuestion> pqs = pqMapper.selectList(new LambdaQueryWrapper<PaperQuestion>()
                .eq(PaperQuestion::getPaperId, paperId));
        BigDecimal total = pqs.stream().map(PaperQuestion::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Paper p = paperMapper.selectById(paperId);
        p.setTotalScore(total);
        p.setQuestionCount(pqs.size());
        paperMapper.updateById(p);
    }

    private void log(Long paperId, String action, String detail) {
        PaperLog l = new PaperLog();
        l.setPaperId(paperId);
        l.setAction(action);
        l.setDetail(detail);
        l.setOperatorId(LoginUser.get().getId());
        logMapper.insert(l);
    }
}