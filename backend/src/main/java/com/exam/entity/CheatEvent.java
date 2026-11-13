package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cheat_event")
public class CheatEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recordId;
    /** BLUR / VISIBILITY / COPY / PASTE / CONTEXT_MENU / FULLSCREEN_EXIT */
    private String eventType;
    private String detail;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}