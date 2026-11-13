package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("shortlist_plan")
public class ShortlistPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long projectId;
    /** TOP_N / RATIO / SCORE_LINE */
    private String ruleType;
    private Integer topN;
    /** 百分比，如 30 表示前 30% */
    private BigDecimal ratio;
    private BigDecimal minScore;
    /** DRAFT / CONFIRMED */
    private String status;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String confirmedByName;
}