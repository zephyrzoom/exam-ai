package com.exam.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.Result;
import com.exam.entity.AnswerRecord;
import com.exam.entity.ExamRecord;
import com.exam.service.GradingService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/grading")
public class GradingController {

    private final GradingService gradingService;

    public GradingController(GradingService gradingService) {
        this.gradingService = gradingService;
    }

    @GetMapping("/records")
    public Result<Page<ExamRecord>> records(@RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) Long projectId,
                                            @RequestParam(required = false) String state) {
        return Result.ok(gradingService.gradingPage(pageNum, pageSize, projectId, state));
    }

    @GetMapping("/records/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(gradingService.gradingDetail(id));
    }

    @PostMapping("/answers/{id}/review")
    public Result<AnswerRecord> review(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal score = new BigDecimal(body.get("score").toString());
        String comment = (String) body.get("comment");
        return Result.ok(gradingService.review(id, score, comment));
    }

    @PostMapping("/records/{id}/finalize")
    public Result<ExamRecord> finalizeRecord(@PathVariable Long id) {
        return Result.ok(gradingService.finalizeRecord(id));
    }
}