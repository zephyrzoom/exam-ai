package com.exam.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.exam.entity.ExamCandidate;
import com.exam.entity.ExamProject;
import com.exam.entity.HrSyncLog;
import com.exam.mapper.HrSyncLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 人事管理系统对接。
 * 配置 exam.hr.base-url 后走真实接口：
 *   GET  {base}/candidates?projectId=xx  拉取考生名单
 *   POST {base}/exam-results             回传测评结果
 * 未配置时使用内置 Mock：生成演示名单、回传仅记录日志。
 */
@Slf4j
@Service
public class HrClient {

    @Value("${exam.hr.base-url:}")
    private String baseUrl;

    private final HrSyncLogMapper syncLogMapper;

    public HrClient(HrSyncLogMapper syncLogMapper) {
        this.syncLogMapper = syncLogMapper;
    }

    public boolean realEnabled() {
        return StrUtil.isNotBlank(baseUrl);
    }

    public List<ExamCandidate> pullCandidates(ExamProject project) {
        List<ExamCandidate> result = new ArrayList<>();
        String status;
        String message;
        String payload;
        if (realEnabled()) {
            try {
                String resp = HttpRequest.get(baseUrl + "/candidates")
                        .form("projectId", project.getId())
                        .form("batch", StrUtil.nullToEmpty(project.getBatch()))
                        .timeout(30000).execute().body();
                JSONArray arr = JSONUtil.parseArray(resp);
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    ExamCandidate c = new ExamCandidate();
                    c.setProjectId(project.getId());
                    c.setHrId(o.getStr("hrId"));
                    c.setName(o.getStr("name"));
                    c.setIdCard(o.getStr("idCard"));
                    c.setPhone(o.getStr("phone"));
                    c.setEmail(o.getStr("email"));
                    c.setVendor(o.getStr("vendor"));
                    c.setDeptName(o.getStr("deptName"));
                    c.setSource("HR");
                    c.setStatus("NOT_ATTENDED");
                    result.add(c);
                }
                status = "SUCCESS";
                message = "从人事系统拉取 " + result.size() + " 名考生";
                payload = resp;
            } catch (Exception e) {
                status = "FAILED";
                message = "人事系统拉取失败：" + e.getMessage();
                payload = null;
                saveLog(project.getId(), "PULL", payload, status, message);
                throw new com.exam.common.BusinessException(message);
            }
        } else {
            result = mockCandidates(project);
            status = "MOCK";
            message = "未配置人事系统接口(exam.hr.base-url)，使用演示名单 " + result.size() + " 人";
            payload = JSONUtil.toJsonStr(result);
        }
        saveLog(project.getId(), "PULL", payload, status, message);
        return result;
    }

    public void pushResults(ExamProject project, String payloadJson) {
        String status;
        String message;
        if (realEnabled()) {
            try {
                String resp = HttpRequest.post(baseUrl + "/exam-results")
                        .header("Content-Type", "application/json")
                        .body(payloadJson)
                        .timeout(30000).execute().body();
                status = "SUCCESS";
                message = "回传成功：" + StrUtil.maxLength(resp, 200);
            } catch (Exception e) {
                status = "FAILED";
                message = "回传失败：" + e.getMessage();
                saveLog(project.getId(), "PUSH", payloadJson, status, message);
                throw new com.exam.common.BusinessException(message);
            }
        } else {
            status = "MOCK";
            message = "未配置人事系统接口，回传内容已存档（模拟回传成功）";
        }
        saveLog(project.getId(), "PUSH", payloadJson, status, message);
    }

    private void saveLog(Long projectId, String direction, String payload, String status, String message) {
        HrSyncLog l = new HrSyncLog();
        l.setProjectId(projectId);
        l.setDirection(direction);
        l.setPayload(payload);
        l.setStatus(status);
        l.setMessage(message);
        syncLogMapper.insert(l);
    }

    private static final String[] MOCK_NAMES = {"陈嘉豪", "林晓芸", "黄志远", "苏婉婷", "刘子墨", "周雨桐", "吴俊熙", "郑雅琪"};
    private static final String[] MOCK_VENDORS = {"中软国际", "软通动力", "博彦科技", "文思海辉"};

    private List<ExamCandidate> mockCandidates(ExamProject project) {
        List<ExamCandidate> list = new ArrayList<>();
        Random random = new Random(project.getId() * 31 + 7);
        for (int i = 0; i < MOCK_NAMES.length; i++) {
            ExamCandidate c = new ExamCandidate();
            c.setProjectId(project.getId());
            c.setHrId("HR-" + project.getId() + "-" + (i + 1));
            c.setName(MOCK_NAMES[i]);
            c.setIdCard(mockIdCard(random, 1994 + random.nextInt(8)));
            c.setPhone("13" + (100000000 + random.nextInt(899999999)));
            c.setEmail("candidate" + (i + 1) + "_p" + project.getId() + "@example.com");
            c.setVendor(MOCK_VENDORS[random.nextInt(MOCK_VENDORS.length)]);
            c.setDeptName(project.getDeptId() == null ? "" : String.valueOf(project.getDeptId()));
            c.setSource("HR");
            c.setStatus("NOT_ATTENDED");
            list.add(c);
        }
        return list;
    }

    private String mockIdCard(Random random, int year) {
        StringBuilder sb = new StringBuilder("110101");
        sb.append(year);
        sb.append(String.format("%02d", 1 + random.nextInt(12)));
        sb.append(String.format("%02d", 1 + random.nextInt(28)));
        sb.append(String.format("%03d", random.nextInt(999)));
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checks = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        for (int i = 0; i < 17; i++) sum += (sb.charAt(i) - '0') * weights[i];
        sb.append(checks[sum % 11]);
        return sb.toString();
    }
}