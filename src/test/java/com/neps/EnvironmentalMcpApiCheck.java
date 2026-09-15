package com.neps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public class EnvironmentalMcpApiCheck {

    private static final String ENDPOINT =
            "http://localhost:8080/mcp";

    private static final String PROTOCOL_VERSION =
            "2025-11-25";

    private static final LocalDate START =
            LocalDate.of(2026, 8, 1);

    private static final LocalDate END =
            LocalDate.of(2026, 8, 10);

    private static final Set<String> EXPECTED_TOOLS =
            Set.of(
                    "queryAirQualityStatistics",
                    "queryAnomalyEvidence",
                    "queryAqiPredictions",
                    "queryPollutionTraces");

    private static final ObjectMapper JSON =
            new ObjectMapper();

    private static final HttpClient HTTP =
            HttpClient.newBuilder()
                    .connectTimeout(
                            Duration.ofSeconds(5))
                    .build();

    public static void main(String[] args)
            throws Exception {

        boolean enabled =
                Boolean.parseBoolean(
                        envOrDefault(
                                "MCP_ENABLED",
                                "false"));

        if (!enabled) {
            send(
                    null,
                    initializeRequest(1),
                    404);

            System.out.println(
                    "MCP默认关闭检查通过");
            return;
        }

        String apiKey = env("MCP_API_KEY");

        if (apiKey.length() < 32) {
            send(
                    apiKey,
                    initializeRequest(1),
                    503);

            System.out.println(
                    "MCP弱密钥配置检查通过");
            return;
        }

        send(
                null,
                initializeRequest(1),
                401);

        send(
                apiKey + "-wrong",
                initializeRequest(2),
                401);

        JsonNode initialized =
                send(
                        apiKey,
                        initializeRequest(3),
                        200);

        requireInitialized(initialized);

        notifyInitialized(apiKey);

        JsonNode toolList =
                send(
                        apiKey,
                        request(
                                4,
                                "tools/list",
                                JSON.createObjectNode()),
                        200);

        requireToolList(toolList);

        long regionId =
                regionId(
                        envLong(
                                "TEST_INSIDE_GRID_ID"));

        DatabaseState before =
                databaseState();

        ObjectNode common =
                commonArguments(regionId);

        JsonNode statistics =
                callTool(
                        apiKey,
                        10,
                        "queryAirQualityStatistics",
                        common.deepCopy());

        JsonNode statisticsResult =
                requireToolResult(
                        statistics,
                        "queryAirQualityStatistics");

        if (statisticsResult.path("data")
                .path("regionId")
                .asLong() != regionId) {

            throw new IllegalStateException(
                    "MCP统计工具返回了错误区域："
                            + statisticsResult);
        }

        ObjectNode anomalyArguments =
                common.deepCopy();

        JsonNode anomaly =
                callTool(
                        apiKey,
                        11,
                        "queryAnomalyEvidence",
                        anomalyArguments);

        requireToolResult(
                anomaly,
                "queryAnomalyEvidence");

        JsonNode predictions =
                callTool(
                        apiKey,
                        12,
                        "queryAqiPredictions",
                        common.deepCopy());

        requireToolResult(
                predictions,
                "queryAqiPredictions");

        JsonNode traces =
                callTool(
                        apiKey,
                        13,
                        "queryPollutionTraces",
                        common.deepCopy());

        requireToolResult(
                traces,
                "queryPollutionTraces");

        DatabaseState after =
                databaseState();

        if (!before.equals(after)) {
            throw new IllegalStateException(
                    "MCP只读检查失败，业务表记录数发生变化。"
                            + "调用前："
                            + before
                            + "，调用后："
                            + after);
        }

        System.out.println(
                "环保只读MCP接口检查全部通过");
    }

    private static ObjectNode initializeRequest(
            int id) {

        ObjectNode params =
                JSON.createObjectNode();

        params.put(
                "protocolVersion",
                PROTOCOL_VERSION);

        params.set(
                "capabilities",
                JSON.createObjectNode());

        params.putObject("clientInfo")
                .put(
                        "name",
                        "neps-mcp-api-check")
                .put(
                        "version",
                        "1.0.0");

        return request(
                id,
                "initialize",
                params);
    }

    private static void requireInitialized(
            JsonNode response) {

        JsonNode result =
                requireResult(
                        response,
                        "initialize");

        if (!PROTOCOL_VERSION.equals(
                result.path("protocolVersion")
                        .asText())) {

            throw new IllegalStateException(
                    "MCP协议版本不正确："
                            + response);
        }

        if (!"neps-environmental-tools".equals(
                result.path("serverInfo")
                        .path("name")
                        .asText())) {

            throw new IllegalStateException(
                    "MCP服务名称不正确："
                            + response);
        }

        if (!result.path("capabilities")
                .has("tools")) {

            throw new IllegalStateException(
                    "MCP没有声明工具能力："
                            + response);
        }
    }

    private static void notifyInitialized(
            String apiKey)
            throws Exception {

        ObjectNode notification =
                JSON.createObjectNode();

        notification.put(
                "jsonrpc",
                "2.0");

        notification.put(
                "method",
                "notifications/initialized");

        notification.set(
                "params",
                JSON.createObjectNode());

        send(
                apiKey,
                notification,
                202);
    }

    private static void requireToolList(
            JsonNode response) {

        JsonNode result =
                requireResult(
                        response,
                        "tools/list");

        Set<String> actual =
                new HashSet<>();

        result.path("tools")
                .forEach(tool ->
                        actual.add(
                                tool.path("name")
                                        .asText()));

        if (!EXPECTED_TOOLS.equals(actual)) {
            throw new IllegalStateException(
                    "MCP工具清单不正确，预期："
                            + EXPECTED_TOOLS
                            + "，实际："
                            + actual);
        }

        result.path("tools")
                .forEach(tool -> {
                    if (tool.path("description")
                            .asText()
                            .isBlank()
                            || !tool.has(
                            "inputSchema")
                            || !tool.path("annotations")
                            .path("readOnlyHint")
                            .asBoolean()
                            || tool.path("annotations")
                            .path("destructiveHint")
                            .asBoolean(true)
                            || !tool.path("annotations")
                            .path("idempotentHint")
                            .asBoolean()
                            || tool.path("annotations")
                            .path("openWorldHint")
                            .asBoolean(true)) {

                        throw new IllegalStateException(
                                "MCP工具缺少描述、参数结构或只读声明："
                                        + tool);
                    }
                });
    }

    private static JsonNode callTool(
            String apiKey,
            int id,
            String toolName,
            ObjectNode arguments)
            throws Exception {

        ObjectNode params =
                JSON.createObjectNode();

        params.put(
                "name",
                toolName);

        params.set(
                "arguments",
                arguments);

        return send(
                apiKey,
                request(
                        id,
                        "tools/call",
                        params),
                200);
    }

    private static JsonNode requireToolResult(
            JsonNode response,
            String toolName)
            throws Exception {

        JsonNode result =
                requireResult(
                        response,
                        toolName);

        if (result.path("isError")
                .asBoolean(false)) {

            throw new IllegalStateException(
                    "MCP工具执行失败："
                            + toolName
                            + "，响应："
                            + response);
        }

        String text = null;

        for (JsonNode content :
                result.path("content")) {

            if ("text".equals(
                    content.path("type")
                            .asText())) {

                text = content.path("text")
                        .asText();
                break;
            }
        }

        if (text == null || text.isBlank()) {
            throw new IllegalStateException(
                    "MCP工具没有返回文本结果："
                            + toolName
                            + "，响应："
                            + response);
        }

        JsonNode toolResult =
                JSON.readTree(text);

        if (!toolResult.has("sources")
                || !toolResult.has("data")) {

            throw new IllegalStateException(
                    "MCP工具结果结构不正确："
                            + toolName
                            + "，结果："
                            + toolResult);
        }

        System.out.println(
                "通过：MCP工具 "
                        + toolName);

        return toolResult;
    }

    private static JsonNode requireResult(
            JsonNode response,
            String operation) {

        if (response.has("error")) {
            throw new IllegalStateException(
                    "MCP操作失败："
                            + operation
                            + "，响应："
                            + response);
        }

        JsonNode result =
                response.path("result");

        if (result.isMissingNode()
                || result.isNull()) {

            throw new IllegalStateException(
                    "MCP操作没有返回result："
                            + operation
                            + "，响应："
                            + response);
        }

        return result;
    }

    private static ObjectNode commonArguments(
            long regionId) {

        ObjectNode arguments =
                JSON.createObjectNode();

        arguments.put(
                "regionId",
                regionId);

        arguments.put(
                "startDate",
                START.toString());

        arguments.put(
                "endDate",
                END.toString());

        arguments.put(
                "reportType",
                "REALTIME");

        return arguments;
    }

    private static ObjectNode request(
            int id,
            String method,
            JsonNode params) {

        ObjectNode request =
                JSON.createObjectNode();

        request.put(
                "jsonrpc",
                "2.0");

        request.put(
                "id",
                id);

        request.put(
                "method",
                method);

        request.set(
                "params",
                params);

        return request;
    }

    private static JsonNode send(
            String apiKey,
            JsonNode body,
            int expectedStatus)
            throws Exception {

        HttpRequest.Builder builder =
                HttpRequest.newBuilder()
                        .uri(URI.create(ENDPOINT))
                        .timeout(
                                Duration.ofSeconds(30))
                        .header(
                                "Content-Type",
                                "application/json")
                        .header(
                                "Accept",
                                "application/json, text/event-stream")
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(
                                                JSON.writeValueAsString(
                                                        body)));

        if (apiKey != null) {
            builder.header(
                    "X-MCP-API-Key",
                    apiKey);
        }

        HttpResponse<String> response =
                HTTP.send(
                        builder.build(),
                        HttpResponse.BodyHandlers
                                .ofString());

        if (response.statusCode()
                != expectedStatus) {

            throw new IllegalStateException(
                    "POST /mcp 预期 "
                            + expectedStatus
                            + "，实际 "
                            + response.statusCode()
                            + "，响应："
                            + response.body());
        }

        System.out.println(
                "通过：POST /mcp → "
                        + expectedStatus);

        if (response.body().isBlank()) {
            return JSON.nullNode();
        }

        return JSON.readTree(
                response.body());
    }

    private static long regionId(
            long gridId)
            throws Exception {

        try (Connection connection =
                     connection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             """
                             SELECT region_id
                             FROM sys_grid
                             WHERE id = ?
                               AND enabled = 1
                             """)) {

            statement.setLong(
                    1,
                    gridId);

            try (ResultSet result =
                         statement.executeQuery()) {

                if (!result.next()) {
                    throw new IllegalStateException(
                            "测试网格不存在或未启用："
                                    + gridId);
                }

                return result.getLong(1);
            }
        }
    }

    private static DatabaseState databaseState()
            throws Exception {

        try (Connection connection =
                     connection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             """
                             SELECT
                               (SELECT COUNT(*)
                                  FROM biz_feedback),
                               (SELECT COUNT(*)
                                  FROM biz_inspection_task),
                               (SELECT COUNT(*)
                                  FROM biz_measurement),
                               (SELECT COUNT(*)
                                  FROM biz_anomaly_event),
                               (SELECT COUNT(*)
                                  FROM biz_work_order),
                               (SELECT COUNT(*)
                                  FROM biz_aqi_prediction),
                               (SELECT COUNT(*)
                                  FROM biz_pollution_trace)
                             """);
             ResultSet result =
                     statement.executeQuery()) {

            result.next();

            return new DatabaseState(
                    result.getLong(1),
                    result.getLong(2),
                    result.getLong(3),
                    result.getLong(4),
                    result.getLong(5),
                    result.getLong(6),
                    result.getLong(7));
        }
    }

    private static Connection connection()
            throws Exception {

        return DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/neps"
                        + "?characterEncoding=UTF-8"
                        + "&serverTimezone=Asia/Shanghai",
                env("DB_USERNAME"),
                env("DB_PASSWORD"));
    }

    private static long envLong(
            String name) {

        return Long.parseLong(
                env(name));
    }

    private static String env(
            String name) {

        String value =
                System.getenv(name);

        if (value == null
                || value.isBlank()) {

            throw new IllegalStateException(
                    "缺少环境变量："
                            + name);
        }

        return value;
    }

    private static String envOrDefault(
            String name,
            String defaultValue) {

        String value =
                System.getenv(name);

        return value == null
                || value.isBlank()
                ? defaultValue
                : value;
    }

    private record DatabaseState(
            long feedbackCount,
            long taskCount,
            long measurementCount,
            long anomalyCount,
            long workOrderCount,
            long predictionCount,
            long traceCount) {
    }
}
