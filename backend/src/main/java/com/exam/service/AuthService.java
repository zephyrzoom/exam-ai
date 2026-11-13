package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.BusinessException;
import com.exam.common.Consts;
import com.exam.config.JwtUtil;
import com.exam.config.LoginUser;
import com.exam.entity.ExamCandidate;
import com.exam.entity.ExamProject;
import com.exam.entity.SysUser;
import com.exam.mapper.ExamCandidateMapper;
import com.exam.mapper.ExamProjectMapper;
import com.exam.mapper.SysUserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final ExamCandidateMapper candidateMapper;
    private final ExamProjectMapper projectMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(SysUserMapper userMapper, ExamCandidateMapper candidateMapper,
                       ExamProjectMapper projectMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.candidateMapper = candidateMapper;
        this.projectMapper = projectMapper;
        this.jwtUtil = jwtUtil;
    }

    public Map<String, Object> login(String username, String password) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (user == null || !encoder.matches(password, user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已禁用");
        }
        String token = jwtUtil.create(user.getId(), user.getRole(), user.getDeptId(), user.getRealName(), "ADMIN");
        Map<String, Object> res = new HashMap<>();
        res.put("token", token);
        res.put("user", user);
        return res;
    }

    public Map<String, Object> candidateLogin(String idCard, String name) {
        List<ExamCandidate> candidates = candidateMapper.selectList(new LambdaQueryWrapper<ExamCandidate>()
                .eq(ExamCandidate::getIdCard, idCard)
                .eq(ExamCandidate::getName, name));
        if (candidates.isEmpty()) {
            throw new BusinessException("未找到考生信息，请核对身份证号与姓名，或联系招聘管理员");
        }
        ExamCandidate first = candidates.get(0);
        String token = jwtUtil.create(first.getId(), Consts.ROLE_CANDIDATE, null, first.getName(), "CANDIDATE");
        Map<String, Object> res = new HashMap<>();
        res.put("token", token);
        res.put("candidate", first);
        res.put("projects", myProjects(idCard));
        return res;
    }

    public List<Map<String, Object>> myProjects(String idCard) {
        List<ExamCandidate> candidates = candidateMapper.selectList(new LambdaQueryWrapper<ExamCandidate>()
                .eq(ExamCandidate::getIdCard, idCard));
        List<Map<String, Object>> list = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (ExamCandidate c : candidates) {
            ExamProject p = projectMapper.selectById(c.getProjectId());
            if (p == null || !Consts.PROJECT_PUBLISHED.equals(p.getStatus())) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("candidateId", c.getId());
            item.put("projectId", p.getId());
            item.put("projectName", p.getName());
            item.put("location", p.getLocation());
            item.put("batch", p.getBatch());
            item.put("durationMin", p.getDurationMin());
            item.put("startTime", p.getStartTime());
            item.put("endTime", p.getEndTime());
            item.put("candidateStatus", c.getStatus());
            String state;
            if (now.isBefore(p.getStartTime())) {
                state = "NOT_STARTED";
            } else if (now.isAfter(p.getEndTime())) {
                state = "ENDED";
            } else {
                state = "OPEN";
            }
            item.put("state", state);
            list.add(item);
        }
        return list;
    }

    public List<Map<String, Object>> myProjectsByCandidateId(Long candidateId) {
        ExamCandidate c = candidateMapper.selectById(candidateId);
        if (c == null) throw new BusinessException("考生不存在");
        return myProjects(c.getIdCard());
    }

    public Map<String, Object> me() {
        LoginUser u = LoginUser.get();
        Map<String, Object> res = new HashMap<>();
        res.put("id", u.getId());
        res.put("role", u.getRole());
        res.put("deptId", u.getDeptId());
        res.put("name", u.getName());
        res.put("type", u.getType());
        if (!u.isCandidate()) {
            SysUser user = userMapper.selectById(u.getId());
            res.put("user", user);
        }
        return res;
    }

    public String encode(String raw) {
        return encoder.encode(raw);
    }
}