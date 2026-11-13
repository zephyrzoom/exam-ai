package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("answer_record")
public class AnswerRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recordId;
    private Long questionId;
    private String type;
    private String answer;
    private BigDecimal fullScore;
    private BigDecimal score;
    private BigDecimal aiScore;
    private String aiComment;
    private BigDecimal reviewScore;
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private String reviewComment;
    /** PENDING / AI_DONE / REVIEWED */
    private String status;
    private Integer sortOrder;

    @TableField(exist = false)
    private Question question;
    @TableField(exist = false)
    private String reviewerName;
}