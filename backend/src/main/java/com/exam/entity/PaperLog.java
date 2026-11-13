package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("paper_log")
public class PaperLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paperId;
    private String action;
    private String detail;
    private Long operatorId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String operatorName;
}