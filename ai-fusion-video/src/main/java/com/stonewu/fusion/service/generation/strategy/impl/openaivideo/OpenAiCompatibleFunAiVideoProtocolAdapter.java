package com.stonewu.fusion.service.generation.strategy.impl.openaivideo;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

/**
 * FunAI 统一异步视频协议适配器。
 */
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleFunAiVideoProtocolAdapter implements OpenAiCompatibleVideoProtocolAdapter {

    private final OpenAiCompatibleVideoProtocolSupport support;

    @Override
    public String getProtocol() {
        return "funai";
    }

    @Override
    public String resolveSubmitUrl(OpenAiCompatibleVideoProtocolContext context) {
        return support.resolveFunAiVideosUrl(context.apiConfig());
    }

    @Override
    public RequestBody buildSubmitBody(OpenAiCompatibleVideoProtocolContext context) {
        return support.buildFunAiSubmitBody(context);
    }

    @Override
    public OpenAiCompatibleVideoTaskResult parseSubmitResponse(OpenAiCompatibleVideoProtocolContext context,
                                                               String responseBody) {
        return parseResult(responseBody, "FunAI 视频任务提交响应不是合法 JSON");
    }

    @Override
    public String resolveQueryUrl(OpenAiCompatibleVideoProtocolContext context, String trackingId) {
        return support.resolveFunAiVideosUrl(context.apiConfig()) + "/" + trackingId;
    }

    @Override
    public OpenAiCompatibleVideoTaskResult parseQueryResponse(OpenAiCompatibleVideoProtocolContext context,
                                                              String responseBody) {
        return parseResult(responseBody, "FunAI 视频任务查询响应不是合法 JSON");
    }

    @Override
    public String resolveVideoContentUrl(OpenAiCompatibleVideoProtocolContext context,
                                         String trackingId,
                                         OpenAiCompatibleVideoTaskResult result) {
        return support.resolveFunAiContentUrl(context.apiConfig(), trackingId, result.contentUrl());
    }

    private OpenAiCompatibleVideoTaskResult parseResult(String responseBody, String invalidMessage) {
        JsonNode root = support.readJson(responseBody, invalidMessage);
        JsonNode node = unwrapTaskNode(root);

        String trackingId = firstNonBlank(
                support.firstText(node, "id", "task_id", "taskId", "video_id", "videoId"),
                support.firstText(root, "id", "task_id", "taskId", "video_id", "videoId"));
        String status = normalizeFunAiStatus(firstNonBlank(
                support.firstText(node, "status", "state"),
                support.firstText(root, "status", "state")));
        Integer duration = support.parsePositiveSeconds(firstNonBlank(
                support.firstText(node, "seconds", "duration"),
                support.firstText(root, "seconds", "duration")));
        String videoUrl = firstNonBlank(
                support.firstText(node, "url", "video_url", "videoUrl", "output_url", "outputUrl"),
                support.firstText(root, "url", "video_url", "videoUrl", "output_url", "outputUrl"));
        String contentUrl = firstNonBlank(
                support.firstText(node, "content_url", "contentUrl"),
                support.firstText(root, "content_url", "contentUrl"));
        String coverUrl = firstNonBlank(
                support.firstText(node, "cover_url", "coverUrl", "thumbnail_url", "thumbnailUrl"),
                support.firstText(root, "cover_url", "coverUrl", "thumbnail_url", "thumbnailUrl"));
        String errorMessage = firstNonBlank(
                support.extractErrorMessage(node),
                support.extractErrorMessage(root));

        return new OpenAiCompatibleVideoTaskResult(
                trackingId, status, duration, videoUrl, contentUrl, coverUrl, errorMessage);
    }

    private JsonNode unwrapTaskNode(JsonNode root) {
        JsonNode data = root.path("data");
        if (data.isArray() && !data.isEmpty()) {
            return data.get(0);
        }
        if (data.isObject()) {
            return data;
        }
        JsonNode result = root.path("result");
        return result.isObject() ? result : root;
    }

    private String normalizeFunAiStatus(String status) {
        String normalized = support.normalizeStatus(status).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "success", "succeeded", "complete", "completed" -> "completed";
            case "failure", "failed", "error" -> "failed";
            case "not_start", "not_started", "pending", "queued" -> "queued";
            case "in_progress", "running", "processing" -> "processing";
            default -> normalized;
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
