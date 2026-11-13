package com.exam.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.BusinessException;
import com.exam.common.Consts;
import com.exam.config.LoginUser;
import com.exam.entity.*;
import com.exam.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final ExamProjectMapper projectMapper;
    private final ExamCandidateMapper candidateMapper;
    private final ExamRecordMapper recordMapper;
    private final PaperMapper paperMapper;
    private final DeptMapper deptMapper;
    private final SysUserMapper userMapper;

    public ProjectService(ExamProjectMapper projectMapper, ExamCandidateMapper candidateMapper,
                          ExamRecordMapper recordMapper, PaperMapper paperMapper,
                          DeptMapper deptMapper, SysUserMapper userMapper) {
        this.projectMapper = projectMapper;
        this.candidateMapper = candidateMapper;
        this.recordMapper = recordMapper;
        this.paperMapper = paperMapper;
        this.deptMapper = deptMapper;
        this.userMapper = userMapper;
    }

    public Page<ExamProject> page(int pageNum, int pageSize, String name, String status) {
        LoginUser u = LoginUser.get();
        LambdaQueryWrapper<ExamProject> w = new LambdaQueryWrapper<>();
        if (!u.isPm()) w.eq(ExamProject::getDeptId, u.getDeptId());
        w.like(StrUtil.isNotBlank(name), ExamProject::getName, name)
                .eq(StrUtil.isNotBlank(status), ExamProject::getStatus, status)
                .orderByDesc(ExamProject::getId);
        Page<ExamProject> page = projectMapper.selectPage(new Page<>(pageNum, pageSize), w);
        fill(page.getRecords());
        return page;
    }

    private void fill(List<ExamProject> list) {
        if (list.isEmpty()) return;
        Map<Long, String> papers = paperMapper.selectList(null).stream()
                .collect(Collectors.toMap(Paper::getId, Paper::getName));
        Map<Long, String> depts = deptMapper.selectList(null).stream()
                .collect(Collectors.toMap(Dept::getId, Dept::getName));
        Map<Long, String> users = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName));
        for (ExamProject p : list) {
            p.setPaperName(papers.get(p.getPaperId()));
            p.setDeptName(depts.get(p.getDeptId()));
            p.setCreatorName(users.get(p.getCreatorId()));
            Long count = candidateMapper.selectCount(new LambdaQueryWrapper<ExamCandidate>()
                    .eq(ExamCandidate::getProjectId, p.getId()));
            p.setCandidateCount(count == null ? 0 : count.intValue());
        }
    }

    public ExamProject get(Long id) {
        ExamProject p = projectMapper.selectById(id);
        if (p == null) throw new BusinessException("笔试项目不存在");
        checkRead(p);
        fill(Collections.singletonList(p));
        return p;
    }

    public void checkRead(ExamProject p) {
        LoginUser u = LoginUser.get();
        if (!u.isPm() && !Objects.equals(p.getDeptId(), u.getDeptId())) {
            throw new BusinessException(403, "无权访问其他部门项目");
        }
    }

    public void checkWrite(ExamProject p) {
        LoginUser u = LoginUser.get();
        if (u.isPm()) throw new BusinessException(403, "笔试项目由各部门招聘负责人维护");
        if (!Objects.equals(p.getDeptId(), u.getDeptId())) {
            throw new BusinessException(403, "无权操作其他部门项目");
        }
    }

    @Transactional
    public ExamProject create(ExamProject p) {
        LoginUser u = LoginUser.get();
        if (u.isPm()) throw new BusinessException(403, "笔试项目由各部门招聘负责人创建");
        validate(p);
        p.setId(null);
        p.setDeptId(u.getDeptId());
        p.setStatus(Consts.PROJECT_DRAFT);
        p.setCreatorId(u.getId());
        p.setDeleted(0);
        projectMapper.insert(p);
        return p;
    }

    private void validate(ExamProject p) {
        if (StrUtil.isBlank(p.getName())) throw new BusinessException("项目名称不能为空");
        if (p.getPaperId() == null) throw new BusinessException("请选择试卷");
        Paper paper = paperMapper.selectById(p.getPaperId());
        if (paper == null) throw new BusinessException("试卷不存在");
        LoginUser u = LoginUser.get();
        if (!Objects.equals(paper.getDeptId(), u.getDeptId())) throw new BusinessException("只能关联本部门试卷");
        if (p.getDurationMin() == null || p.getDurationMin() <= 0) throw new BusinessException("考试时长须大于0");
        if (p.getStartTime() == null || p.getEndTime() == null) throw new BusinessException("请设置开考与截止时间");
        if (!p.getEndTime().isAfter(p.getStartTime())) throw new BusinessException("截止时间须晚于开考时间");
        if (p.getPassScore() == null) throw new BusinessException("请设置及格线");
        if (p.getPassScore().compareTo(paper.getTotalScore()) > 0)
            throw new BusinessException("及格线不能大于试卷总分 " + paper.getTotalScore());
    }

    @Transactional
    public ExamProject update(Long id, ExamProject p) {
        ExamProject old = get(id);
        checkWrite(old);
        validate(p);
        old.setName(p.getName());
        old.setPaperId(p.getPaperId());
        old.setDurationMin(p.getDurationMin());
        old.setLocation(p.getLocation());
        old.setBatch(p.getBatch());
        old.setStartTime(p.getStartTime());
        old.setEndTime(p.getEndTime());
        old.setPassScore(p.getPassScore());
        projectMapper.updateById(old);
        return old;
    }

    @Transactional
    public void delete(Long id) {
        ExamProject p = get(id);
        checkWrite(p);
        Long records = recordMapper.selectCount(new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getProjectId, id));
        if (records != null && records > 0) throw new BusinessException("项目已有考试记录，无法删除");
        candidateMapper.delete(new LambdaQueryWrapper<ExamCandidate>().eq(ExamCandidate::getProjectId, id));
        projectMapper.deleteById(id);
    }

    @Transactional
    public ExamProject publish(Long id) {
        ExamProject p = get(id);
        checkWrite(p);
        Paper paper = paperMapper.selectById(p.getPaperId());
        if (!Consts.PAPER_PUBLISHED.equals(paper.getStatus())) {
            throw new BusinessException("关联试卷尚未发布（需先签署保密承诺书并发布试卷）");
        }
        Long candidates = candidateMapper.selectCount(new LambdaQueryWrapper<ExamCandidate>()
                .eq(ExamCandidate::getProjectId, id));
        if (candidates == null || candidates == 0) {
            throw new BusinessException("请先导入考生名单再发布项目");
        }
        p.setStatus(Consts.PROJECT_PUBLISHED);
        projectMapper.updateById(p);
        return p;
    }

    /** 结束项目：未交卷考生记缺考 */
    @Transactional
    public ExamProject finish(Long id) {
        ExamProject p = get(id);
        checkWrite(p);
        markAbsent(p);
        p.setStatus(Consts.PROJECT_FINISHED);
        projectMapper.updateById(p);
        return p;
    }

    public void markAbsent(ExamProject p) {
        List<ExamCandidate> candidates = candidateMapper.selectList(new LambdaQueryWrapper<ExamCandidate>()
                .eq(ExamCandidate::getProjectId, p.getId()));
        for (ExamCandidate c : candidates) {
            Long exist = recordMapper.selectCount(new LambdaQueryWrapper<ExamRecord>()
                    .eq(ExamRecord::getCandidateId, c.getId()));
            if (exist == null || exist == 0) {
                ExamRecord r = new ExamRecord();
                r.setProjectId(p.getId());
                r.setCandidateId(c.getId());
                r.setPaperId(p.getPaperId());
                r.setStatus(Consts.REC_SUBMITTED);
                r.setSubmitType("AUTO");
                r.setSubmitTime(LocalDateTime.now());
                r.setAbsent(1);
                r.setObjectiveScore(java.math.BigDecimal.ZERO);
                r.setSubjectiveScore(java.math.BigDecimal.ZERO);
                r.setTotalScore(java.math.BigDecimal.ZERO);
                r.setAiDone(1);
                r.setReviewDone(1);
                r.setPassed(0);
                recordMapper.insert(r);
                c.setStatus("SUBMITTED");
                candidateMapper.updateById(c);
            }
        }
    }
}