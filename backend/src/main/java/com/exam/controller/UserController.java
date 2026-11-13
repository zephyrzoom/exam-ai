package com.exam.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.BusinessException;
import com.exam.common.Consts;
import com.exam.common.Result;
import com.exam.config.LoginUser;
import com.exam.entity.Dept;
import com.exam.entity.SysUser;
import com.exam.mapper.DeptMapper;
import com.exam.mapper.SysUserMapper;
import com.exam.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class UserController {

    private final SysUserMapper userMapper;
    private final DeptMapper deptMapper;
    private final AuthService authService;

    public UserController(SysUserMapper userMapper, DeptMapper deptMapper, AuthService authService) {
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
        this.authService = authService;
    }

    private void requirePm() {
        if (!LoginUser.get().isPm()) throw new BusinessException(403, "仅项目管理部可管理用户");
    }

    @GetMapping("/depts")
    public Result<List<Dept>> depts() {
        return Result.ok(deptMapper.selectList(null));
    }

    @PostMapping("/depts")
    public Result<Dept> createDept(@RequestBody Dept dept) {
        requirePm();
        if (StrUtil.isBlank(dept.getName())) throw new BusinessException("部门名称不能为空");
        dept.setId(null);
        deptMapper.insert(dept);
        return Result.ok(dept);
    }

    @GetMapping("/users")
    public Result<List<SysUser>> users() {
        requirePm();
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getId));
        Map<Long, String> deptNames = deptMapper.selectList(null).stream()
                .collect(Collectors.toMap(Dept::getId, Dept::getName));
        users.forEach(u -> u.setDeptName(deptNames.get(u.getDeptId())));
        return Result.ok(users);
    }

    @PostMapping("/users")
    public Result<SysUser> create(@RequestBody Map<String, Object> body) {
        requirePm();
        SysUser u = new SysUser();
        u.setUsername((String) body.get("username"));
        u.setRealName((String) body.get("realName"));
        u.setRole((String) body.get("role"));
        u.setEmail((String) body.get("email"));
        u.setPhone((String) body.get("phone"));
        u.setDeptId(body.get("deptId") == null ? null : Long.valueOf(body.get("deptId").toString()));
        String password = (String) body.getOrDefault("password", "123456");
        validate(u);
        Long exist = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, u.getUsername()));
        if (exist != null && exist > 0) throw new BusinessException("用户名已存在");
        u.setPassword(authService.encode(password));
        u.setStatus(1);
        userMapper.insert(u);
        return Result.ok(u);
    }

    @PutMapping("/users/{id}")
    public Result<SysUser> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        requirePm();
        SysUser u = userMapper.selectById(id);
        if (u == null) throw new BusinessException("用户不存在");
        if (body.get("realName") != null) u.setRealName((String) body.get("realName"));
        if (body.get("role") != null) u.setRole((String) body.get("role"));
        if (body.get("email") != null) u.setEmail((String) body.get("email"));
        if (body.get("phone") != null) u.setPhone((String) body.get("phone"));
        if (body.containsKey("deptId")) u.setDeptId(body.get("deptId") == null ? null : Long.valueOf(body.get("deptId").toString()));
        if (body.get("status") != null) u.setStatus(Integer.valueOf(body.get("status").toString()));
        if (StrUtil.isNotBlank((String) body.get("password"))) {
            u.setPassword(authService.encode((String) body.get("password")));
        }
        validate(u);
        userMapper.updateById(u);
        return Result.ok(u);
    }

    @DeleteMapping("/users/{id}")
    public Result<?> delete(@PathVariable Long id) {
        requirePm();
        if (id.equals(LoginUser.get().getId())) throw new BusinessException("不能删除当前登录账号");
        userMapper.deleteById(id);
        return Result.ok();
    }

    private void validate(SysUser u) {
        if (StrUtil.isBlank(u.getUsername())) throw new BusinessException("用户名不能为空");
        if (StrUtil.isBlank(u.getRealName())) throw new BusinessException("姓名不能为空");
        if (!Consts.ROLE_PM.equals(u.getRole()) && !Consts.ROLE_DEPT.equals(u.getRole()))
            throw new BusinessException("角色不合法");
        if (Consts.ROLE_DEPT.equals(u.getRole()) && u.getDeptId() == null)
            throw new BusinessException("部门招聘负责人须指定部门");
    }
}