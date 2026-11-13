package com.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam_candidate")
public class ExamCandidate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String name;
    private String idCard;
    private String phone;
    private String email;
    private String vendor;
    private String deptName;
    /** HR / EXCEL / MANUAL */
    private String source;
    private String hrId;
    /** NOT_ATTENDED / ATTENDED / SUBMITTED */
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}