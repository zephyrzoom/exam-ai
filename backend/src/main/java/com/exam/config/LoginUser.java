package com.exam.config;

import lombok.Data;

@Data
public class LoginUser {
    private Long id;
    private String role;
    private Long deptId;
    private String name;
    /** ADMIN / CANDIDATE */
    private String type;

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public boolean isPm() {
        return "PM_ADMIN".equals(role);
    }

    public boolean isCandidate() {
        return "CANDIDATE".equals(type);
    }
}