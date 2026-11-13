package com.exam.controller;

import com.exam.common.Result;
import com.exam.entity.HrSyncLog;
import com.exam.entity.ShortlistPlan;
import com.exam.service.ShortlistService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ShortlistController {

    private final ShortlistService shortlistService;

    public ShortlistController(ShortlistService shortlistService) {
        this.shortlistService = shortlistService;
    }

    @GetMapping("/projects/{id}/shortlist-plan")
    public Result<ShortlistPlan> getPlan(@PathVariable Long id) {
        return Result.ok(shortlistService.getPlan(id));
    }

    @PostMapping("/projects/{id}/shortlist-plan")
    public Result<ShortlistPlan> savePlan(@PathVariable Long id, @RequestBody ShortlistPlan plan) {
        return Result.ok(shortlistService.savePlan(id, plan));
    }

    @GetMapping("/projects/{id}/shortlist/preview")
    public Result<Map<String, Object>> preview(@PathVariable Long id) {
        return Result.ok(shortlistService.preview(id));
    }

    @PostMapping("/projects/{id}/shortlist/confirm")
    public Result<Map<String, Object>> confirm(@PathVariable Long id) {
        return Result.ok(shortlistService.confirm(id));
    }

    @PostMapping("/projects/{id}/shortlist/revoke")
    public Result<?> revoke(@PathVariable Long id) {
        shortlistService.revoke(id);
        return Result.ok();
    }

    @PostMapping("/projects/{id}/shortlist/push-hr")
    public Result<Map<String, Object>> pushHr(@PathVariable Long id) {
        return Result.ok(shortlistService.pushHr(id));
    }

    @GetMapping("/projects/{id}/hr-sync-logs")
    public Result<List<HrSyncLog>> syncLogs(@PathVariable Long id) {
        return Result.ok(shortlistService.syncLogs(id));
    }
}