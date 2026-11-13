package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notify_log")
public class NotifyLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String type;
    private String toList;
    private String ccList;
    private String subject;
    private String content;
    /** SENT / SIMULATED / FAILED */
    private String status;
    private String error;
    private Long operatorId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String operatorName;
}