package com.exam.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.Result;
import com.exam.entity.Question;
import com.exam.service.QuestionService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    public Result<Page<Question>> page(@RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String bankType,
                                       @RequestParam(required = false) Long deptId,
                                       @RequestParam(required = false) String type,
                                       @RequestParam(required = false) String skill,
                                       @RequestParam(required = false) Integer difficulty,
                                       @RequestParam(required = false) String knowledgePoint,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String status) {
        return Result.ok(questionService.page(pageNum, pageSize, bankType, deptId, type, skill,
                difficulty, knowledgePoint, keyword, status));
    }

    @GetMapping("/{id}")
    public Result<Question> get(@PathVariable Long id) {
        return Result.ok(questionService.get(id));
    }

    @PostMapping
    public Result<Question> create(@RequestBody Question question) {
        return Result.ok(questionService.create(question));
    }

    @PutMapping("/{id}")
    public Result<Question> update(@PathVariable Long id, @RequestBody Question question) {
        return Result.ok(questionService.update(id, question));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        questionService.delete(id);
        return Result.ok();
    }

    @PostMapping("/import")
    public Result<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(defaultValue = "PRIVATE") String bankType) throws IOException {
        return Result.ok(questionService.importExcel(file, bankType));
    }

    @GetMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) String bankType,
                       @RequestParam(required = false) Long deptId,
                       @RequestParam(required = false) String type,
                       @RequestParam(required = false) String skill,
                       @RequestParam(required = false) Integer difficulty,
                       @RequestParam(required = false) String keyword) throws IOException {
        questionService.exportExcel(response, bankType, deptId, type, skill, difficulty, keyword);
    }

    @GetMapping("/template")
    public void template(HttpServletResponse response) throws IOException {
        questionService.downloadTemplate(response);
    }

    @PostMapping("/{id}/contribute")
    public Result<Question> contribute(@PathVariable Long id) {
        return Result.ok(questionService.contribute(id));
    }

    @GetMapping("/contributions")
    public Result<Page<Question>> contributions(@RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(questionService.contributions(pageNum, pageSize));
    }

    @PostMapping("/{id}/approve")
    public Result<?> approve(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        questionService.approve(id, Boolean.TRUE.equals(body.get("pass")));
        return Result.ok();
    }
}