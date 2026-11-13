package com.exam.controller;

import com.exam.common.Result;
import com.exam.entity.ExamRecord;
import com.exam.service.ReportService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/projects/{id}/scores")
    public Result<List<ExamRecord>> scores(@PathVariable Long id) {
        return Result.ok(reportService.scores(id));
    }

    @GetMapping("/projects/{id}/scores/export")
    public void exportScores(@PathVariable Long id, HttpServletResponse response) throws IOException {
        reportService.exportScores(id, response);
    }

    @GetMapping("/projects/{id}/analysis")
    public Result<Map<String, Object>> analysis(@PathVariable Long id) {
        return Result.ok(reportService.analysis(id));
    }

    @GetMapping("/records/{id}/report")
    public Result<Map<String, Object>> report(@PathVariable Long id) {
        return Result.ok(reportService.report(id));
    }
}