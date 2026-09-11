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

public class FrontendContractApiCheck {

    private static final String BASE = "http://localhost:8080";
    private static final String DECISION_PHONE = "19900009201";
    private static final LocalDate START = LocalDate.of(2026, 8, 1);
    private static final LocalDate END = LocalDate.of(2026, 8, 10);
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        cleanup();

        try {
            TestData data = seed();

            get(client(), "/api/admin/regions", 401);

            HttpClient admin = client();
            login(admin, env("ADMIN_PHONE"), env("ADMIN_PASSWORD"));

            JsonNode adminRegions = getJson(
                    admin,
                    "/api/admin/regions",
                    200);

            requireRegion(
                    adminRegions,
                    data.insideRegionId());

            HttpClient decision = client();
            login(
                    decision,
                    DECISION_PHONE,
                    env("ADMIN_PASSWORD"));

            get(decision, "/api/admin/regions", 403);

            JsonNode dashboard = getJson(
                    decision,
                    dashboardPath(data.insideRegionId()),
                    200);

            requireDashboard(
                    dashboard,
                    data.insideRegionId());

            get(
                    decision,
                    dashboardPath(data.outsideRegionId()),
                    403);

            post(
                    decision,
                    "/api/aqi-predictions",
                    """
                            {
                              "regionId": %d,
                              "targetDate": "%s",
                              "sourceName": "FRONTEND_CONTRACT_CHECK",
                              "demo": true
                            }
                            """.formatted(
                            data.insideRegionId(),
                            LocalDate.now()),
                    403);

            post(
                    decision,
                    "/api/pollution-traces",
                    """
                            {
                              "anomalyEventId": 1,
                              "demo": true
                            }
                            """,
                    403);

            System.out.println("前端所需后端契约检查全部通过");
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
                long insideRegionId = regionId(
                        connection,
                        envLong("TEST_INSIDE_GRID_ID"));
                long outsideRegionId = regionId(
                        connection,
                        envLong("TEST_OUTSIDE_GRID_ID"));

                long decisionId = insertUser(
                        connection,
                        passwordHash(connection, adminId));

                try (PreparedStatement statement =
                             connection.prepareStatement(
                                     "INSERT INTO sys_user_region "
                                             + "(user_id, region_id) VALUES (?, ?)")) {
                    statement.setLong(1, decisionId);
                    statement.setLong(2, insideRegionId);
                    statement.executeUpdate();
                }

                connection.commit();
                return new TestData(
                        insideRegionId,
                        outsideRegionId);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static long insertUser(
            Connection connection,
            String passwordHash) throws Exception {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             """
                                     INSERT INTO sys_user (
                                         phone, display_name, password_hash,
                                         role, enabled, created_at
                                     ) VALUES (?, '前端契约决策者', ?,
                                         'DECISION', 1, ?)
                                     """,
                             Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, DECISION_PHONE);
            statement.setString(2, passwordHash);
            statement.setTimestamp(
                    3,
                    Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException(
                            "决策者测试账号创建失败");
                }
                return keys.getLong(1);
            }
        }
    }

    private static void requireRegion(
            JsonNode regions,
            long regionId) {

        if (!regions.isArray()) {
            throw new IllegalStateException(
                    "管理员区域响应不是数组：" + regions);
        }

        for (JsonNode region : regions) {
            if (region.path("id").asLong() == regionId
                    && !region.path("name").asText().isBlank()) {
                return;
            }
        }

        throw new IllegalStateException(
                "管理员区域列表缺少测试区域：" + regions);
    }

    private static void requireDashboard(
            JsonNode response,
            long regionId) {

        if (response.path("regionId").asLong() != regionId
                || !START.toString().equals(
                response.path("startDate").asText())
                || !END.toString().equals(
                response.path("endDate").asText())
                || response.path("regionName").asText().isBlank()
                || !response.path("confirmedEventCount").canConvertToLong()
                || !response.path("openWarningCount").canConvertToLong()
                || !response.path("warningLevelDistribution").isArray()
                || response.path("warningLevelDistribution").size() != 3
                || !response.path("workOrderCount").canConvertToLong()
                || !response.path("closedWorkOrderCount").canConvertToLong()
                || !response.path("workOrderStatusDistribution").isArray()
                || response.path("workOrderStatusDistribution").size() != 4
                || response.path("generatedAt").asText().isBlank()) {

            throw new IllegalStateException(
                    "决策大屏聚合响应不正确：" + response);
        }
    }

    private static String dashboardPath(long regionId) {
        return "/api/decision/dashboard?regionId="
                + regionId
                + "&startDate=" + START
                + "&endDate=" + END;
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

    private static void post(
            HttpClient client,
            String path,
            String body,
            int expected) throws Exception {

        JsonNode csrf = csrf(client);

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + path))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .header(
                                csrf.path("headerName").asText(),
                                csrf.path("token").asText())
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        requireStatus("POST " + path, response, expected);
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

        System.out.println(
                "通过：" + operation + " → " + expected);
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
                    "登录失败：" + phone
                            + "，响应：" + response.body());
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
            throw new IllegalStateException(
                    "获取CSRF令牌失败");
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
                            "管理员测试账号不存在");
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

    private static void cleanup() throws Exception {
        try (Connection connection = connection();
             PreparedStatement grants = connection.prepareStatement(
                     "DELETE FROM sys_user_region "
                             + "WHERE user_id IN "
                             + "(SELECT id FROM sys_user WHERE phone = ?)");
             PreparedStatement user = connection.prepareStatement(
                     "DELETE FROM sys_user WHERE phone = ?")) {

            grants.setString(1, DECISION_PHONE);
            grants.executeUpdate();

            user.setString(1, DECISION_PHONE);
            user.executeUpdate();
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
            long outsideRegionId) {
    }
}
