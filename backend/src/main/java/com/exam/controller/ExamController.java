package com.exam.controller;

import com.exam.common.Result;
import com.exam.service.ExamService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exam")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @PostMapping("/enter")
    public Result<Map<String, Object>> enter(@RequestBody Map<String, Object> body) {
        return Result.ok(examService.enter(Long.valueOf(body.get("projectId").toString())));
    }

    @PostMapping("/save")
    public Result<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        Long recordId = Long.valueOf(body.get("recordId").toString());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> answers = (List<Map<String, Object>>) body.get("answers");
        return Result.ok(examService.save(recordId, answers));
    }

    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
        return Result.ok(examService.submit(Long.valueOf(body.get("recordId").toString())));
    }

    @GetMapping("/status/{recordId}")
    public Result<Map<String, Object>> status(@PathVariable Long recordId) {
        return Result.ok(examService.status(recordId));
    }

    @PostMapping("/cheat")
    public Result<?> cheat(@RequestBody Map<String, Object> body) {
        examService.reportCheat(Long.valueOf(body.get("recordId").toString()),
                (String) body.get("eventType"), (String) body.get("detail"));
        return Result.ok();
    }
}