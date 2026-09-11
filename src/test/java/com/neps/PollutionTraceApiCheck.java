package com.neps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PollutionTraceApiCheck {

    private static final String BASE = "http://localhost:8080";
    private static final String PREFIX = "NEPS_TRACE_CHECK";
    private static final String METHOD_VERSION = "DEMO-TRACE-1";
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        cleanup();

        try {
            TestData data = seed();

            get(client(), "/api/pollution-traces", 401);

            HttpClient admin = client();
            login(admin, env("ADMIN_PHONE"), env("ADMIN_PASSWORD"));

            JsonNode success = post(
                    admin,
                    request(data.fullEventId()),
                    201);

            long traceId = success.path("id").asLong();
            requireSuccessfulTrace(success, data);
            checkSuccessfulTraceDatabase(traceId, data);

            post(admin, request(data.fullEventId()), 409);

            JsonNode noCoordinates = post(
                    admin,
                    request(data.noCoordinatesEventId()),
                    201);
            requireInsufficient(
                    noCoordinates,
                    "经纬度");

            JsonNode noWeather = post(
                    admin,
                    request(data.noWeatherEventId()),
                    201);
            requireInsufficient(
                    noWeather,
                    "气象记录");

            post(
                    admin,
                    request(data.pendingEventId()),
                    409);

            post(
                    admin,
                    request(data.outsideEventId()),
                    403);

            JsonNode detail = getJson(
                    admin,
                    "/api/pollution-traces/" + traceId,
                    200);
            requireSuccessfulTrace(detail, data);

            JsonNode list = getJson(
                    admin,
                    "/api/pollution-traces?anomalyEventId="
                            + data.fullEventId(),
                    200);

            if (!containsId(list, traceId)) {
                throw new IllegalStateException(
                        "授权事件列表缺少已生成的溯源结果");
            }

            JsonNode hidden = getJson(
                    admin,
                    "/api/pollution-traces?anomalyEventId="
                            + data.outsideEventId(),
                    200);

            if (!hidden.isArray() || !hidden.isEmpty()) {
                throw new IllegalStateException(
                        "列表不应泄露越权事件的溯源结果");
            }

            get(
                    admin,
                    "/api/pollution-traces/"
                            + data.outsideTraceId(),
                    403);

            get(admin, "/api/pollution-traces/0", 400);

            System.out.println("疑似污染溯源接口检查全部通过");
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

                LocalDateTime eventTime =
                        LocalDateTime.of(2016, 8, 7, 12, 0);

                long fullEventId = insertEventChain(
                        connection,
                        insideGridId,
                        publicId,
                        workerId,
                        adminId,
                        new BigDecimal("117.230000"),
                        new BigDecimal("31.820000"),
                        eventTime,
                        "PROCESSING",
                        "FULL");

                long noCoordinatesEventId = insertEventChain(
                        connection,
                        insideGridId,
                        publicId,
                        workerId,
                        adminId,
                        null,
                        null,
                        eventTime.plusDays(1),
                        "PROCESSING",
                        "NO_COORDINATES");

                long noWeatherEventId = insertEventChain(
                        connection,
                        insideGridId,
                        publicId,
                        workerId,
                        adminId,
                        new BigDecimal("117.230000"),
                        new BigDecimal("31.820000"),
                        eventTime.plusMonths(1),
                        "PROCESSING",
                        "NO_WEATHER");

                long pendingEventId = insertEventChain(
                        connection,
                        insideGridId,
                        publicId,
                        workerId,
                        adminId,
                        new BigDecimal("117.230000"),
                        new BigDecimal("31.820000"),
                        eventTime.plusDays(2),
                        "PENDING_REVIEW",
                        "PENDING");

                long outsideEventId = insertEventChain(
                        connection,
                        outsideGridId,
                        publicId,
                        workerId,
                        adminId,
                        null,
                        null,
                        eventTime,
                        "PROCESSING",
                        "OUTSIDE");

                long weatherBatchId = insertBatch(
                        connection,
                        "WEATHER",
                        2,
                        adminId);
                long emissionBatchId = insertBatch(
                        connection,
                        "EMISSION",
                        5,
                        adminId);

                long weatherId = insertWeather(
                        connection,
                        weatherBatchId,
                        insideRegionId,
                        eventTime,
                        "VALID",
                        new BigDecimal("90"),
                        new BigDecimal("3.5"),
                        "WEATHER-VALID");

                insertWeather(
                        connection,
                        weatherBatchId,
                        insideRegionId,
                        eventTime.minusMinutes(1),
                        "INVALID",
                        new BigDecimal("270"),
                        new BigDecimal("8"),
                        "WEATHER-INVALID");

                List<Long> screenedIds = new ArrayList<>();

                long candidateEmissionId = insertEmission(
                        connection,
                        emissionBatchId,
                        insideRegionId,
                        eventTime,
                        new BigDecimal("117.240000"),
                        new BigDecimal("31.820000"),
                        "UPWIND");
                screenedIds.add(candidateEmissionId);

                screenedIds.add(insertEmission(
                        connection,
                        emissionBatchId,
                        insideRegionId,
                        eventTime,
                        new BigDecimal("117.220000"),
                        new BigDecimal("31.820000"),
                        "DOWNWIND"));

                screenedIds.add(insertEmission(
                        connection,
                        emissionBatchId,
                        insideRegionId,
                        eventTime,
                        new BigDecimal("117.500000"),
                        new BigDecimal("31.820000"),
                        "TOO_FAR"));

                insertEmission(
                        connection,
                        emissionBatchId,
                        insideRegionId,
                        eventTime.plusHours(7),
                        new BigDecimal("117.240000"),
                        new BigDecimal("31.820000"),
                        "OUTSIDE_TIME");

                insertInvalidEmission(
                        connection,
                        emissionBatchId,
                        insideRegionId,
                        eventTime,
                        "INVALID");

                long outsideTraceId = insertOutsideTrace(
                        connection,
                        outsideEventId,
                        adminId,
                        eventTime);

                connection.commit();

                return new TestData(
                        fullEventId,
                        noCoordinatesEventId,
                        noWeatherEventId,
                        pendingEventId,
                        outsideEventId,
                        outsideTraceId,
                        insideGridId,
                        insideRegionId,
                        weatherId,
                        candidateEmissionId,
                        List.copyOf(screenedIds));
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
            BigDecimal longitude,
            BigDecimal latitude,
            LocalDateTime observedAt,
            String eventStatus,
            String suffix) throws Exception {

        long feedbackId = insert(
                connection,
                """
                        INSERT INTO biz_feedback (
                            submitter_id, grid_id, address,
                            longitude, latitude, observed_at,
                            description, status, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, 'PROCESSING', ?, ?)
                        """,
                publicId,
                gridId,
                PREFIX + " ADDRESS " + suffix,
                longitude,
                latitude,
                observedAt,
                PREFIX + " " + suffix,
                LocalDateTime.now(),
                LocalDateTime.now());

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
                LocalDateTime.now(),
                LocalDateTime.now());

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
                observedAt,
                PREFIX,
                PREFIX,
                LocalDateTime.now(),
                LocalDateTime.now());

        return insert(
                connection,
                """
                        INSERT INTO biz_anomaly_event (
                            feedback_id, task_id, measurement_id, grid_id,
                            status, suggested_priority, confirmed_priority,
                            trigger_reason, reviewed_by, reviewed_at,
                            review_reason, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, 'HIGH', ?, ?, ?, ?, ?, ?, ?)
                        """,
                feedbackId,
                taskId,
                measurementId,
                gridId,
                eventStatus,
                "PENDING_REVIEW".equals(eventStatus) ? null : "HIGH",
                PREFIX,
                "PENDING_REVIEW".equals(eventStatus) ? null : adminId,
                "PENDING_REVIEW".equals(eventStatus) ? null : LocalDateTime.now(),
                "PENDING_REVIEW".equals(eventStatus) ? null : PREFIX,
                LocalDateTime.now(),
                LocalDateTime.now());
    }

    private static long insertBatch(
            Connection connection,
            String type,
            int recordCount,
            long adminId) throws Exception {

        return insert(
                connection,
                """
                        INSERT INTO biz_import_batch (
                            batch_no, data_type, original_name, file_hash,
                            record_count, source_name, is_demo,
                            imported_by, imported_at
                        ) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)
                        """,
                UUID.randomUUID().toString(),
                type,
                PREFIX + "-" + type + ".csv",
                UUID.randomUUID().toString().replace("-", "")
                        + UUID.randomUUID().toString().replace("-", ""),
                recordCount,
                PREFIX + "_" + type,
                adminId,
                LocalDateTime.now());
    }

    private static long insertWeather(
            Connection connection,
            long batchId,
            long regionId,
            LocalDateTime observedAt,
            String quality,
            BigDecimal direction,
            BigDecimal speed,
            String suffix) throws Exception {

        return insert(
                connection,
                """
                        INSERT INTO biz_external_weather (
                            import_batch_id, record_code, region_id,
                            observed_at, wind_direction, wind_speed,
                            quality_flag, source_name, is_demo, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                        """,
                batchId,
                PREFIX + "-" + suffix,
                regionId,
                observedAt,
                direction,
                speed,
                quality,
                PREFIX + "_WEATHER",
                LocalDateTime.now());
    }

    private static long insertEmission(
            Connection connection,
            long batchId,
            long regionId,
            LocalDateTime observedAt,
            BigDecimal longitude,
            BigDecimal latitude,
            String suffix) throws Exception {

        return insertEmission(
                connection,
                batchId,
                regionId,
                observedAt,
                longitude,
                latitude,
                suffix,
                "VALID");
    }

    private static void insertInvalidEmission(
            Connection connection,
            long batchId,
            long regionId,
            LocalDateTime observedAt,
            String suffix) throws Exception {

        insertEmission(
                connection,
                batchId,
                regionId,
                observedAt,
                new BigDecimal("117.240000"),
                new BigDecimal("31.820000"),
                suffix,
                "INVALID");
    }

    private static long insertEmission(
            Connection connection,
            long batchId,
            long regionId,
            LocalDateTime observedAt,
            BigDecimal longitude,
            BigDecimal latitude,
            String suffix,
            String quality) throws Exception {

        return insert(
                connection,
                """
                        INSERT INTO biz_external_emission (
                            import_batch_id, record_code, region_id,
                            enterprise_code, enterprise_name,
                            longitude, latitude, observed_at,
                            pollutant_code, emission_value, emission_unit,
                            quality_flag, source_name, is_demo, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?,
                            'SO2', 12.5000, 'kg/h', ?, ?, 1, ?)
                        """,
                batchId,
                PREFIX + "-EMISSION-" + suffix,
                regionId,
                PREFIX + "-" + suffix,
                suffix + "企业",
                longitude,
                latitude,
                observedAt,
                quality,
                PREFIX + "_EMISSION",
                LocalDateTime.now());
    }

    private static long insertOutsideTrace(
            Connection connection,
            long eventId,
            long adminId,
            LocalDateTime eventTime) throws Exception {

        return insert(
                connection,
                """
                        INSERT INTO biz_pollution_trace (
                            anomaly_event_id, event_observed_at,
                            window_start, window_end, time_window_hours,
                            max_distance_km, direction_tolerance_deg,
                            status, failure_reason, method_version,
                            is_demo, generated_by, generated_at
                        ) VALUES (?, ?, ?, ?, 6, 20, 45,
                            'INSUFFICIENT', ?, ?, 1, ?, ?)
                        """,
                eventId,
                eventTime,
                eventTime.minusHours(6),
                eventTime.plusHours(6),
                PREFIX,
                METHOD_VERSION,
                adminId,
                LocalDateTime.now());
    }

    private static void requireSuccessfulTrace(
            JsonNode response,
            TestData data) {

        Set<Long> emissionIds = new HashSet<>();
        response.path("emissionRecordIds")
                .forEach(node -> emissionIds.add(node.asLong()));

        JsonNode candidates = response.path("candidates");
        JsonNode candidate = candidates.path(0);

        if (response.path("id").asLong() <= 0
                || response.path("anomalyEventId").asLong()
                != data.fullEventId()
                || response.path("gridId").asLong()
                != data.insideGridId()
                || response.path("regionId").asLong()
                != data.insideRegionId()
                || !"SUCCEEDED".equals(
                response.path("status").asText())
                || !response.path("failureReason").isNull()
                || response.path("weatherRecordId").asLong()
                != data.weatherId()
                || response.path("windDirection").asDouble() != 90.0
                || response.path("timeWindowHours").asInt() != 6
                || response.path("maxDistanceKm").asDouble() != 20.0
                || response.path("directionToleranceDeg").asDouble() != 45.0
                || !METHOD_VERSION.equals(
                response.path("methodVersion").asText())
                || !response.path("demo").asBoolean()
                || !emissionIds.equals(
                new HashSet<>(data.screenedEmissionIds()))
                || !candidates.isArray()
                || candidates.size() != 1
                || candidate.path("emissionRecordId").asLong()
                != data.candidateEmissionId()
                || !candidate.path("enterpriseCode").asText()
                .endsWith("UPWIND")
                || candidate.path("distanceKm").asDouble() >= 2
                || Math.abs(
                candidate.path("bearingDeg").asDouble() - 90) >= 1
                || candidate.path("directionDifferenceDeg").asDouble() >= 1
                || candidate.path("evidence").asText().isBlank()) {

            throw new IllegalStateException(
                    "成功溯源响应不正确：" + response);
        }
    }

    private static void requireInsufficient(
            JsonNode response,
            String expectedReason) {

        if (response.path("id").asLong() <= 0
                || !"INSUFFICIENT".equals(
                response.path("status").asText())
                || !response.path("failureReason").asText()
                .contains(expectedReason)
                || !response.path("candidates").isArray()
                || !response.path("candidates").isEmpty()) {

            throw new IllegalStateException(
                    "资料不足响应不正确：" + response);
        }
    }

    private static void checkSuccessfulTraceDatabase(
            long traceId,
            TestData data) throws Exception {

        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT status, weather_record_id,
                                    emission_record_ids,
                                    JSON_VALID(candidate_snapshot) AS valid_json
                             FROM biz_pollution_trace
                             WHERE id = ?
                             """)) {

            statement.setLong(1, traceId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()
                        || !"SUCCEEDED".equals(
                        result.getString("status"))
                        || result.getLong("weather_record_id")
                        != data.weatherId()
                        || result.getInt("valid_json") != 1) {
                    throw new IllegalStateException(
                            "成功溯源数据库记录不正确");
                }

                Set<Long> storedIds = new HashSet<>();
                for (String id : result
                        .getString("emission_record_ids")
                        .split(",")) {
                    storedIds.add(Long.valueOf(id));
                }

                if (!storedIds.equals(
                        new HashSet<>(data.screenedEmissionIds()))) {
                    throw new IllegalStateException(
                            "排污输入记录追溯不正确");
                }
            }
        }
    }

    private static boolean containsId(
            JsonNode array,
            long id) {

        if (!array.isArray()) {
            return false;
        }

        for (JsonNode item : array) {
            if (item.path("id").asLong() == id) {
                return true;
            }
        }

        return false;
    }

    private static String request(long eventId) {
        return """
                {
                  "anomalyEventId": %d,
                  "demo": true
                }
                """.formatted(eventId);
    }

    private static JsonNode post(
            HttpClient client,
            String body,
            int expected) throws Exception {

        JsonNode csrf = csrf(client);

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(
                                BASE + "/api/pollution-traces"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .header(
                                csrf.path("headerName").asText(),
                                csrf.path("token").asText())
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        requireStatus(
                "POST /api/pollution-traces",
                response,
                expected);

        return response.body().isBlank()
                ? JSON.nullNode()
                : JSON.readTree(response.body());
    }

    private static JsonNode getJson(
            HttpClient client,
            String path,
            int expected) throws Exception {

        HttpResponse<String> response = get(
                client,
                path,
                expected);

        return JSON.readTree(response.body());
    }

    private static HttpResponse<String> get(
            HttpClient client,
            String path,
            int expected) throws Exception {

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + path))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        requireStatus("GET " + path, response, expected);
        return response;
    }

    private static void requireStatus(
            String operation,
            HttpResponse<String> response,
            int expected) {

        if (response.statusCode() != expected) {
            throw new IllegalStateException(
                    operation + " 预期 " + expected
                            + "，实际 " + response.statusCode()
                            + "，响应：" + response.body());
        }

        System.out.println("通过：" + operation + " → " + expected);
    }

    private static void login(
            HttpClient client,
            String phone,
            String password) throws Exception {

        JsonNode csrf = csrf(client);
        String form = "phone="
                + URLEncoder.encode(phone, StandardCharsets.UTF_8)
                + "&password="
                + URLEncoder.encode(password, StandardCharsets.UTF_8);

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/api/auth/login"))
                        .header(
                                "Content-Type",
                                "application/x-www-form-urlencoded")
                        .header(
                                csrf.path("headerName").asText(),
                                csrf.path("token").asText())
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 204) {
            throw new IllegalStateException(
                    "管理员登录失败：" + response.body());
        }
    }

    private static JsonNode csrf(
            HttpClient client) throws Exception {

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + "/api/auth/csrf"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("获取CSRF令牌失败");
        }

        return JSON.readTree(response.body());
    }

    private static HttpClient client() {
        CookieManager cookies = new CookieManager();
        cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        return HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    private static long insert(
            Connection connection,
            String sql,
            Object... values) throws Exception {

        try (PreparedStatement statement = connection.prepareStatement(
                sql,
                Statement.RETURN_GENERATED_KEYS)) {

            for (int index = 0; index < values.length; index++) {
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

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("测试数据ID生成失败");
                }
                return keys.getLong(1);
            }
        }
    }

    private static long userId(
            Connection connection,
            String phone) throws Exception {

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM sys_user WHERE phone = ?")) {

            statement.setString(1, phone);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("管理员账号不存在");
                }
                return result.getLong(1);
            }
        }
    }

    private static long roleUserId(
            Connection connection,
            String role) throws Exception {

        try (PreparedStatement statement = connection.prepareStatement(
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

    private static long regionId(
            Connection connection,
            long gridId) throws Exception {

        try (PreparedStatement statement = connection.prepareStatement(
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
                    WHERE f.description LIKE 'NEPS_TRACE_CHECK%'
                    """);

            statement.executeUpdate(
                    "DELETE FROM biz_external_weather "
                            + "WHERE source_name LIKE 'NEPS_TRACE_CHECK%'");
            statement.executeUpdate(
                    "DELETE FROM biz_external_emission "
                            + "WHERE source_name LIKE 'NEPS_TRACE_CHECK%'");
            statement.executeUpdate(
                    "DELETE FROM biz_import_batch "
                            + "WHERE source_name LIKE 'NEPS_TRACE_CHECK%'");

            statement.executeUpdate("""
                    DELETE e
                    FROM biz_anomaly_event e
                    JOIN biz_feedback f ON f.id = e.feedback_id
                    WHERE f.description LIKE 'NEPS_TRACE_CHECK%'
                    """);

            statement.executeUpdate("""
                    DELETE m
                    FROM biz_measurement m
                    JOIN biz_feedback f ON f.id = m.feedback_id
                    WHERE f.description LIKE 'NEPS_TRACE_CHECK%'
                    """);

            statement.executeUpdate("""
                    DELETE t
                    FROM biz_inspection_task t
                    JOIN biz_feedback f ON f.id = t.feedback_id
                    WHERE f.description LIKE 'NEPS_TRACE_CHECK%'
                    """);

            statement.executeUpdate(
                    "DELETE FROM biz_feedback "
                            + "WHERE description LIKE 'NEPS_TRACE_CHECK%'");
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
            long fullEventId,
            long noCoordinatesEventId,
            long noWeatherEventId,
            long pendingEventId,
            long outsideEventId,
            long outsideTraceId,
            long insideGridId,
            long insideRegionId,
            long weatherId,
            long candidateEmissionId,
            List<Long> screenedEmissionIds) {
    }
}
