package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dept")
public class Dept {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}