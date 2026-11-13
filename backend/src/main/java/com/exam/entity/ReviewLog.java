package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("review_log")
public class ReviewLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long answerId;
    private Long recordId;
    private Long reviewerId;
    /** AI_SCORE / CONFIRM / ADJUST */
    private String action;
    private BigDecimal oldScore;
    private BigDecimal newScore;
    private String comment;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String reviewerName;
}