package com.neps;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public class RegisterApiCheck {

    public static void main(String[] args) throws Exception {
        String phone = Objects.requireNonNull(
                System.getenv("TEST_PHONE"),
                "请设置一个尚未注册的 TEST_PHONE");

        ObjectMapper mapper = new ObjectMapper();

        String json = mapper.writeValueAsString(Map.of(
                "phone", phone,
                "displayName", "注册检查账号",
                "password", "DemoPass2026!",
                "role", "ADMIN"
        ));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/api/auth/register"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        for (int expected : new int[]{201, 409}) {
            var response = client.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != expected) {
                throw new IllegalStateException(
                        "预期状态码：" + expected
                                + "，实际状态码：" + response.statusCode());
            }

            var body = mapper.readTree(response.body());

            if (body.has("password") || body.has("passwordHash")) {
                throw new IllegalStateException("响应暴露了密码字段");
            }

            if (expected == 201
                    && (!"PUBLIC".equals(body.path("role").asText())
                    || body.path("id").asLong() <= 0)) {
                throw new IllegalStateException("用户身份或ID不符合预期");
            }
        }

        System.out.println("注册、角色限制和重复手机号检查通过");
    }
}
