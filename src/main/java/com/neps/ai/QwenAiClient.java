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

        JsonNode result = sendJson(requestBody);

        validateResult(result);

        return objectMapper.writeValueAsString(result);
    }

    public String answerQuestion(
            String contextSnapshot) throws Exception {

        validateConfiguration();

        ObjectNode requestBody =
                objectMapper.createObjectNode();

        requestBody.put("model", model);
        requestBody.put("enable_thinking", false);
        requestBody.put("max_completion_tokens", 700);

        requestBody.putObject("response_format")
                .put("type", "json_object");

        ArrayNode messages =
                requestBody.putArray("messages");

        messages.addObject()
                .put("role", "system")
                .put(
                        "content",
                        """
                        你是环保公众监督系统的只读问答助手。
                        只能使用用户消息中提供的数据回答，
                        不得编造数据、来源或结论，
                        不得执行SQL、修改数据、派发任务或改变业务状态。
                        监测事实、预测和建议必须分开。
                        当前没有提供预测数据时，predictions必须为空数组。
                        每项事实应标明数据中的sourceId。
                        请只输出JSON，必须包含：
                        answer字符串、
                        monitoringFacts字符串数组、
                        predictions字符串数组、
                        suggestions字符串数组。
                        """);

        messages.addObject()
                .put("role", "user")
                .put(
                        "content",
                        "请根据以下授权数据回答问题："
                                + contextSnapshot);

        JsonNode result = sendJson(requestBody);
        validateQuestionResult(result);

        return objectMapper.writeValueAsString(result);
    }

    private JsonNode sendJson(
            ObjectNode requestBody) throws Exception {

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

            throw new IllegalStateException(
                    "AI服务返回HTTP "
                            + response.statusCode()
                            + "："
                            + safeErrorDetail(response.body()));
        }

        JsonNode contentNode = objectMapper
                .readTree(response.body())
                .path("choices")
                .path(0)
                .path("message")
                .path("content");

        if (!contentNode.isTextual()
                || contentNode.asText().isBlank()) {

            throw new IllegalStateException(
                    "AI服务没有返回有效内容");
        }

        try {
            return objectMapper.readTree(
                    contentNode.asText());
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "AI服务返回的内容不是合法JSON");
        }
    }

    private void validateQuestionResult(
            JsonNode result) {

        boolean invalid =
                !result.isObject()
                        || result.path("answer")
                        .asText().isBlank()
                        || !result.path("monitoringFacts")
                        .isArray()
                        || !result.path("predictions")
                        .isArray()
                        || !result.path("suggestions")
                        .isArray();

        if (invalid) {
            throw new IllegalStateException(
                    "AI问答结果缺少必要字段");
        }
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

    private String safeErrorDetail(String responseBody) {
        try {
            JsonNode body =
                    objectMapper.readTree(responseBody);

            JsonNode error = body.path("error");

            String code =
                    error.path("code").asText();

            String message =
                    error.path("message").asText();

            String detail =
                    (code.isBlank() ? "" : code + "，")
                            + (message.isBlank()
                            ? "未返回错误说明"
                            : message);

            /*
             * 双重保护：即使服务意外回显密钥，
             * 也不能让它进入数据库和控制台。
             */
            if (!apiKey.isBlank()) {
                detail = detail.replace(apiKey, "***");
            }

            return detail.length() <= 300
                    ? detail
                    : detail.substring(0, 300);
        } catch (Exception exception) {
            return "未返回可解析的错误说明";
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