package com.neps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neps.ai.AiAgentRequestGuard;
import org.springframework.web.server.ResponseStatusException;

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
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;

public class AiAgentGuardApiCheck {

    private static final String BASE =
            "http://localhost:8080";

    private static final ObjectMapper JSON =
            new ObjectMapper();

    public static void main(String[] args)
            throws Exception {

        requireEnvironment();
        checkGuardDirectly();

        long regionId =
                regionId(
                        envLong(
                                "TEST_INSIDE_GRID_ID"));

        post(
                client(),
                regionId,
                Set.of(401));

        HttpClient admin = client();

        login(
                admin,
                env("ADMIN_PHONE"),
                env("ADMIN_PASSWORD"));

        post(
                admin,
                regionId,
                Set.of(200, 422));

        post(
                admin,
                regionId,
                Set.of(200, 422));

        post(
                admin,
                regionId,
                Set.of(429));

        Thread.sleep(2300);

        post(
                admin,
                regionId,
                Set.of(200, 422));

        System.out.println(
                "AI Agent限流与并发保护检查全部通过");
    }

    private static void checkGuardDirectly() {
        AiAgentRequestGuard guard =
                new AiAgentRequestGuard(
                        2,
                        2);

        try (AiAgentRequestGuard.Permit ignored =
                     guard.acquire("concurrent-user")) {

            expectTooManyRequests(() ->
                    guard.acquire(
                            "concurrent-user"));
        }

        try (AiAgentRequestGuard.Permit ignored =
                     guard.acquire("released-user")) {
            // 释放后同一用户应可再次进入。
        }

        try (AiAgentRequestGuard.Permit ignored =
                     guard.acquire("released-user")) {
            // 第二次请求仍在窗口额度内。
        }

        expectTooManyRequests(() ->
                guard.acquire(
                        "released-user"));

        System.out.println(
                "通过：Agent并发占用、释放和滑动窗口逻辑");
    }

    private static void expectTooManyRequests(
            Action action) {

        try {
            AiAgentRequestGuard.Permit permit =
                    action.run();

            permit.close();

            throw new IllegalStateException(
                    "预期触发429保护，但请求被允许");

        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value()
                    != 429) {

                throw new IllegalStateException(
                        "预期429，实际："
                                + exception.getStatusCode(),
                        exception);
            }
        }
    }

    private static void post(
            HttpClient client,
            long regionId,
            Set<Integer> expectedStatuses)
            throws Exception {

        JsonNode csrf = csrf(client);

        String body = """
                {
                  "question": "查询本区域空气统计",
                  "regionId": %d,
                  "startDate": "%s",
                  "endDate": "%s",
                  "reportType": "REALTIME"
                }
                """.formatted(
                regionId,
                LocalDate.of(2026, 1, 1),
                LocalDate.now());

        HttpResponse<String> response =
                client.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(
                                        BASE
                                                + "/api/ai/questions"))
                                .timeout(
                                        Duration.ofSeconds(30))
                                .header(
                                        "Content-Type",
                                        "application/json")
                                .header(
                                        csrf.path("headerName")
                                                .asText(),
                                        csrf.path("token")
                                                .asText())
                                .POST(
                                        HttpRequest.BodyPublishers
                                                .ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers
                                .ofString());

        if (!expectedStatuses.contains(
                response.statusCode())) {

            throw new IllegalStateException(
                    "POST /api/ai/questions 预期 "
                            + expectedStatuses
                            + "，实际 "
                            + response.statusCode()
                            + "，响应："
                            + response.body());
        }

        System.out.println(
                "通过：POST /api/ai/questions → "
                        + response.statusCode());
    }

    private static void requireEnvironment() {
        if (!"mock".equalsIgnoreCase(
                env("AI_MODE"))) {

            throw new IllegalStateException(
                    "本检查必须使用AI_MODE=mock，避免消耗模型额度");
        }

        if (envLong(
                "AI_MAX_REQUESTS_PER_WINDOW") != 2
                || envLong(
                "AI_RATE_LIMIT_WINDOW_SECONDS") != 2) {

            throw new IllegalStateException(
                    "本检查要求限流次数和窗口秒数都配置为2");
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
                                        csrf.path("headerName")
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

    private static long regionId(
            long gridId)
            throws Exception {

        try (Connection connection =
                     connection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             "SELECT region_id FROM sys_grid "
                                     + "WHERE id = ? AND enabled = 1")) {

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

    @FunctionalInterface
    private interface Action {
        AiAgentRequestGuard.Permit run();
    }
}
