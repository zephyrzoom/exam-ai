package com.exam.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.BusinessException;
import com.exam.common.Consts;
import com.exam.config.LoginUser;
import com.exam.entity.Dept;
import com.exam.entity.Question;
import com.exam.entity.SysUser;
import com.exam.mapper.DeptMapper;
import com.exam.mapper.QuestionMapper;
import com.exam.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuestionService {

    private final QuestionMapper questionMapper;
    private final DeptMapper deptMapper;
    private final SysUserMapper userMapper;

    public QuestionService(QuestionMapper questionMapper, DeptMapper deptMapper, SysUserMapper userMapper) {
        this.questionMapper = questionMapper;
        this.deptMapper = deptMapper;
        this.userMapper = userMapper;
    }

    public Page<Question> page(int pageNum, int pageSize, String bankType, Long deptId, String type,
                               String skill, Integer difficulty, String knowledgePoint, String keyword, String status) {
        LoginUser u = LoginUser.get();
        LambdaQueryWrapper<Question> w = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(bankType)) {
            w.eq(Question::getBankType, bankType);
        }
        if (Consts.BANK_PRIVATE.equals(bankType)) {
            if (u.isPm()) {
                w.eq(deptId != null, Question::getDeptId, deptId);
            } else {
                w.eq(Question::getDeptId, u.getDeptId());
            }
        }
        w.eq(StrUtil.isNotBlank(type), Question::getType, type)
                .eq(StrUtil.isNotBlank(skill), Question::getSkill, skill)
                .eq(difficulty != null, Question::getDifficulty, difficulty)
                .like(StrUtil.isNotBlank(knowledgePoint), Question::getKnowledgePoint, knowledgePoint)
                .eq(StrUtil.isNotBlank(status), Question::getStatus, status)
                .and(StrUtil.isNotBlank(keyword), x -> x.like(Question::getContent, keyword)
                        .or().like(Question::getKnowledgePoint, keyword))
                .orderByDesc(Question::getId);
        Page<Question> page = questionMapper.selectPage(new Page<>(pageNum, pageSize), w);
        fillNames(page.getRecords());
        return page;
    }

    private void fillNames(List<Question> list) {
        if (list.isEmpty()) return;
        Map<Long, String> deptNames = deptMapper.selectList(null).stream()
                .collect(Collectors.toMap(Dept::getId, Dept::getName));
        Map<Long, String> userNames = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName));
        for (Question q : list) {
            q.setDeptName(deptNames.get(q.getDeptId()));
            q.setCreatorName(userNames.get(q.getCreatorId()));
        }
    }

    public Question get(Long id) {
        Question q = questionMapper.selectById(id);
        if (q == null) throw new BusinessException("试题不存在");
        checkRead(q);
        return q;
    }

    private void checkRead(Question q) {
        LoginUser u = LoginUser.get();
        if (Consts.BANK_PRIVATE.equals(q.getBankType()) && !u.isPm()
                && !Objects.equals(q.getDeptId(), u.getDeptId())) {
            throw new BusinessException(403, "无权访问其他部门私有题库");
        }
    }

    private void checkWrite(Question q) {
        LoginUser u = LoginUser.get();
        if (Consts.BANK_PUBLIC.equals(q.getBankType())) {
            if (!u.isPm()) throw new BusinessException(403, "公共题库由项目管理部维护");
        } else if (!Objects.equals(q.getDeptId(), u.getDeptId())) {
            throw new BusinessException(403, "只能维护本部门私有题库");
        }
    }

    public Question create(Question q) {
        LoginUser u = LoginUser.get();
        validate(q);
        if (Consts.BANK_PUBLIC.equals(q.getBankType())) {
            if (!u.isPm()) throw new BusinessException(403, "公共题库由项目管理部维护");
            q.setDeptId(null);
        } else {
            q.setBankType(Consts.BANK_PRIVATE);
            q.setDeptId(u.getDeptId());
        }
        q.setId(null);
        q.setStatus(Consts.STATUS_NORMAL);
        q.setSourceQuestionId(null);
        q.setCreatorId(u.getId());
        if (q.getDefaultScore() == null) q.setDefaultScore(defaultScore(q.getType()));
        questionMapper.insert(q);
        return q;
    }

    private BigDecimal defaultScore(String type) {
        switch (type) {
            case Consts.Q_SINGLE: return new BigDecimal("3");
            case Consts.Q_MULTI: return new BigDecimal("4");
            case Consts.Q_JUDGE: return new BigDecimal("2");
            default: return new BigDecimal("10");
        }
    }

    private void validate(Question q) {
        if (StrUtil.isBlank(q.getContent())) throw new BusinessException("题干不能为空");
        if (StrUtil.isBlank(q.getType())) throw new BusinessException("题型不能为空");
        if (q.getDifficulty() == null || q.getDifficulty() < 1 || q.getDifficulty() > 5)
            throw new BusinessException("难度须为1-5");
        if (!Consts.Q_ESSAY.equals(q.getType()) && StrUtil.isBlank(q.getAnswer()))
            throw new BusinessException("客观题必须设置答案");
    }

    public Question update(Long id, Question q) {
        Question old = get(id);
        checkWrite(old);
        validate(q);
        q.setId(id);
        q.setBankType(old.getBankType());
        q.setDeptId(old.getDeptId());
        q.setCreatorId(old.getCreatorId());
        q.setSourceQuestionId(old.getSourceQuestionId());
        if (q.getStatus() == null) q.setStatus(old.getStatus());
        questionMapper.updateById(q);
        return questionMapper.selectById(id);
    }

    public void delete(Long id) {
        Question q = get(id);
        checkWrite(q);
        questionMapper.deleteById(id);
    }

    /** 私有题贡献至公共题库：生成待审核副本，由项目管理部审核 */
    public Question contribute(Long id) {
        Question src = get(id);
        if (!Consts.BANK_PRIVATE.equals(src.getBankType())) {
            throw new BusinessException("仅私有题库试题可贡献至公共题库");
        }
        LoginUser u = LoginUser.get();
        if (!Objects.equals(src.getDeptId(), u.getDeptId())) {
            throw new BusinessException(403, "只能贡献本部门试题");
        }
        Long exist = questionMapper.selectCount(new LambdaQueryWrapper<Question>()
                .eq(Question::getSourceQuestionId, id)
                .eq(Question::getStatus, Consts.STATUS_PENDING));
        if (exist != null && exist > 0) throw new BusinessException("该试题已有待审核的贡献记录");
        Question copy = new Question();
        copy.setBankType(Consts.BANK_PUBLIC);
        copy.setType(src.getType());
        copy.setContent(src.getContent());
        copy.setOptionsJson(src.getOptionsJson());
        copy.setAnswer(src.getAnswer());
        copy.setAnalysis(src.getAnalysis());
        copy.setScorePoints(src.getScorePoints());
        copy.setSkill(src.getSkill());
        copy.setDifficulty(src.getDifficulty());
        copy.setKnowledgePoint(src.getKnowledgePoint());
        copy.setDefaultScore(src.getDefaultScore());
        copy.setStatus(Consts.STATUS_PENDING);
        copy.setSourceQuestionId(src.getId());
        copy.setCreatorId(u.getId());
        questionMapper.insert(copy);
        return copy;
    }

    public Page<Question> contributions(int pageNum, int pageSize) {
        if (!LoginUser.get().isPm()) throw new BusinessException(403, "仅项目管理部可审核贡献试题");
        Page<Question> page = questionMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getStatus, Consts.STATUS_PENDING)
                        .orderByDesc(Question::getId));
        fillNames(page.getRecords());
        return page;
    }

    public void approve(Long id, boolean pass) {
        if (!LoginUser.get().isPm()) throw new BusinessException(403, "仅项目管理部可审核贡献试题");
        Question q = questionMapper.selectById(id);
        if (q == null || !Consts.STATUS_PENDING.equals(q.getStatus())) {
            throw new BusinessException("试题不存在或不在待审核状态");
        }
        if (pass) {
            q.setStatus(Consts.STATUS_NORMAL);
            questionMapper.updateById(q);
        } else {
            questionMapper.deleteById(id);
        }
    }

    /** Excel 导入。列: 题型 题干 选项(用|分隔) 答案 解析 评分要点 技能 难度 知识点 分值 */
    public Map<String, Object> importExcel(MultipartFile file, String bankType) throws IOException {
        LoginUser u = LoginUser.get();
        if (Consts.BANK_PUBLIC.equals(bankType) && !u.isPm()) {
            throw new BusinessException(403, "公共题库由项目管理部维护");
        }
        List<Map<Integer, String>> rows = EasyExcel.read(file.getInputStream()).sheet().headRowNumber(1).doReadSync();
        int success = 0;
        List<String> errors = new ArrayList<>();
        int rowNo = 1;
        for (Map<Integer, String> row : rows) {
            rowNo++;
            try {
                Question q = new Question();
                q.setType(parseType(row.get(0)));
                q.setContent(row.get(1));
                String opts = row.get(2);
                if (StrUtil.isNotBlank(opts)) {
                    List<String> optList = Arrays.stream(opts.split("\\|"))
                            .map(String::trim).filter(StrUtil::isNotBlank)
                            .map(s -> s.replaceFirst("^[A-Za-z][\\.、．:：]\\s*", ""))
                            .collect(Collectors.toList());
                    q.setOptionsJson(cn.hutool.json.JSONUtil.toJsonStr(optList));
                }
                q.setAnswer(row.get(3));
                q.setAnalysis(row.get(4));
                q.setScorePoints(row.get(5));
                q.setSkill(row.get(6));
                q.setDifficulty(StrUtil.isBlank(row.get(7)) ? 3 : Integer.parseInt(row.get(7).trim()));
                q.setKnowledgePoint(row.get(8));
                q.setDefaultScore(StrUtil.isBlank(row.get(9)) ? null : new BigDecimal(row.get(9).trim()));
                q.setBankType(Consts.BANK_PUBLIC.equals(bankType) ? Consts.BANK_PUBLIC : Consts.BANK_PRIVATE);
                create(q);
                success++;
            } catch (Exception e) {
                errors.add("第" + rowNo + "行: " + e.getMessage());
            }
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", success);
        res.put("fail", errors.size());
        res.put("errors", errors);
        return res;
    }

    private String parseType(String text) {
        if (text == null) throw new BusinessException("题型为空");
        String t = text.trim();
        if (t.contains("单选") || t.equalsIgnoreCase("SINGLE")) return Consts.Q_SINGLE;
        if (t.contains("多选") || t.equalsIgnoreCase("MULTI")) return Consts.Q_MULTI;
        if (t.contains("判断") || t.equalsIgnoreCase("JUDGE")) return Consts.Q_JUDGE;
        if (t.contains("主观") || t.contains("简答") || t.equalsIgnoreCase("ESSAY")) return Consts.Q_ESSAY;
        throw new BusinessException("无法识别题型: " + t);
    }

    public void exportExcel(HttpServletResponse response, String bankType, Long deptId, String type,
                            String skill, Integer difficulty, String keyword) throws IOException {
        Page<Question> page = page(1, 10000, bankType, deptId, type, skill, difficulty, null, keyword, Consts.STATUS_NORMAL);
        List<List<String>> head = Arrays.asList(
                Collections.singletonList("题型"), Collections.singletonList("题干"),
                Collections.singletonList("选项(|分隔)"), Collections.singletonList("答案"),
                Collections.singletonList("解析"), Collections.singletonList("评分要点"),
                Collections.singletonList("技能"), Collections.singletonList("难度"),
                Collections.singletonList("知识点"), Collections.singletonList("分值"));
        List<List<Object>> data = new ArrayList<>();
        for (Question q : page.getRecords()) {
            String opts = "";
            if (StrUtil.isNotBlank(q.getOptionsJson())) {
                opts = String.join("|", cn.hutool.json.JSONUtil.toList(q.getOptionsJson(), String.class));
            }
            data.add(Arrays.asList(typeName(q.getType()), q.getContent(), opts, q.getAnswer(),
                    q.getAnalysis(), q.getScorePoints(), q.getSkill(), q.getDifficulty(),
                    q.getKnowledgePoint(), q.getDefaultScore()));
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String name = URLEncoder.encode("题库导出", "UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + name + ".xlsx");
        EasyExcel.write(response.getOutputStream()).head(head).sheet("题库").doWrite(data);
    }

    public static String typeName(String type) {
        switch (type) {
            case Consts.Q_SINGLE: return "单选";
            case Consts.Q_MULTI: return "多选";
            case Consts.Q_JUDGE: return "判断";
            case Consts.Q_ESSAY: return "主观简答";
            default: return type;
        }
    }

    public void downloadTemplate(HttpServletResponse response) throws IOException {
        List<List<String>> head = Arrays.asList(
                Collections.singletonList("题型(单选/多选/判断/主观)"), Collections.singletonList("题干"),
                Collections.singletonList("选项(|分隔)"), Collections.singletonList("答案"),
                Collections.singletonList("解析"), Collections.singletonList("评分要点"),
                Collections.singletonList("技能"), Collections.singletonList("难度1-5"),
                Collections.singletonList("知识点"), Collections.singletonList("分值"));
        List<List<Object>> demo = new ArrayList<>();
        demo.add(Arrays.asList("单选", "Java 中用于定义常量的关键字是？", "A. static|B. final|C. const|D. define",
                "B", "final 修饰的变量为常量", "", "Java", "1", "基础语法", "3"));
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment;filename=question_template.xlsx");
        EasyExcel.write(response.getOutputStream()).head(head).sheet("模板").doWrite(demo);
    }
}