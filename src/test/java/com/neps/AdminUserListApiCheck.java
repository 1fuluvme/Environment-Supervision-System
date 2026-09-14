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
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

public class AdminUserListApiCheck {

    private static final String BASE = "http://localhost:8080";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> ROLES =
            Set.of("PUBLIC", "GRID", "ADMIN", "DECISION");

    public static void main(String[] args) throws Exception {
        get(newClient(), "/api/admin/users/list", 401);

        HttpClient admin = newClient();
        login(admin, env("ADMIN_PHONE"), env("ADMIN_PASSWORD"));

        JsonNode all = get(admin, "/api/admin/users/list", 200);
        checkUserArray(all, null);

        if (all.isEmpty()) {
            throw new IllegalStateException("账号列表不应为空");
        }

        Set<String> existingRoles = new HashSet<>();
        for (JsonNode user : all) {
            existingRoles.add(user.path("role").asText());
        }

        for (String role : existingRoles) {
            JsonNode filtered = get(
                    admin,
                    "/api/admin/users/list?role=" + role,
                    200);
            checkUserArray(filtered, role);

            long expectedCount = 0;
            for (JsonNode user : all) {
                if (role.equals(user.path("role").asText())) {
                    expectedCount++;
                }
            }

            if (filtered.size() != expectedCount) {
                throw new IllegalStateException(
                        role + " 角色筛选数量与全部列表不一致");
            }
        }

        get(admin, "/api/admin/users/list?role=UNKNOWN", 400);

        JsonNode first = all.get(0);
        JsonNode lookup = get(
                admin,
                "/api/admin/users?phone="
                        + URLEncoder.encode(
                        first.path("phone").asText(),
                        StandardCharsets.UTF_8),
                200);

        if (lookup.path("id").asLong() != first.path("id").asLong()) {
            throw new IllegalStateException("原手机号查询接口发生回归");
        }

        System.out.println("管理员账号全量列表、角色筛选与原查询接口检查全部通过");
    }

    private static void checkUserArray(
            JsonNode body,
            String expectedRole) {

        if (!body.isArray()) {
            throw new IllegalStateException("预期返回账号数组：" + body);
        }

        for (JsonNode user : body) {
            String role = user.path("role").asText();

            if (user.path("id").asLong() <= 0
                    || user.path("phone").asText().isBlank()
                    || user.path("displayName").asText().isBlank()
                    || !ROLES.contains(role)
                    || !user.path("enabled").isBoolean()
                    || user.has("password")
                    || user.has("passwordHash")
                    || user.has("password_hash")) {
                throw new IllegalStateException(
                        "账号列表响应内容不安全或不完整：" + user);
            }

            if (expectedRole != null && !expectedRole.equals(role)) {
                throw new IllegalStateException(
                        "角色筛选混入了其他角色：" + user);
            }
        }
    }

    private static HttpClient newClient() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(
                        null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    private static void login(
            HttpClient client,
            String phone,
            String password) throws Exception {

        HttpRequest csrfRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/api/auth/csrf"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> csrfResponse = client.send(
                csrfRequest,
                HttpResponse.BodyHandlers.ofString());

        if (csrfResponse.statusCode() != 200) {
            throw new IllegalStateException(
                    "获取 CSRF 令牌失败：" + csrfResponse.body());
        }

        JsonNode csrf = JSON.readTree(csrfResponse.body());

        String form = "phone="
                + URLEncoder.encode(phone, StandardCharsets.UTF_8)
                + "&password="
                + URLEncoder.encode(password, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/api/auth/login"))
                .timeout(Duration.ofSeconds(10))
                .header(
                        "Content-Type",
                        "application/x-www-form-urlencoded")
                .header(
                        csrf.path("headerName").asText(),
                        csrf.path("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 204) {
            throw new IllegalStateException(
                    "管理员登录失败：" + response.body());
        }
    }

    private static JsonNode get(
            HttpClient client,
            String path,
            int expected) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != expected) {
            throw new IllegalStateException(
                    path + " 预期 " + expected
                            + "，实际 " + response.statusCode()
                            + "，响应：" + response.body());
        }

        System.out.println("通过：" + path + " → " + expected);
        return JSON.readTree(
                response.body().isBlank() ? "null" : response.body());
    }

    private static String env(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("请设置环境变量 " + key);
        }
        return value;
    }
}
