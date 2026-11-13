package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("question")
public class Question {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** PUBLIC / PRIVATE */
    private String bankType;
    private Long deptId;
    /** SINGLE / MULTI / JUDGE / ESSAY */
    private String type;
    private String content;
    /** JSON 数组，如 ["A. xxx","B. xxx"]；判断题为 ["正确","错误"] */
    private String optionsJson;
    /** 单选 A；多选 ABC；判断 A/B；主观为参考答案 */
    private String answer;
    private String analysis;
    /** 主观题评分要点 */
    private String scorePoints;
    private String skill;
    /** 1入门 2简单 3中等 4较难 5困难 */
    private Integer difficulty;
    private String knowledgePoint;
    private BigDecimal defaultScore;
    /** NORMAL / PENDING / DISABLED */
    private String status;
    private Long sourceQuestionId;
    private Long creatorId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String deptName;
    @TableField(exist = false)
    private String creatorName;
}