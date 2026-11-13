package com.exam.controller;

import com.exam.common.Result;
import com.exam.config.LoginUser;
import com.exam.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        return Result.ok(authService.login(body.get("username"), body.get("password")));
    }

    @PostMapping("/candidate-login")
    public Result<Map<String, Object>> candidateLogin(@RequestBody Map<String, String> body) {
        return Result.ok(authService.candidateLogin(body.get("idCard"), body.get("name")));
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(authService.me());
    }

    @GetMapping("/my-projects")
    public Result<List<Map<String, Object>>> myProjects() {
        LoginUser u = LoginUser.get();
        return Result.ok(authService.myProjectsByCandidateId(u.getId()));
    }
}