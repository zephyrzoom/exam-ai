package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("paper")
public class Paper {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long deptId;
    /** DRAFT / PUBLISHED */
    private String status;
    private BigDecimal totalScore;
    private Integer questionCount;
    private String description;
    private String jobRequirement;
    private String assessTarget;
    private Long creatorId;
    private Long pledgeUserId;
    private LocalDateTime pledgeTime;
    private String pledgeText;
    private LocalDateTime publishedAt;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String deptName;
    @TableField(exist = false)
    private String creatorName;
    @TableField(exist = false)
    private String pledgeUserName;
}