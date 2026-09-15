package com.neps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AiAgentApiCheck {

    private static final String BASE =
            "http://localhost:8080";

    private static final String PREFIX =
            "NEPS_AI_AGENT_CHECK";

    private static final String CONVERSATION_ID =
            "agent-memory-check";

    private static final String MEMORY_TOKEN =
            "蓝鲸";

    private static final LocalDate START =
            LocalDate.of(2026, 8, 1);

    private static final LocalDate END =
            LocalDate.of(2026, 8, 10);

    private static final ObjectMapper JSON =
            new ObjectMapper();

    public static void main(String[] args)
            throws Exception {

        cleanup();

        try {
            TestData data = seed();

            HttpClient anonymous = client();

            post(
                    anonymous,
                    question(
                            "分析本区域的空气统计、异常、预测和污染溯源情况",
                            data.regionId(),
                            null),
                    401);

            deleteConversation(
                    anonymous,
                    data.regionId(),
                    401);

            HttpClient admin = client();

            login(
                    admin,
                    env("ADMIN_PHONE"),
                    env("ADMIN_PASSWORD"));

            JsonNode overview =
                    post(
                            admin,
                            question(
                                    "分析本区域的空气统计、异常、预测和污染溯源情况；"
                                            + "本次会话识别词是"
                                            + MEMORY_TOKEN,
                                    data.regionId(),
                                    null,
                                    CONVERSATION_ID),
                            200);

            requireOverview(
                    overview,
                    data.regionId());

            JsonNode memoryAnswer =
                    post(
                            admin,
                            question(
                                    "继续核对空气统计，并告诉我本次会话识别词是什么",
                                    data.regionId(),
                                    null,
                                    CONVERSATION_ID),
                            200);

            if (!memoryAnswer.path("answer")
                    .asText()
                    .contains(MEMORY_TOKEN)) {

                throw new IllegalStateException(
                        "Agent没有正确使用会话记忆："
                                + memoryAnswer);
            }

            String memoryKey =
                    conversationKey(
                            data.regionId());

            if (memoryCount(memoryKey) == 0) {
                throw new IllegalStateException(
                        "Agent会话记忆没有写入数据库");
            }

            deleteConversation(
                    admin,
                    data.regionId(),
                    204);

            if (memoryCount(memoryKey) != 0) {
                throw new IllegalStateException(
                        "Agent会话记忆清空失败");
            }

            post(
                    admin,
                    question(
                            "查询空气统计",
                            data.regionId(),
                            null,
                            "invalid conversation"),
                    400);

            System.out.println(
                    "Spring AI Agent多轮会话记忆检查通过");

            JsonNode event =
                    post(
                            admin,
                            question(
                                    "这个事件有哪些异常依据",
                                    data.regionId(),
                                    data.eventId()),
                            200);

            requireEvent(
                    event,
                    data.eventId());

            post(
                    admin,
                    question(
                            "修改这个事件并派发工单",
                            data.regionId(),
                            data.eventId()),
                    400);

            if (workOrderCount() != 0) {
                throw new IllegalStateException(
                        "Agent只读检查失败：产生了工单");
            }

            System.out.println(
                    "Spring AI只读Agent接口检查全部通过");

        } finally {
            cleanup();
        }
    }

    private static void requireOverview(
            JsonNode response,
            long regionId) {

        Set<String> sourceTypes =
                sourceTypes(response);

        Set<String> expectedTypes =
                Set.of(
                        "AIR_QUALITY_STATISTICS",
                        "ANOMALY_SUMMARY",
                        "AQI_PREDICTION",
                        "POLLUTION_TRACE");

        boolean invalid =
                response.path("regionId").asLong()
                        != regionId
                        || !"REALTIME".equals(
                        response.path("reportType")
                                .asText())
                        || !"QWEN".equals(
                        response.path("provider")
                                .asText())
                        || response.path("demo")
                        .asBoolean()
                        || response.path("answer")
                        .asText()
                        .isBlank()
                        || response.path(
                                "monitoringFacts")
                        .isEmpty()
                        || response.path(
                                "predictions")
                        .isEmpty()
                        || !sourceTypes.containsAll(
                        expectedTypes);

        if (invalid) {
            throw new IllegalStateException(
                    "Agent综合回答不正确："
                            + response);
        }

        String expectedModel =
                envOrDefault(
                        "AI_CHAT_MODEL",
                        "qwen-plus");

        if (!expectedModel.equals(
                response.path("modelName")
                        .asText())) {

            throw new IllegalStateException(
                    "Agent模型名称不正确："
                            + response);
        }

        requireSourceReferences(response);
    }

    private static void requireEvent(
            JsonNode response,
            long eventId) {

        String sourceId =
                "ANOMALY_EVENT:" + eventId;

        boolean invalid =
                response.path("answer")
                        .asText()
                        .isBlank()
                        || response.path(
                                "monitoringFacts")
                        .isEmpty()
                        || !hasSource(
                        response,
                        sourceId,
                        "ANOMALY_EVENT");

        if (invalid) {
            throw new IllegalStateException(
                    "Agent事件回答不正确："
                            + response);
        }

        requireSourceReferences(response);
    }

    private static void requireSourceReferences(
            JsonNode response) {

        Set<String> sourceIds =
                new HashSet<>();

        response.path("sources")
                .forEach(source ->
                        sourceIds.add(
                                source.path("id")
                                        .asText()));

        for (String field :
                new String[]{
                        "monitoringFacts",
                        "predictions"}) {

            for (JsonNode item :
                    response.path(field)) {

                boolean matched =
                        sourceIds.stream()
                                .anyMatch(id ->
                                        item.asText()
                                                .contains(id));

                if (!matched) {
                    throw new IllegalStateException(
                            "Agent回答包含无法核对的来源："
                                    + item);
                }
            }
        }
    }

    private static Set<String> sourceTypes(
            JsonNode response) {

        Set<String> result =
                new HashSet<>();

        response.path("sources")
                .forEach(source ->
                        result.add(
                                source.path("type")
                                        .asText()));

        return result;
    }

    private static boolean hasSource(
            JsonNode response,
            String id,
            String type) {

        for (JsonNode source :
                response.path("sources")) {

            if (id.equals(
                    source.path("id").asText())
                    && type.equals(
                    source.path("type")
                            .asText())) {

                return true;
            }
        }

        return false;
    }

    private static TestData seed()
            throws Exception {

        try (Connection connection =
                     connection()) {

            connection.setAutoCommit(false);

            try {
                long adminId =
                        userId(
                                connection,
                                env("ADMIN_PHONE"));

                long publicId =
                        roleUserId(
                                connection,
                                "PUBLIC");

                long workerId =
                        roleUserId(
                                connection,
                                "GRID");

                long gridId =
                        envLong(
                                "TEST_INSIDE_GRID_ID");

                long regionId =
                        regionId(
                                connection,
                                gridId);

                LocalDateTime observedAt =
                        LocalDateTime.of(
                                2026, 8, 7,
                                12, 0);

                long eventId =
                        insertEventChain(
                                connection,
                                gridId,
                                publicId,
                                workerId,
                                adminId,
                                observedAt);

                insertPrediction(
                        connection,
                        regionId,
                        adminId);

                insertTrace(
                        connection,
                        eventId,
                        adminId,
                        observedAt);

                connection.commit();

                return new TestData(
                        regionId,
                        eventId);

            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static long insertEventChain(
            Connection connection,
            long gridId,
            long publicId,
            long workerId,
            long adminId,
            LocalDateTime observedAt)
            throws Exception {

        long feedbackId =
                insert(
                        connection,
                        """
                        INSERT INTO biz_feedback (
                            submitter_id, grid_id, address,
                            observed_at, description, status,
                            created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, 'PROCESSING', ?, ?)
                        """,
                        publicId,
                        gridId,
                        PREFIX + " ADDRESS",
                        observedAt,
                        PREFIX + " FEEDBACK",
                        observedAt,
                        observedAt);

        long taskId =
                insert(
                        connection,
                        """
                        INSERT INTO biz_inspection_task (
                            feedback_id, assignee_id, assigned_by,
                            requirement, priority, status,
                            assigned_at, updated_at
                        ) VALUES (?, ?, ?, ?, 'HIGH', 'COMPLETED', ?, ?)
                        """,
                        feedbackId,
                        workerId,
                        adminId,
                        PREFIX,
                        observedAt,
                        observedAt);

        long measurementId =
                insert(
                        connection,
                        """
                        INSERT INTO biz_measurement (
                            task_id, feedback_id, submitter_id,
                            version_no, measured_at, location,
                            report_type, data_source,
                            statistically_valid, quality_flag,
                            aqi_calculable, aqi,
                            aqi_level, aqi_category,
                            standard_version, rule_status,
                            suggested_priority, review_status,
                            submitted_at, updated_at
                        ) VALUES (
                            ?, ?, ?, 1, ?, ?,
                            'REALTIME', ?, 1, 'VALID',
                            1, 180, 4, '中度污染',
                            'HJ 633-2026', 'SUSPECTED',
                            'HIGH', 'APPROVED', ?, ?
                        )
                        """,
                        taskId,
                        feedbackId,
                        workerId,
                        observedAt,
                        PREFIX + " LOCATION",
                        PREFIX,
                        observedAt,
                        observedAt);

        return insert(
                connection,
                """
                INSERT INTO biz_anomaly_event (
                    feedback_id, task_id, measurement_id,
                    grid_id, status,
                    suggested_priority, confirmed_priority,
                    trigger_reason, reviewed_by,
                    reviewed_at, review_reason,
                    created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, 'PROCESSING',
                    'HIGH', 'HIGH', ?, ?, ?, ?, ?, ?
                )
                """,
                feedbackId,
                taskId,
                measurementId,
                gridId,
                PREFIX + " TRIGGER",
                adminId,
                observedAt,
                PREFIX + " REVIEW",
                observedAt,
                observedAt);
    }

    private static void insertPrediction(
            Connection connection,
            long regionId,
            long adminId)
            throws Exception {

        insert(
                connection,
                """
                INSERT INTO biz_aqi_prediction (
                    region_id,
                    history_start_date,
                    history_end_date,
                    target_date,
                    method,
                    sample_count,
                    predicted_aqi,
                    actual_aqi,
                    absolute_error,
                    input_record_ids,
                    source_name,
                    is_demo,
                    generated_by,
                    generated_at
                ) VALUES (
                    ?,
                    '2026-08-01',
                    '2026-08-07',
                    '2026-08-08',
                    'MA7',
                    7,
                    160,
                    170,
                    10,
                    '1,2,3,4,5,6,7',
                    ?,
                    1,
                    ?,
                    ?
                )
                """,
                regionId,
                PREFIX,
                adminId,
                LocalDateTime.of(
                        2026, 8, 7,
                        13, 0));
    }

    private static void insertTrace(
            Connection connection,
            long eventId,
            long adminId,
            LocalDateTime observedAt)
            throws Exception {

        insert(
                connection,
                """
                INSERT INTO biz_pollution_trace (
                    anomaly_event_id,
                    event_observed_at,
                    window_start,
                    window_end,
                    time_window_hours,
                    max_distance_km,
                    direction_tolerance_deg,
                    candidate_snapshot,
                    status,
                    method_version,
                    is_demo,
                    generated_by,
                    generated_at
                ) VALUES (
                    ?, ?, ?, ?,
                    6, 20, 45,
                    '[]',
                    'SUCCEEDED',
                    'DEMO-TRACE-1',
                    1,
                    ?,
                    ?
                )
                """,
                eventId,
                observedAt,
                observedAt.minusHours(6),
                observedAt.plusHours(6),
                adminId,
                observedAt.plusHours(1));
    }

    private static String question(
            String text,
            long regionId,
            Long eventId)
            throws Exception {

        return question(
                text,
                regionId,
                eventId,
                null);
    }

    private static String question(
            String text,
            long regionId,
            Long eventId,
            String conversationId)
            throws Exception {

        var body =
                JSON.createObjectNode();

        body.put("question", text);
        body.put("regionId", regionId);
        body.put(
                "startDate",
                START.toString());
        body.put(
                "endDate",
                END.toString());
        body.put(
                "reportType",
                "REALTIME");

        if (eventId != null) {
            body.put(
                    "anomalyEventId",
                    eventId);
        }

        if (conversationId != null) {
            body.put(
                    "conversationId",
                    conversationId);
        }

        return JSON.writeValueAsString(body);
    }

    private static JsonNode post(
            HttpClient client,
            String body,
            int expected)
            throws Exception {

        JsonNode csrf = csrf(client);

        HttpResponse<String> response =
                client.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(
                                        BASE
                                                + "/api/ai/questions"))
                                .timeout(
                                        Duration.ofSeconds(
                                                180))
                                .header(
                                        "Content-Type",
                                        "application/json")
                                .header(
                                        csrf.path(
                                                        "headerName")
                                                .asText(),
                                        csrf.path("token")
                                                .asText())
                                .POST(
                                        HttpRequest.BodyPublishers
                                                .ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers
                                .ofString());

        if (response.statusCode() != expected) {
            throw new IllegalStateException(
                    "POST /api/ai/questions 预期 "
                            + expected
                            + "，实际 "
                            + response.statusCode()
                            + "，响应："
                            + response.body());
        }

        System.out.println(
                "通过：POST /api/ai/questions → "
                        + expected);

        return response.body().isBlank()
                ? JSON.nullNode()
                : JSON.readTree(
                response.body());
    }

    private static void deleteConversation(
            HttpClient client,
            long regionId,
            int expectedStatus)
            throws Exception {

        JsonNode csrf = csrf(client);

        String query =
                "?regionId=" + regionId
                        + "&startDate=" + START
                        + "&endDate=" + END
                        + "&reportType=REALTIME";

        HttpResponse<String> response =
                client.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(
                                        BASE
                                                + "/api/ai/conversations/"
                                                + CONVERSATION_ID
                                                + query))
                                .header(
                                        csrf.path("headerName")
                                                .asText(),
                                        csrf.path("token")
                                                .asText())
                                .DELETE()
                                .build(),
                        HttpResponse.BodyHandlers
                                .ofString());

        if (response.statusCode() != expectedStatus) {
            throw new IllegalStateException(
                    "DELETE /api/ai/conversations/"
                            + CONVERSATION_ID
                            + " 预期 "
                            + expectedStatus
                            + "，实际 "
                            + response.statusCode()
                            + "，响应："
                            + response.body());
        }

        System.out.println(
                "通过：DELETE /api/ai/conversations/"
                        + CONVERSATION_ID
                        + " → "
                        + expectedStatus);
    }

    private static String conversationKey(
            long regionId) {

        String raw =
                env("ADMIN_PHONE")
                        + ":"
                        + CONVERSATION_ID
                        + ":"
                        + regionId
                        + ":"
                        + START
                        + ":"
                        + END
                        + ":REALTIME";

        return UUID.nameUUIDFromBytes(
                        raw.getBytes(
                                StandardCharsets.UTF_8))
                .toString();
    }

    private static long memoryCount(
            String conversationKey)
            throws Exception {

        try (Connection connection =
                     connection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             "SELECT COUNT(*) "
                                     + "FROM SPRING_AI_CHAT_MEMORY "
                                     + "WHERE conversation_id = ?")) {

            statement.setString(
                    1,
                    conversationKey);

            try (ResultSet result =
                         statement.executeQuery()) {

                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void login(
            HttpClient client,
            String phone,
            String password)
            throws Exception {

        JsonNode csrf = csrf(client);

        String form =
                "phone="
                        + URLEncoder.encode(
                        phone,
                        StandardCharsets.UTF_8)
                        + "&password="
                        + URLEncoder.encode(
                        password,
                        StandardCharsets.UTF_8);

        HttpResponse<String> response =
                client.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(
                                        BASE
                                                + "/api/auth/login"))
                                .header(
                                        "Content-Type",
                                        "application/x-www-form-urlencoded")
                                .header(
                                        csrf.path(
                                                        "headerName")
                                                .asText(),
                                        csrf.path("token")
                                                .asText())
                                .POST(
                                        HttpRequest.BodyPublishers
                                                .ofString(form))
                                .build(),
                        HttpResponse.BodyHandlers
                                .ofString());

        if (response.statusCode() != 204) {
            throw new IllegalStateException(
                    "登录失败："
                            + response.body());
        }
    }

    private static JsonNode csrf(
            HttpClient client)
            throws Exception {

        HttpResponse<String> response =
                client.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(
                                        BASE
                                                + "/api/auth/csrf"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers
                                .ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "获取CSRF令牌失败");
        }

        return JSON.readTree(
                response.body());
    }

    private static HttpClient client() {
        CookieManager cookies =
                new CookieManager();

        cookies.setCookiePolicy(
                CookiePolicy.ACCEPT_ALL);

        return HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(
                        Duration.ofSeconds(5))
                .build();
    }

    private static long insert(
            Connection connection,
            String sql,
            Object... values)
            throws Exception {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql,
                             Statement.RETURN_GENERATED_KEYS)) {

            for (int index = 0;
                 index < values.length;
                 index++) {

                Object value = values[index];

                if (value
                        instanceof LocalDateTime dateTime) {

                    statement.setTimestamp(
                            index + 1,
                            Timestamp.valueOf(dateTime));

                } else {
                    statement.setObject(
                            index + 1,
                            value);
                }
            }

            statement.executeUpdate();

            try (ResultSet keys =
                         statement.getGeneratedKeys()) {

                if (!keys.next()) {
                    throw new IllegalStateException(
                            "测试数据ID生成失败");
                }

                return keys.getLong(1);
            }
        }
    }

    private static long userId(
            Connection connection,
            String phone)
            throws Exception {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             """
                             SELECT id
                             FROM sys_user
                             WHERE phone = ?
                             """)) {

            statement.setString(1, phone);

            try (ResultSet result =
                         statement.executeQuery()) {

                if (!result.next()) {
                    throw new IllegalStateException(
                            "测试账号不存在："
                                    + phone);
                }

                return result.getLong(1);
            }
        }
    }

    private static long roleUserId(
            Connection connection,
            String role)
            throws Exception {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             """
                             SELECT id
                             FROM sys_user
                             WHERE role = ?
                               AND enabled = 1
                             ORDER BY id
                             LIMIT 1
                             """)) {

            statement.setString(1, role);

            try (ResultSet result =
                         statement.executeQuery()) {

                if (!result.next()) {
                    throw new IllegalStateException(
                            "缺少启用的测试角色账号："
                                    + role);
                }

                return result.getLong(1);
            }
        }
    }

    private static long regionId(
            Connection connection,
            long gridId)
            throws Exception {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             """
                             SELECT region_id
                             FROM sys_grid
                             WHERE id = ?
                             """)) {

            statement.setLong(1, gridId);

            try (ResultSet result =
                         statement.executeQuery()) {

                if (!result.next()) {
                    throw new IllegalStateException(
                            "测试网格不存在："
                                    + gridId);
                }

                return result.getLong(1);
            }
        }
    }

    private static long workOrderCount()
            throws Exception {

        try (Connection connection =
                     connection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             """
                             SELECT COUNT(*)
                             FROM biz_work_order w
                             JOIN biz_anomaly_event e
                               ON e.id = w.anomaly_event_id
                             JOIN biz_feedback f
                               ON f.id = e.feedback_id
                             WHERE f.description LIKE ?
                             """)) {

            statement.setString(
                    1,
                    PREFIX + "%");

            try (ResultSet result =
                         statement.executeQuery()) {

                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void cleanup()
            throws Exception {

        try (Connection connection =
                     connection();
             Statement statement =
                     connection.createStatement()) {

            statement.executeUpdate("""
                    DELETE t
                    FROM biz_pollution_trace t
                    JOIN biz_anomaly_event e
                      ON e.id = t.anomaly_event_id
                    JOIN biz_feedback f
                      ON f.id = e.feedback_id
                    WHERE f.description LIKE
                          'NEPS_AI_AGENT_CHECK%'
                    """);

            statement.executeUpdate(
                    "DELETE FROM biz_aqi_prediction "
                            + "WHERE source_name = '"
                            + PREFIX + "'");

            statement.executeUpdate("""
                    DELETE w
                    FROM biz_work_order w
                    JOIN biz_anomaly_event e
                      ON e.id = w.anomaly_event_id
                    JOIN biz_feedback f
                      ON f.id = e.feedback_id
                    WHERE f.description LIKE
                          'NEPS_AI_AGENT_CHECK%'
                    """);

            statement.executeUpdate("""
                    DELETE e
                    FROM biz_anomaly_event e
                    JOIN biz_feedback f
                      ON f.id = e.feedback_id
                    WHERE f.description LIKE
                          'NEPS_AI_AGENT_CHECK%'
                    """);

            statement.executeUpdate("""
                    DELETE m
                    FROM biz_measurement m
                    JOIN biz_feedback f
                      ON f.id = m.feedback_id
                    WHERE f.description LIKE
                          'NEPS_AI_AGENT_CHECK%'
                    """);

            statement.executeUpdate("""
                    DELETE t
                    FROM biz_inspection_task t
                    JOIN biz_feedback f
                      ON f.id = t.feedback_id
                    WHERE f.description LIKE
                          'NEPS_AI_AGENT_CHECK%'
                    """);

            statement.executeUpdate(
                    "DELETE FROM biz_feedback "
                            + "WHERE description LIKE '"
                            + PREFIX + "%'");
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

    private record TestData(
            long regionId,
            long eventId) {
    }
}
