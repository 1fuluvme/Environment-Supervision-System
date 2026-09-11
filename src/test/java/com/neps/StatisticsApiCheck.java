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
import java.util.HashMap;
import java.util.Map;

public class StatisticsApiCheck {

    private static final String BASE = "http://localhost:8080";
    private static final String PREFIX = "NEPS_STATISTICS_CHECK";
    private static final LocalDate START = LocalDate.of(2001, 1, 1);
    private static final LocalDate END = LocalDate.of(2001, 1, 3);
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        cleanup();

        try {
            TestData data = seed();

            get(client(), dailyPath(data.regionId()), 401);

            HttpClient admin = client();
            login(admin, env("ADMIN_PHONE"), env("ADMIN_PASSWORD"));

            JsonNode daily = getJson(
                    admin,
                    dailyPath(data.regionId()),
                    200);
            requireDaily(daily, data);

            JsonNode realtime = getJson(
                    admin,
                    statisticsPath(data.regionId(), "REALTIME"),
                    200);
            requireRealtime(realtime, data);

            get(
                    admin,
                    statisticsPath(data.outsideRegionId(), "DAILY"),
                    403);

            get(
                    admin,
                    statisticsPath(data.regionId(), "INSTANT"),
                    400);

            get(
                    admin,
                    "/api/statistics?regionId=" + data.regionId()
                            + "&startDate=2001-01-03"
                            + "&endDate=2001-01-01"
                            + "&reportType=DAILY",
                    400);

            get(
                    admin,
                    "/api/statistics?regionId=" + data.regionId()
                            + "&startDate=" + LocalDate.now()
                            + "&endDate=" + LocalDate.now().plusDays(1)
                            + "&reportType=DAILY",
                    400);

            get(
                    admin,
                    "/api/statistics?regionId=" + data.regionId(),
                    400);

            System.out.println("共用空气质量统计接口检查全部通过");
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
                long publicId = roleUserId(connection, "PUBLIC");
                long workerId = roleUserId(connection, "GRID");
                long insideGridId = envLong("TEST_INSIDE_GRID_ID");
                long childGridId = envLong("TEST_CHILD_GRID_ID");
                long outsideGridId = envLong("TEST_OUTSIDE_GRID_ID");
                long regionId = regionId(connection, insideGridId);
                long outsideRegionId = regionId(connection, outsideGridId);

                if (insideGridId == childGridId
                        || !gridInRegionTree(
                        connection,
                        regionId,
                        childGridId)) {
                    throw new IllegalStateException(
                            "TEST_CHILD_GRID_ID必须是统计区域内的另一个网格");
                }

                int configuredGridCount = configuredGridCount(
                        connection,
                        regionId);

                if (configuredGridCount < 2) {
                    throw new IllegalStateException(
                            "统计测试区域至少需要两个启用网格");
                }

                insertMeasurementChain(
                        connection, insideGridId, publicId, workerId, adminId,
                        LocalDateTime.of(2001, 1, 1, 0, 0),
                        "DAILY", 50, 1, 1, "VALID", "APPROVED", "D1");

                insertMeasurementChain(
                        connection, insideGridId, publicId, workerId, adminId,
                        LocalDateTime.of(2001, 1, 2, 0, 0),
                        "DAILY", 100, 2, 1, "VALID", "APPROVED", "D2-A");

                insertMeasurementChain(
                        connection, childGridId, publicId, workerId, adminId,
                        LocalDateTime.of(2001, 1, 2, 0, 0),
                        "DAILY", 150, 3, 1, "VALID", "APPROVED", "D2-B");

                insertMeasurementChain(
                        connection, childGridId, publicId, workerId, adminId,
                        LocalDateTime.of(2001, 1, 3, 0, 0),
                        "DAILY", 200, 4, 1, "INVALID", "APPROVED", "INVALID");

                insertMeasurementChain(
                        connection, insideGridId, publicId, workerId, adminId,
                        LocalDateTime.of(2001, 1, 3, 1, 0),
                        "DAILY", 250, 5, 1, "VALID", "PENDING", "PENDING");

                insertMeasurementChain(
                        connection, insideGridId, publicId, workerId, adminId,
                        LocalDateTime.of(2001, 1, 3, 2, 0),
                        "DAILY", 300, 5, 0, "VALID", "APPROVED", "STAT_INVALID");

                insertMeasurementChain(
                        connection, insideGridId, publicId, workerId, adminId,
                        LocalDateTime.of(2001, 1, 1, 10, 5),
                        "REALTIME", 80, 2, 1, "VALID", "APPROVED", "R10-A");

                insertMeasurementChain(
                        connection, childGridId, publicId, workerId, adminId,
                        LocalDateTime.of(2001, 1, 1, 10, 45),
                        "REALTIME", 100, 2, 1, "VALID", "APPROVED", "R10-B");

                insertMeasurementChain(
                        connection, childGridId, publicId, workerId, adminId,
                        LocalDateTime.of(2001, 1, 1, 11, 10),
                        "REALTIME", 120, 3, 1, "VALID", "APPROVED", "R11");

                connection.commit();

                return new TestData(
                        regionId,
                        outsideRegionId,
                        configuredGridCount);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void insertMeasurementChain(
            Connection connection,
            long gridId,
            long publicId,
            long workerId,
            long adminId,
            LocalDateTime measuredAt,
            String reportType,
            int aqi,
            int aqiLevel,
            int statisticallyValid,
            String qualityFlag,
            String reviewStatus,
            String suffix) throws Exception {

        long feedbackId = insert(
                connection,
                """
                        INSERT INTO biz_feedback (
                            submitter_id, grid_id, address, observed_at,
                            description, status, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, 'PENDING_REVIEW', ?, ?)
                        """,
                publicId,
                gridId,
                PREFIX,
                measuredAt,
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
                        ) VALUES (?, ?, ?, ?, 'MEDIUM', 'PENDING_REVIEW', ?, ?)
                        """,
                feedbackId,
                workerId,
                adminId,
                PREFIX,
                LocalDateTime.now(),
                LocalDateTime.now());

        insert(
                connection,
                """
                        INSERT INTO biz_measurement (
                            task_id, feedback_id, submitter_id, version_no,
                            measured_at, location, report_type, data_source,
                            statistically_valid, quality_flag,
                            aqi_calculable, aqi, aqi_level, aqi_category,
                            review_status, submitted_at, updated_at
                        ) VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?,
                            1, ?, ?, ?, ?, ?, ?)
                        """,
                taskId,
                feedbackId,
                workerId,
                measuredAt,
                PREFIX,
                reportType,
                PREFIX,
                statisticallyValid,
                qualityFlag,
                aqi,
                aqiLevel,
                category(aqiLevel),
                reviewStatus,
                LocalDateTime.now(),
                LocalDateTime.now());
    }

    private static void requireDaily(
            JsonNode response,
            TestData data) {

        Map<Integer, Long> levels = levelCounts(response);
        Map<String, JsonNode> trend = trendPoints(response);
        double expectedCoverage = Math.round(
                2 * 10000.0 / data.configuredGridCount()) / 100.0;

        if (response.path("regionId").asLong() != data.regionId()
                || !"2001-01-01".equals(response.path("startDate").asText())
                || !"2001-01-03".equals(response.path("endDate").asText())
                || !"DAILY".equals(response.path("reportType").asText())
                || !"AQI".equals(response.path("unit").asText())
                || response.path("measurementCount").asLong() != 6
                || response.path("validAqiCount").asLong() != 3
                || response.path("goodCount").asLong() != 2
                || response.path("pollutedCount").asLong() != 1
                || response.path("configuredGridCount").asInt()
                != data.configuredGridCount()
                || response.path("coveredGridCount").asInt() != 2
                || Math.abs(
                response.path("coverageRatePercent").asDouble()
                        - expectedCoverage) > 0.001
                || levels.size() != 6
                || levels.getOrDefault(1, -1L) != 1
                || levels.getOrDefault(2, -1L) != 1
                || levels.getOrDefault(3, -1L) != 1
                || levels.getOrDefault(4, -1L) != 0
                || levels.getOrDefault(5, -1L) != 0
                || levels.getOrDefault(6, -1L) != 0
                || trend.size() != 2
                || !trendPoint(trend, "2001-01-01", 50, 1)
                || !trendPoint(trend, "2001-01-02", 125, 2)
                || response.path("generatedAt").asText().isBlank()) {

            throw new IllegalStateException(
                    "DAILY统计响应不正确：" + response);
        }
    }

    private static void requireRealtime(
            JsonNode response,
            TestData data) {

        Map<Integer, Long> levels = levelCounts(response);
        Map<String, JsonNode> trend = trendPoints(response);

        if (response.path("regionId").asLong() != data.regionId()
                || !"REALTIME".equals(response.path("reportType").asText())
                || response.path("measurementCount").asLong() != 3
                || response.path("validAqiCount").asLong() != 3
                || response.path("goodCount").asLong() != 2
                || response.path("pollutedCount").asLong() != 1
                || response.path("coveredGridCount").asInt() != 2
                || levels.getOrDefault(2, -1L) != 2
                || levels.getOrDefault(3, -1L) != 1
                || trend.size() != 2
                || !trendPoint(trend, "2001-01-01 10:00", 90, 2)
                || !trendPoint(trend, "2001-01-01 11:00", 120, 1)) {

            throw new IllegalStateException(
                    "REALTIME统计响应不正确：" + response);
        }
    }

    private static Map<Integer, Long> levelCounts(JsonNode response) {
        Map<Integer, Long> result = new HashMap<>();

        for (JsonNode item : response.path("levelDistribution")) {
            result.put(
                    item.path("level").asInt(),
                    item.path("count").asLong());
        }

        return result;
    }

    private static Map<String, JsonNode> trendPoints(JsonNode response) {
        Map<String, JsonNode> result = new HashMap<>();

        for (JsonNode item : response.path("trend")) {
            result.put(item.path("period").asText(), item);
        }

        return result;
    }

    private static boolean trendPoint(
            Map<String, JsonNode> trend,
            String period,
            double average,
            long count) {

        JsonNode point = trend.get(period);

        return point != null
                && Math.abs(
                point.path("averageAqi").asDouble() - average) < 0.001
                && point.path("validRecordCount").asLong() == count;
    }

    private static String category(int level) {
        return switch (level) {
            case 1 -> "优";
            case 2 -> "良";
            case 3 -> "轻度污染";
            case 4 -> "中度污染";
            case 5 -> "重度污染";
            case 6 -> "严重污染";
            default -> throw new IllegalArgumentException("AQI级别不正确");
        };
    }

    private static String dailyPath(long regionId) {
        return statisticsPath(regionId, "DAILY");
    }

    private static String statisticsPath(
            long regionId,
            String reportType) {

        return "/api/statistics?regionId=" + regionId
                + "&startDate=" + START
                + "&endDate=" + END
                + "&reportType=" + reportType;
    }

    private static JsonNode getJson(
            HttpClient client,
            String path,
            int expected) throws Exception {

        HttpResponse<String> response = get(client, path, expected);
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

        if (response.statusCode() != expected) {
            throw new IllegalStateException(
                    "GET " + path + " 预期 " + expected
                            + "，实际 " + response.statusCode()
                            + "，响应：" + response.body());
        }

        System.out.println("通过：GET " + path + " → " + expected);
        return response;
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

    private static JsonNode csrf(HttpClient client) throws Exception {
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

    private static boolean gridInRegionTree(
            Connection connection,
            long regionId,
            long gridId) throws Exception {

        try (PreparedStatement statement = connection.prepareStatement("""
                WITH RECURSIVE region_tree AS (
                    SELECT id
                    FROM sys_region
                    WHERE id = ? AND enabled = 1

                    UNION ALL

                    SELECT r.id
                    FROM sys_region r
                    JOIN region_tree parent ON r.parent_id = parent.id
                    WHERE r.enabled = 1
                )
                SELECT COUNT(*)
                FROM sys_grid
                WHERE id = ?
                  AND enabled = 1
                  AND region_id IN (SELECT id FROM region_tree)
                """)) {

            statement.setLong(1, regionId);
            statement.setLong(2, gridId);

            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1) == 1;
            }
        }
    }

    private static int configuredGridCount(
            Connection connection,
            long regionId) throws Exception {

        try (PreparedStatement statement = connection.prepareStatement("""
                WITH RECURSIVE region_tree AS (
                    SELECT id
                    FROM sys_region
                    WHERE id = ? AND enabled = 1

                    UNION ALL

                    SELECT r.id
                    FROM sys_region r
                    JOIN region_tree parent ON r.parent_id = parent.id
                    WHERE r.enabled = 1
                )
                SELECT COUNT(*)
                FROM sys_grid
                WHERE enabled = 1
                  AND region_id IN (SELECT id FROM region_tree)
                """)) {

            statement.setLong(1, regionId);

            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static void cleanup() throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("""
                    DELETE m
                    FROM biz_measurement m
                    JOIN biz_feedback f ON f.id = m.feedback_id
                    WHERE f.description LIKE 'NEPS_STATISTICS_CHECK%'
                    """);

            statement.executeUpdate("""
                    DELETE t
                    FROM biz_inspection_task t
                    JOIN biz_feedback f ON f.id = t.feedback_id
                    WHERE f.description LIKE 'NEPS_STATISTICS_CHECK%'
                    """);

            statement.executeUpdate(
                    "DELETE FROM biz_feedback "
                            + "WHERE description LIKE 'NEPS_STATISTICS_CHECK%'");
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
            long regionId,
            long outsideRegionId,
            int configuredGridCount) {
    }
}
