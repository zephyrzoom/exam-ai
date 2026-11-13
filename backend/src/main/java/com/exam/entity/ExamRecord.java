package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("exam_record")
public class ExamRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long candidateId;
    private Long paperId;
    private LocalDateTime startTime;
    private LocalDateTime deadline;
    private LocalDateTime submitTime;
    /** MANUAL / AUTO */
    private String submitType;
    /** IN_PROGRESS / SUBMITTED / GRADED */
    private String status;
    private BigDecimal objectiveScore;
    private BigDecimal subjectiveScore;
    private BigDecimal totalScore;
    private Integer aiDone;
    private Integer reviewDone;
    private Integer rankNo;
    private Integer passed;
    private Integer shortlisted;
    private Integer absent;
    private Integer switchCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String candidateName;
    @TableField(exist = false)
    private String idCard;
    @TableField(exist = false)
    private String vendor;
    @TableField(exist = false)
    private String projectName;
}