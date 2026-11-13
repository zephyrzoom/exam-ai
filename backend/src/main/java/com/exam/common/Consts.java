package com.exam.common;

public interface Consts {
    String ROLE_PM = "PM_ADMIN";
    String ROLE_DEPT = "DEPT_ADMIN";
    String ROLE_CANDIDATE = "CANDIDATE";

    String BANK_PUBLIC = "PUBLIC";
    String BANK_PRIVATE = "PRIVATE";

    String Q_SINGLE = "SINGLE";
    String Q_MULTI = "MULTI";
    String Q_JUDGE = "JUDGE";
    String Q_ESSAY = "ESSAY";

    String STATUS_NORMAL = "NORMAL";
    String STATUS_PENDING = "PENDING";
    String STATUS_DISABLED = "DISABLED";

    String PAPER_DRAFT = "DRAFT";
    String PAPER_PUBLISHED = "PUBLISHED";

    String PROJECT_DRAFT = "DRAFT";
    String PROJECT_PUBLISHED = "PUBLISHED";
    String PROJECT_FINISHED = "FINISHED";

    String REC_IN_PROGRESS = "IN_PROGRESS";
    String REC_SUBMITTED = "SUBMITTED";
    String REC_GRADED = "GRADED";

    String ANS_PENDING = "PENDING";
    String ANS_AI_DONE = "AI_DONE";
    String ANS_REVIEWED = "REVIEWED";

    String[] DIFFICULTY_NAMES = {"", "入门", "简单", "中等", "较难", "困难"};
}