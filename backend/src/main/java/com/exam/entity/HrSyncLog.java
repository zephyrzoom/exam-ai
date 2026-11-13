package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("hr_sync_log")
public class HrSyncLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    /** PULL / PUSH */
    private String direction;
    private String payload;
    /** SUCCESS / FAILED / MOCK */
    private String status;
    private String message;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}