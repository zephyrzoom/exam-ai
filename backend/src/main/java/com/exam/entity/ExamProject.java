package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("exam_project")
public class ExamProject {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long paperId;
    private Long deptId;
    private Integer durationMin;
    private String location;
    private String batch;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal passScore;
    /** DRAFT / PUBLISHED / FINISHED */
    private String status;
    private Long creatorId;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String paperName;
    @TableField(exist = false)
    private String deptName;
    @TableField(exist = false)
    private String creatorName;
    @TableField(exist = false)
    private Integer candidateCount;
}