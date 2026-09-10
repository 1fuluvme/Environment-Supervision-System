package com.neps.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neps.entity.Attachment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Component
public class QwenAiClient {

    private static final int MAX_IMAGES = 3;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Path uploadRoot;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;

    public QwenAiClient(
            ObjectMapper objectMapper,

            @Value("${app.upload.directory}")
            String uploadDirectory,

            @Value("${app.ai.base-url:}")
            String baseUrl,

            @Value("${app.ai.api-key:}")
            String apiKey,

            @Value("${app.ai.model:qwen3-vl-flash}")
            String model,

            @Value("${app.ai.timeout-seconds:60}")
            int timeoutSeconds) {

        this.objectMapper = objectMapper;
        this.uploadRoot = Path.of(uploadDirectory)
                .toAbsolutePath()
                .normalize();
        this.baseUrl = baseUrl == null
                ? ""
                : baseUrl.trim();
        this.apiKey = apiKey == null
                ? ""
                : apiKey.trim();
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String analyze(
            String inputSnapshot,
            List<Attachment> attachments) throws Exception {

        validateConfiguration();

        ObjectNode requestBody =
                objectMapper.createObjectNode();

        requestBody.put("model", model);
        requestBody.put("enable_thinking", false);
        requestBody.put("max_completion_tokens", 500);

        requestBody.putObject("response_format")
                .put("type", "json_object");

        ArrayNode messages =
                requestBody.putArray("messages");

        messages.addObject()
                .put("role", "system")
                .put(
                        "content",
                        """
                        你是环保公众监督系统的AI初判助手。
                        你只能根据提供的文字、检测数据和图片给出辅助线索。
                        不得根据图片生成实测污染物浓度或真实AQI。
                        不得把推测描述为已经确认的事实。
                        请输出JSON，并且必须包含：
                        summary字符串、
                        suspectedPhenomenon字符串、
                        checkItems字符串数组、
                        safetyNotice字符串。
                        """);

        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");

        ArrayNode content =
                userMessage.putArray("content");

        content.addObject()
                .put("type", "text")
                .put(
                        "text",
                        "请对以下环保业务记录进行初判："
                                + inputSnapshot);

        addImages(content, attachments);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(buildEndpoint())
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(
                                requestBody)))
                .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            // 不输出请求头，避免API Key进入控制台。
            throw new IllegalStateException(
                    "AI服务返回HTTP "
                            + response.statusCode());
        }

        JsonNode responseBody =
                objectMapper.readTree(response.body());

        JsonNode contentNode = responseBody
                .path("choices")
                .path(0)
                .path("message")
                .path("content");

        if (!contentNode.isTextual()
                || contentNode.asText().isBlank()) {

            throw new IllegalStateException(
                    "AI服务没有返回有效内容");
        }

        JsonNode result;

        try {
            result = objectMapper.readTree(
                    contentNode.asText());
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "AI服务返回的内容不是合法JSON");
        }

        validateResult(result);

        return objectMapper.writeValueAsString(result);
    }

    public String modelName() {
        return model;
    }

    private void addImages(
            ArrayNode content,
            List<Attachment> attachments) throws Exception {

        if (attachments == null || attachments.isEmpty()) {
            return;
        }

        int imageCount = Math.min(
                attachments.size(),
                MAX_IMAGES);

        // ponytail: 单次最多分析3张图，确有多图需求时再提高限制。
        for (int i = 0; i < imageCount; i++) {
            Attachment attachment = attachments.get(i);

            Path imagePath = uploadRoot
                    .resolve(attachment.getStoragePath())
                    .normalize();

            if (!imagePath.startsWith(uploadRoot)) {
                throw new IllegalStateException(
                        "AI图片路径超出上传目录");
            }

            if (!Files.isRegularFile(imagePath)) {
                throw new IllegalStateException(
                        "AI分析图片不存在");
            }

            byte[] imageBytes =
                    Files.readAllBytes(imagePath);

            String dataUrl = "data:"
                    + attachment.getContentType()
                    + ";base64,"
                    + Base64.getEncoder()
                    .encodeToString(imageBytes);

            content.addObject()
                    .put("type", "image_url")
                    .putObject("image_url")
                    .put("url", dataUrl);
        }
    }

    private URI buildEndpoint() {
        String normalizedBaseUrl =
                baseUrl.endsWith("/")
                        ? baseUrl.substring(
                        0, baseUrl.length() - 1)
                        : baseUrl;

        URI endpoint = URI.create(
                normalizedBaseUrl
                        + "/chat/completions");

        if (!"https".equalsIgnoreCase(
                endpoint.getScheme())) {

            throw new IllegalStateException(
                    "AI服务地址必须使用HTTPS");
        }

        return endpoint;
    }

    private void validateConfiguration() {
        if (baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "没有配置AI_BASE_URL");
        }

        if (apiKey.isBlank()) {
            throw new IllegalStateException(
                    "没有配置AI_API_KEY");
        }

        if (model == null || model.isBlank()) {
            throw new IllegalStateException(
                    "没有配置AI_MODEL");
        }

        if (timeoutSeconds < 1
                || timeoutSeconds > 180) {

            throw new IllegalStateException(
                    "AI超时时间必须在1至180秒之间");
        }
    }

    private void validateResult(JsonNode result) {
        boolean invalid =
                !result.isObject()
                        || result.path("summary")
                        .asText().isBlank()
                        || result.path(
                                "suspectedPhenomenon")
                        .asText().isBlank()
                        || !result.path("checkItems")
                        .isArray()
                        || result.path("checkItems")
                        .isEmpty()
                        || result.path("safetyNotice")
                        .asText().isBlank();

        if (invalid) {
            throw new IllegalStateException(
                    "AI返回结果缺少必要字段");
        }
    }
}