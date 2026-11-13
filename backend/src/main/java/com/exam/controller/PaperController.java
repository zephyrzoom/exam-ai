package com.exam.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.Result;
import com.exam.entity.Paper;
import com.exam.entity.PaperLog;
import com.exam.entity.PaperQuestion;
import com.exam.service.ExportService;
import com.exam.service.PaperService;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/papers")
public class PaperController {

    private final PaperService paperService;
    private final ExportService exportService;

    public PaperController(PaperService paperService, ExportService exportService) {
        this.paperService = paperService;
        this.exportService = exportService;
    }

    @GetMapping
    public Result<Page<Paper>> page(@RequestParam(defaultValue = "1") int pageNum,
                                    @RequestParam(defaultValue = "10") int pageSize,
                                    @RequestParam(required = false) String name,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) Long deptId,
                                    @RequestParam(required = false) String createdBegin,
                                    @RequestParam(required = false) String createdEnd) {
        return Result.ok(paperService.page(pageNum, pageSize, name, status, deptId, createdBegin, createdEnd));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(paperService.detail(id));
    }

    @PostMapping
    public Result<Paper> create(@RequestBody Paper paper) {
        return Result.ok(paperService.create(paper));
    }

    @PostMapping("/ai-compose")
    public Result<Paper> aiCompose(@RequestBody PaperService.AiComposeRequest req) {
        return Result.ok(paperService.aiCompose(req));
    }

    @PutMapping("/{id}")
    public Result<Paper> update(@PathVariable Long id, @RequestBody Paper paper) {
        return Result.ok(paperService.update(id, paper));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        paperService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/copy")
    public Result<Paper> copy(@PathVariable Long id) {
        return Result.ok(paperService.copy(id));
    }

    @PostMapping("/{id}/questions")
    public Result<?> addQuestions(@PathVariable Long id, @RequestBody List<Map<String, Object>> items) {
        paperService.addQuestions(id, items);
        return Result.ok();
    }

    @DeleteMapping("/{id}/questions/{questionId}")
    public Result<?> removeQuestion(@PathVariable Long id, @PathVariable Long questionId) {
        paperService.removeQuestion(id, questionId);
        return Result.ok();
    }

    @PutMapping("/{id}/questions/{questionId}")
    public Result<?> updateScore(@PathVariable Long id, @PathVariable Long questionId,
                                 @RequestBody Map<String, Object> body) {
        paperService.updateQuestionScore(id, questionId, new BigDecimal(body.get("score").toString()));
        return Result.ok();
    }

    @PostMapping("/{id}/reorder")
    public Result<?> reorder(@PathVariable Long id, @RequestBody List<Long> questionIds) {
        paperService.reorder(id, questionIds);
        return Result.ok();
    }

    @GetMapping("/{id}/logs")
    public Result<List<PaperLog>> logs(@PathVariable Long id) {
        return Result.ok(paperService.logs(id));
    }

    @GetMapping("/pledge-template")
    public Result<String> pledgeTemplate() {
        return Result.ok(PaperService.PLEDGE_TEMPLATE);
    }

    @PostMapping("/{id}/pledge")
    public Result<Paper> pledge(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(paperService.pledge(id, body.get("signerName")));
    }

    @PostMapping("/{id}/publish")
    public Result<Paper> publish(@PathVariable Long id) {
        return Result.ok(paperService.publish(id));
    }

    @GetMapping("/{id}/export")
    public void export(@PathVariable Long id, @RequestParam(defaultValue = "word") String format,
                       HttpServletResponse response) throws IOException {
        Map<String, Object> detail = paperService.detail(id);
        Paper paper = (Paper) detail.get("paper");
        @SuppressWarnings("unchecked")
        List<PaperQuestion> pqs = (List<PaperQuestion>) detail.get("questions");
        exportService.exportPaper(paper, pqs, format, response);
    }
}