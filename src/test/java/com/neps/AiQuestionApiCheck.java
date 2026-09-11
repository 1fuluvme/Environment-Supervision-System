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

public class AiQuestionApiCheck {

    private static final String BASE = "http://localhost:8080";
    private static final String PREFIX = "NEPS_AI_QUESTION_CHECK";
    private static final String DECISION_PHONE = "19900009101";
    private static final String PUBLIC_PHONE = "19900009102";
    private static final LocalDate START = LocalDate.of(2026, 8, 1);
    private static final LocalDate END = LocalDate.of(2026, 8, 10);
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        cleanup();

        try {
            TestData data = seed();

            post(client(), question(
                    "本市最近几天空气污染记录多少",
                    data.insideRegionId(), null), 401);

            HttpClient publicClient = client();
            login(publicClient, PUBLIC_PHONE, env("ADMIN_PASSWORD"));
            post(publicClient, question(
                    "本市最近几天空气污染记录多少",
                    data.insideRegionId(), null), 403);

            HttpClient admin = client();
            login(admin, env("ADMIN_PHONE"), env("ADMIN_PASSWORD"));

            JsonNode overview = post(
                    admin,
                    question(
                            "本市最近几天空气统计、异常、预测和溯源情况",
                            data.insideRegionId(),
                            null),
                    200);

            requireOverview(overview, data);

            JsonNode event = post(
                    admin,
                    question(
                            "这个事件有什么已知依据",
                            data.insideRegionId(),
                            data.insideEventId()),
                    200);

            requireAdminEvent(event, data.insideEventId());

            post(
                    admin,
                    question(
                            "这个事件有什么已知依据",
                            data.insideRegionId(),
                            null),
                    400);

            post(
                    admin,
                    question(
                            "替我派发工单",
                            data.insideRegionId(),
                            data.insideEventId()),
                    400);

            if (workOrderCount() != 0) {
                throw new IllegalStateException(
                        "只读问答不应创建工单");
            }

            post(
                    admin,
                    question(
                            "帮我写一首诗",
                            data.insideRegionId(),
                            null),
                    400);

            post(
                    admin,
                    question(
                            "查看空气统计",
                            data.outsideRegionId(),
                            null),
                    403);

            post(
                    admin,
                    question(
                            "这个事件有什么已知依据",
                            data.insideRegionId(),
                            data.outsideEventId()),
                    403);

            HttpClient decision = client();
            login(decision, DECISION_PHONE, env("ADMIN_PASSWORD"));

            JsonNode decisionEvent = post(
                    decision,
                    question(
                            "这个事件有什么已知依据",
                            data.insideRegionId(),
                            data.insideEventId()),
                    200);

            requireDecisionEvent(
                    decisionEvent,
                    data.insideEventId());

            post(
                    admin,
                    questionForRange(
                            "查看空气统计",
                            data.insideRegionId(),
                            LocalDate.of(1990, 1, 1),
                            LocalDate.of(1990, 1, 2),
                            null),
                    422);

            System.out.println("受限只读AI问答接口检查全部通过");
        } finally {
            cleanup();
        }
    }

    private static TestData seed() throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);

            try {
                long adminId = userId(
                        connection,
                        env("ADMIN_PHONE"));
                long publicId = roleUserId(
                        connection,
                        "PUBLIC");
                long workerId = roleUserId(
                        connection,
                        "GRID");
                long insideGridId =
                        envLong("TEST_INSIDE_GRID_ID");
                long outsideGridId =
                        envLong("TEST_OUTSIDE_GRID_ID");
                long insideRegionId = regionId(
                        connection,
                        insideGridId);
                long outsideRegionId = regionId(
                        connection,
                        outsideGridId);

                String passwordHash = passwordHash(
                        connection,
                        adminId);

                long decisionId = insertUser(
                        connection,
                        DECISION_PHONE,
                        "DECISION",
                        passwordHash);

                insertUser(
                        connection,
                        PUBLIC_PHONE,
                        "PUBLIC",
                        passwordHash);

                try (PreparedStatement statement =
                             connection.prepareStatement(
                                     "INSERT INTO sys_user_region "
                                             + "(user_id, region_id) VALUES (?, ?)")) {
                    statement.setLong(1, decisionId);
                    statement.setLong(2, insideRegionId);
                    statement.executeUpdate();
                }

                LocalDateTime eventTime =
                        LocalDateTime.of(2026, 8, 7, 12, 0);

                long insideEventId = insertEventChain(
                        connection,
                        insideGridId,
                        publicId,
                        workerId,
                        adminId,
                        eventTime,
                        "INSIDE");

                long outsideEventId = insertEventChain(
                        connection,
                        outsideGridId,
                        publicId,
                        workerId,
                        adminId,
                        eventTime,
                        "OUTSIDE");

                insertPrediction(
                        connection,
                        insideRegionId,
                        adminId);

                insertTrace(
                        connection,
                        insideEventId,
                        adminId,
                        eventTime);

                connection.commit();

                return new TestData(
                        insideRegionId,
                        outsideRegionId,
                        insideEventId,
                        outsideEventId);
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
            LocalDateTime eventTime,
            String suffix) throws Exception {

        long feedbackId = insert(
                connection,
                """
                        INSERT INTO biz_feedback (
                            submitter_id, grid_id, address, observed_at,
                            description, status, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, 'PROCESSING', ?, ?)
                        """,
                publicId,
                gridId,
                PREFIX + " ADDRESS " + suffix,
                eventTime,
                PREFIX + " " + suffix,
                eventTime,
                eventTime);

        long taskId = insert(
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
                eventTime,
                eventTime);

        long measurementId = insert(
                connection,
                """
                        INSERT INTO biz_measurement (
                            task_id, feedback_id, submitter_id, version_no,
                            measured_at, location, report_type, data_source,
                            statistically_valid, quality_flag,
                            aqi_calculable, aqi, aqi_level, aqi_category,
                            standard_version, rule_status, suggested_priority,
                            review_status, submitted_at, updated_at
                        ) VALUES (?, ?, ?, 1, ?, ?, 'REALTIME', ?,
                            1, 'VALID', 1, 180, 4, '中度污染',
                            'HJ 633-2026', 'SUSPECTED', 'HIGH',
                            'APPROVED', ?, ?)
                        """,
                taskId,
                feedbackId,
                workerId,
                eventTime,
                PREFIX,
                PREFIX,
                eventTime,
                eventTime);

        return insert(
                connection,
                """
                        INSERT INTO biz_anomaly_event (
                            feedback_id, task_id, measurement_id, grid_id,
                            status, suggested_priority, confirmed_priority,
                            trigger_reason, reviewed_by, reviewed_at,
                            review_reason, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, 'PROCESSING', 'HIGH', 'HIGH',
                            ?, ?, ?, ?, ?, ?)
                        """,
                feedbackId,
                taskId,
                measurementId,
                gridId,
                PREFIX + " TRIGGER " + suffix,
                adminId,
                eventTime,
                PREFIX + " REVIEW " + suffix,
                eventTime,
                eventTime);
    }

    private static void insertPrediction(
            Connection connection,
            long regionId,
            long adminId) throws Exception {

        insert(
                connection,
                """
                        INSERT INTO biz_aqi_prediction (
                            region_id, history_start_date, history_end_date,
                            target_date, method, sample_count, predicted_aqi,
                            actual_aqi, absolute_error, input_record_ids,
                            source_name, is_demo, generated_by, generated_at
                        ) VALUES (?, '2026-08-01', '2026-08-07',
                            '2026-08-08', 'MA7', 7, 160,
                            170, 10, '1,2,3,4,5,6,7', ?, 1, ?, ?)
                        """,
                regionId,
                PREFIX,
                adminId,
                LocalDateTime.of(2026, 8, 7, 13, 0));
    }

    private static void insertTrace(
            Connection connection,
            long eventId,
            long adminId,
            LocalDateTime eventTime) throws Exception {

        insert(
                connection,
                """
                        INSERT INTO biz_pollution_trace (
                            anomaly_event_id, event_observed_at,
                            window_start, window_end, time_window_hours,
                            max_distance_km, direction_tolerance_deg,
                            candidate_snapshot, status, method_version,
                            is_demo, generated_by, generated_at
                        ) VALUES (?, ?, ?, ?, 6, 20, 45,
                            '[]', 'SUCCEEDED', 'DEMO-TRACE-1', 1, ?, ?)
                        """,
                eventId,
                eventTime,
                eventTime.minusHours(6),
                eventTime.plusHours(6),
                adminId,
                eventTime.plusHours(1));
    }

    private static void requireOverview(
            JsonNode response,
            TestData data) {

        Set<String> sourceTypes = sourceTypes(response);

        if (response.path("regionId").asLong()
                != data.insideRegionId()
                || !"REALTIME".equals(
                response.path("reportType").asText())
                || !"MOCK".equals(
                response.path("provider").asText())
                || !response.path("demo").asBoolean()
                || response.path("answer").asText().isBlank()
                || response.path("monitoringFacts").isEmpty()
                || response.path("predictions").size() != 1
                || response.path("suggestions").isEmpty()
                || !sourceTypes.containsAll(Set.of(
                "AIR_QUALITY_STATISTICS",
                "ANOMALY_SUMMARY",
                "AQI_PREDICTION",
                "POLLUTION_TRACE"))) {

            throw new IllegalStateException(
                    "综合问答响应不正确：" + response);
        }

        requireSourceReferences(response);
    }

    private static void requireAdminEvent(
            JsonNode response,
            long eventId) {

        String facts = response.path("monitoringFacts").toString();
        String expectedSource = "ANOMALY_EVENT:" + eventId;

        if (!facts.contains(PREFIX + " TRIGGER INSIDE")
                || !facts.contains(expectedSource)
                || !hasSource(
                response,
                expectedSource,
                "授权异常事件业务依据")) {

            throw new IllegalStateException(
                    "管理员事件依据响应不正确：" + response);
        }
    }

    private static void requireDecisionEvent(
            JsonNode response,
            long eventId) {

        String facts = response.path("monitoringFacts").toString();

        if (!facts.contains("决策者仅展示事件状态摘要")
                || facts.contains(PREFIX + " TRIGGER INSIDE")
                || !hasSource(
                response,
                "ANOMALY_EVENT:" + eventId,
                "授权异常事件状态摘要")) {

            throw new IllegalStateException(
                    "决策者事件摘要响应不正确：" + response);
        }
    }

    private static void requireSourceReferences(
            JsonNode response) {

        Set<String> ids = new HashSet<>();
        response.path("sources")
                .forEach(source ->
                        ids.add(source.path("id").asText()));

        for (String field : new String[]{
                "monitoringFacts", "predictions"}) {
            for (JsonNode item : response.path(field)) {
                boolean matched = ids.stream()
                        .anyMatch(id ->
                                item.asText().contains(id));

                if (!matched) {
                    throw new IllegalStateException(
                            "回答内容使用了无法核对的来源：" + item);
                }
            }
        }
    }

    private static Set<String> sourceTypes(
            JsonNode response) {

        Set<String> result = new HashSet<>();
        response.path("sources")
                .forEach(source ->
                        result.add(
                                source.path("type").asText()));
        return result;
    }

    private static boolean hasSource(
            JsonNode response,
            String id,
            String description) {

        for (JsonNode source : response.path("sources")) {
            if (id.equals(source.path("id").asText())
                    && description.equals(
                    source.path("description").asText())) {
                return true;
            }
        }
        return false;
    }

    private static String question(
            String question,
            long regionId,
            Long eventId) throws Exception {

        return questionForRange(
                question,
                regionId,
                START,
                END,
                eventId);
    }

    private static String questionForRange(
            String question,
            long regionId,
            LocalDate start,
            LocalDate end,
            Long eventId) throws Exception {

        var body = JSON.createObjectNode();
        body.put("question", question);
        body.put("regionId", regionId);
        body.put("startDate", start.toString());
        body.put("endDate", end.toString());
        body.put("reportType", "REALTIME");

        if (eventId != null) {
            body.put("anomalyEventId", eventId);
        }

        return JSON.writeValueAsString(body);
    }

    private static JsonNode post(
            HttpClient client,
            String body,
            int expected) throws Exception {

        JsonNode csrf = csrf(client);

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(
                                BASE + "/api/ai/questions"))
                        .timeout(Duration.ofSeconds(20))
                        .header(
                                "Content-Type",
                                "application/json")
                        .header(
                                csrf.path("headerName").asText(),
                                csrf.path("token").asText())
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

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
                : JSON.readTree(response.body());
    }

    private static void login(
            HttpClient client,
            String phone,
            String password) throws Exception {

        JsonNode csrf = csrf(client);
        String form = "phone="
                + URLEncoder.encode(
                phone,
                StandardCharsets.UTF_8)
                + "&password="
                + URLEncoder.encode(
                password,
                StandardCharsets.UTF_8);

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(
                                BASE + "/api/auth/login"))
                        .header(
                                "Content-Type",
                                "application/x-www-form-urlencoded")
                        .header(
                                csrf.path("headerName").asText(),
                                csrf.path("token").asText())
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 204) {
            throw new IllegalStateException(
                    "登录失败：" + phone
                            + "，响应：" + response.body());
        }
    }

    private static JsonNode csrf(
            HttpClient client) throws Exception {

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(
                                BASE + "/api/auth/csrf"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "获取CSRF令牌失败");
        }

        return JSON.readTree(response.body());
    }

    private static HttpClient client() {
        CookieManager cookies = new CookieManager();
        cookies.setCookiePolicy(
                CookiePolicy.ACCEPT_ALL);

        return HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    private static long insertUser(
            Connection connection,
            String phone,
            String role,
            String passwordHash) throws Exception {

        return insert(
                connection,
                """
                        INSERT INTO sys_user (
                            phone, display_name, password_hash,
                            role, enabled, created_at
                        ) VALUES (?, ?, ?, ?, 1, ?)
                        """,
                phone,
                PREFIX + " " + role,
                passwordHash,
                role,
                LocalDateTime.now());
    }

    private static long insert(
            Connection connection,
            String sql,
            Object... values) throws Exception {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql,
                             Statement.RETURN_GENERATED_KEYS)) {

            for (int index = 0;
                 index < values.length;
                 index++) {

                Object value = values[index];

                if (value instanceof LocalDateTime dateTime) {
                    statement.setTimestamp(
                            index + 1,
                            Timestamp.valueOf(dateTime));
                } else {
                    statement.setObject(index + 1, value);
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
            String phone) throws Exception {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             "SELECT id FROM sys_user WHERE phone = ?")) {

            statement.setString(1, phone);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException(
                            "测试账号不存在：" + phone);
                }
                return result.getLong(1);
            }
        }
    }

    private static long roleUserId(
            Connection connection,
            String role) throws Exception {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             """
                                     SELECT id
                                     FROM sys_user
                                     WHERE role = ? AND enabled = 1
                                     ORDER BY id
                                     LIMIT 1
                                     """)) {

            statement.setString(1, role);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException(
                            "缺少启用的测试角色账号：" + role);
                }
                return result.getLong(1);
            }
        }
    }

    private static String passwordHash(
            Connection connection,
            long userId) throws Exception {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             "SELECT password_hash FROM sys_user WHERE id = ?")) {

            statement.setLong(1, userId);

            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private static long regionId(
            Connection connection,
            long gridId) throws Exception {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             "SELECT region_id FROM sys_grid WHERE id = ?")) {

            statement.setLong(1, gridId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException(
                            "测试网格不存在：" + gridId);
                }
                return result.getLong(1);
            }
        }
    }

    private static long workOrderCount() throws Exception {
        try (Connection connection = connection();
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

            statement.setString(1, PREFIX + "%");

            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void cleanup() throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("""
                    DELETE t
                    FROM biz_pollution_trace t
                    JOIN biz_anomaly_event e
                      ON e.id = t.anomaly_event_id
                    JOIN biz_feedback f
                      ON f.id = e.feedback_id
                    WHERE f.description LIKE 'NEPS_AI_QUESTION_CHECK%'
                    """);

            statement.executeUpdate(
                    "DELETE FROM biz_aqi_prediction "
                            + "WHERE source_name = '"
                            + PREFIX + "'");

            statement.executeUpdate("""
                    DELETE e
                    FROM biz_anomaly_event e
                    JOIN biz_feedback f ON f.id = e.feedback_id
                    WHERE f.description LIKE 'NEPS_AI_QUESTION_CHECK%'
                    """);

            statement.executeUpdate("""
                    DELETE m
                    FROM biz_measurement m
                    JOIN biz_feedback f ON f.id = m.feedback_id
                    WHERE f.description LIKE 'NEPS_AI_QUESTION_CHECK%'
                    """);

            statement.executeUpdate("""
                    DELETE t
                    FROM biz_inspection_task t
                    JOIN biz_feedback f ON f.id = t.feedback_id
                    WHERE f.description LIKE 'NEPS_AI_QUESTION_CHECK%'
                    """);

            statement.executeUpdate(
                    "DELETE FROM biz_feedback "
                            + "WHERE description LIKE '"
                            + PREFIX + "%'");

            statement.executeUpdate(
                    "DELETE FROM sys_user_region "
                            + "WHERE user_id IN (SELECT id FROM sys_user "
                            + "WHERE phone IN ('" + DECISION_PHONE
                            + "','" + PUBLIC_PHONE + "'))");

            statement.executeUpdate(
                    "DELETE FROM sys_user WHERE phone IN ('"
                            + DECISION_PHONE + "','"
                            + PUBLIC_PHONE + "')");
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/neps"
                        + "?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai",
                env("DB_USERNAME"),
                env("DB_PASSWORD"));
    }

    private static long envLong(String name) {
        return Long.parseLong(env(name));
    }

    private static String env(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "缺少环境变量：" + name);
        }

        return value;
    }

    private record TestData(
            long insideRegionId,
            long outsideRegionId,
            long insideEventId,
            long outsideEventId) {
    }
}
