package com.exam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.BusinessException;
import com.exam.common.Result;
import com.exam.config.LoginUser;
import com.exam.entity.*;
import com.exam.mapper.NotifyLogMapper;
import com.exam.mapper.PaperMapper;
import com.exam.service.CandidateService;
import com.exam.service.MailService;
import com.exam.service.ProjectService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final CandidateService candidateService;
    private final MailService mailService;
    private final PaperMapper paperMapper;
    private final NotifyLogMapper notifyLogMapper;

    public ProjectController(ProjectService projectService, CandidateService candidateService,
                             MailService mailService, PaperMapper paperMapper, NotifyLogMapper notifyLogMapper) {
        this.projectService = projectService;
        this.candidateService = candidateService;
        this.mailService = mailService;
        this.paperMapper = paperMapper;
        this.notifyLogMapper = notifyLogMapper;
    }

    @GetMapping
    public Result<Page<ExamProject>> page(@RequestParam(defaultValue = "1") int pageNum,
                                          @RequestParam(defaultValue = "10") int pageSize,
                                          @RequestParam(required = false) String name,
                                          @RequestParam(required = false) String status) {
        return Result.ok(projectService.page(pageNum, pageSize, name, status));
    }

    @GetMapping("/{id}")
    public Result<ExamProject> get(@PathVariable Long id) {
        return Result.ok(projectService.get(id));
    }

    @PostMapping
    public Result<ExamProject> create(@RequestBody ExamProject project) {
        return Result.ok(projectService.create(project));
    }

    @PutMapping("/{id}")
    public Result<ExamProject> update(@PathVariable Long id, @RequestBody ExamProject project) {
        return Result.ok(projectService.update(id, project));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/publish")
    public Result<ExamProject> publish(@PathVariable Long id) {
        return Result.ok(projectService.publish(id));
    }

    @PostMapping("/{id}/finish")
    public Result<ExamProject> finish(@PathVariable Long id) {
        return Result.ok(projectService.finish(id));
    }

    // ---------------- 考生名单 ----------------

    @GetMapping("/{id}/candidates")
    public Result<List<ExamCandidate>> candidates(@PathVariable Long id) {
        return Result.ok(candidateService.list(id));
    }

    @PostMapping("/{id}/candidates")
    public Result<ExamCandidate> addCandidate(@PathVariable Long id, @RequestBody ExamCandidate candidate) {
        return Result.ok(candidateService.addManual(id, candidate));
    }

    @DeleteMapping("/{id}/candidates/{candidateId}")
    public Result<?> deleteCandidate(@PathVariable Long id, @PathVariable Long candidateId) {
        candidateService.delete(id, candidateId);
        return Result.ok();
    }

    @PostMapping("/{id}/candidates/hr-sync")
    public Result<Map<String, Object>> hrSync(@PathVariable Long id) {
        return Result.ok(candidateService.hrSync(id));
    }

    @PostMapping("/{id}/candidates/import")
    public Result<Map<String, Object>> importCandidates(@PathVariable Long id,
                                                        @RequestParam("file") MultipartFile file) throws IOException {
        return Result.ok(candidateService.importExcel(id, file));
    }

    @GetMapping("/{id}/candidates/export")
    public void exportCandidates(@PathVariable Long id, HttpServletResponse response) throws IOException {
        candidateService.export(id, response);
    }

    @GetMapping("/candidates/template")
    public void candidateTemplate(HttpServletResponse response) throws IOException {
        candidateService.downloadTemplate(response);
    }

    // ---------------- 考试通知 ----------------

    @PostMapping("/{id}/notify")
    public Result<NotifyLog> notify(@PathVariable Long id) {
        ExamProject project = projectService.get(id);
        projectService.checkWrite(project);
        List<ExamCandidate> candidates = candidateService.list(id);
        if (candidates.isEmpty()) throw new BusinessException("考生名单为空，无法发送通知");
        Paper paper = paperMapper.selectById(project.getPaperId());
        return Result.ok(mailService.sendExamNotice(project, paper, candidates, LoginUser.get().getId()));
    }

    @GetMapping("/{id}/notify-logs")
    public Result<List<NotifyLog>> notifyLogs(@PathVariable Long id) {
        projectService.get(id);
        return Result.ok(notifyLogMapper.selectList(new LambdaQueryWrapper<NotifyLog>()
                .eq(NotifyLog::getProjectId, id).orderByDesc(NotifyLog::getId)));
    }
}