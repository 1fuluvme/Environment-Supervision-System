package com.neps;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FrontendDeploymentApiCheck {

    private static final String BASE_URL =
            System.getProperty(
                    "baseUrl",
                    "http://localhost:8080");

    private static final HttpClient CLIENT =
            HttpClient.newHttpClient();

    public static void main(String[] args)
            throws Exception {

        HttpResponse<String> index = get("/");
        expectStatus(index, 200, "前端首页");
        expect(
                index.body().contains("东软环保公众监督系统"),
                "首页没有包含前端应用标题");

        Matcher assetMatcher = Pattern
                .compile("(?:src|href)=\"(/assets/[^\"]+)\"")
                .matcher(index.body());

        expect(assetMatcher.find(), "首页没有引用 Vite 构建资源");

        HttpResponse<String> asset =
                get(assetMatcher.group(1));
        expectStatus(asset, 200, "前端静态资源");

        HttpResponse<String> csrf =
                get("/api/auth/csrf");
        expectStatus(csrf, 200, "CSRF接口");

        HttpResponse<String> currentUser =
                get("/api/auth/me");
        expectStatus(currentUser, 401, "未登录身份接口");

        System.out.println(
                "前端单JAR部署检查全部通过");
    }

    private static HttpResponse<String> get(
            String path) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .GET()
                .build();

        return CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString());
    }

    private static void expectStatus(
            HttpResponse<?> response,
            int expected,
            String name) {

        expect(
                response.statusCode() == expected,
                name + "状态码应为 " + expected
                        + "，实际为 "
                        + response.statusCode());
    }

    private static void expect(
            boolean condition,
            String message) {

        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
