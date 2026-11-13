package com.exam.config;

import com.exam.common.BusinessException;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isEmpty()) {
            throw new BusinessException(401, "未登录");
        }
        try {
            Claims claims = jwtUtil.parse(token);
            LoginUser user = new LoginUser();
            user.setId(claims.get("uid", Long.class));
            user.setRole(claims.get("role", String.class));
            Object deptId = claims.get("deptId");
            user.setDeptId(deptId == null ? null : Long.valueOf(deptId.toString()));
            user.setName(claims.get("name", String.class));
            user.setType(claims.get("type", String.class));
            LoginUser.set(user);
        } catch (Exception e) {
            throw new BusinessException(401, "登录已过期，请重新登录");
        }

        String uri = request.getRequestURI();
        LoginUser u = LoginUser.get();
        if (uri.startsWith("/api/auth/")) {
            return true;
        }
        if (uri.startsWith("/api/exam/")) {
            if (!u.isCandidate()) {
                throw new BusinessException(403, "仅考生可访问考试接口");
            }
        } else if (u.isCandidate()) {
            throw new BusinessException(403, "考生无权访问管理接口");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        LoginUser.clear();
    }
}