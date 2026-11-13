package com.exam.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.exam.entity.Question;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 能力服务：
 * 1. 组卷抽题 —— 按题型/难度比例的加权随机算法（可解释、可复现的"智能抽题"）。
 * 2. 主观题初评 —— 配置 OpenAI 兼容接口(exam.ai.*)后调用大模型；未配置时使用评分要点匹配引擎兜底。
 */
@Slf4j
@Service
public class AiService {

    @Value("${exam.ai.base-url:}")
    private String baseUrl;
    @Value("${exam.ai.api-key:}")
    private String apiKey;
    @Value("${exam.ai.model:}")
    private String model;

    public boolean llmEnabled() {
        return StrUtil.isNotBlank(baseUrl) && StrUtil.isNotBlank(apiKey);
    }

    @Data
    public static class AiScore {
        private BigDecimal score;
        private String comment;
    }

    public AiScore scoreEssay(Question question, String candidateAnswer, BigDecimal fullScore) {
        if (StrUtil.isBlank(candidateAnswer)) {
            AiScore s = new AiScore();
            s.setScore(BigDecimal.ZERO);
            s.setComment("考生未作答，得 0 分。");
            return s;
        }
        if (llmEnabled()) {
            try {
                return llmScore(question, candidateAnswer, fullScore);
            } catch (Exception e) {
                log.warn("LLM 评分失败，回退到要点匹配引擎: {}", e.getMessage());
            }
        }
        return keywordScore(question, candidateAnswer, fullScore);
    }

    private AiScore llmScore(Question question, String answer, BigDecimal fullScore) {
        String prompt = "你是严谨的笔试阅卷专家。请根据评分要点对考生答案评分。\n"
                + "【题目】" + question.getContent() + "\n"
                + "【参考答案】" + StrUtil.nullToEmpty(question.getAnswer()) + "\n"
                + "【评分要点】" + StrUtil.nullToEmpty(question.getScorePoints()) + "\n"
                + "【满分】" + fullScore + "\n"
                + "【考生答案】" + answer + "\n"
                + "要求：按要点覆盖程度给分，可给小数；只输出 JSON，格式 {\"score\": 数字, \"comment\": \"评分说明(指出命中与缺失的要点)\"}";
        JSONObject body = new JSONObject();
        body.set("model", model);
        cn.hutool.json.JSONArray messages = new cn.hutool.json.JSONArray();
        JSONObject msg = new JSONObject();
        msg.set("role", "user");
        msg.set("content", prompt);
        messages.add(msg);
        body.set("messages", messages);
        body.set("temperature", 0.1);
        String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
        String resp = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body.toString())
                .timeout(60000)
                .execute().body();
        JSONObject json = JSONUtil.parseObj(resp);
        String content = json.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getStr("content");
        content = content.replaceAll("```json", "").replaceAll("```", "").trim();
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        JSONObject result = JSONUtil.parseObj(content.substring(start, end + 1));
        AiScore s = new AiScore();
        BigDecimal score = result.getBigDecimal("score");
        if (score == null) score = BigDecimal.ZERO;
        score = score.max(BigDecimal.ZERO).min(fullScore).setScale(1, RoundingMode.HALF_UP);
        s.setScore(score);
        s.setComment("[大模型初评] " + result.getStr("comment", ""));
        return s;
    }

    /** 兜底引擎：按评分要点关键词覆盖率 + 答案完整度打分 */
    private AiScore keywordScore(Question question, String answer, BigDecimal fullScore) {
        AiScore s = new AiScore();
        String pointsText = StrUtil.blankToDefault(question.getScorePoints(), question.getAnswer());
        List<String> points = splitPoints(pointsText);
        String normalized = answer.toLowerCase();
        List<String> hit = new ArrayList<>();
        List<String> miss = new ArrayList<>();
        for (String point : points) {
            List<String> keywords = extractKeywords(point);
            boolean matched = keywords.stream().anyMatch(k -> normalized.contains(k.toLowerCase()));
            if (matched) hit.add(point); else miss.add(point);
        }
        BigDecimal ratio;
        if (points.isEmpty()) {
            ratio = answer.length() >= 30 ? new BigDecimal("0.6") : new BigDecimal("0.3");
        } else {
            ratio = new BigDecimal(hit.size()).divide(new BigDecimal(points.size()), 4, RoundingMode.HALF_UP);
            if (answer.length() < 20) ratio = ratio.multiply(new BigDecimal("0.8"));
        }
        BigDecimal score = fullScore.multiply(ratio).setScale(1, RoundingMode.HALF_UP);
        StringBuilder comment = new StringBuilder("[规则引擎初评] ");
        if (!points.isEmpty()) {
            comment.append("评分要点共 ").append(points.size()).append(" 项，命中 ").append(hit.size()).append(" 项。");
            if (!hit.isEmpty()) comment.append(" 命中：").append(String.join("；", hit)).append("。");
            if (!miss.isEmpty()) comment.append(" 缺失：").append(String.join("；", miss)).append("。");
        } else {
            comment.append("未配置评分要点，按答案完整度估分，建议人工重点复核。");
        }
        s.setScore(score);
        s.setComment(comment.toString());
        return s;
    }

    private List<String> splitPoints(String text) {
        List<String> points = new ArrayList<>();
        if (StrUtil.isBlank(text)) return points;
        for (String part : text.split("[；;\\n]")) {
            String p = part.replaceFirst("^\\d+[\\.、．]\\s*", "").trim();
            if (p.length() >= 2) points.add(p);
        }
        return points;
    }

    private List<String> extractKeywords(String point) {
        List<String> keywords = new ArrayList<>();
        String[] segs = point.split("[：:，,（）()、\\s]+");
        for (String seg : segs) {
            if (seg.length() >= 2 && keywords.size() < 4) keywords.add(seg);
        }
        if (keywords.isEmpty() && point.length() >= 2) keywords.add(point.substring(0, Math.min(4, point.length())));
        return keywords;
    }
}