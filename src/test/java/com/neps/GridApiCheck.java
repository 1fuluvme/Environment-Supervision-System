package com.neps;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class GridApiCheck {

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        ObjectMapper mapper = new ObjectMapper();

        Map<String, Integer> cases = Map.of(
                "/api/grids/1", 200,
                "/api/grids/0", 400,
                "/api/grids/-1", 400,
                "/api/grids/abc", 400,
                "/api/grids/999999", 404
        );

        for (var item : cases.entrySet()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080" + item.getKey()))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            var response = client.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != item.getValue()) {
                throw new IllegalStateException(
                        item.getKey() + " 状态码不符合预期：" + response.body());
            }

            var body = mapper.readTree(response.body());

            if (item.getValue() == 200) {
                if (body.path("id").asLong() != 1) {
                    throw new IllegalStateException("返回了错误的网格");
                }
            } else if (body.path("status").asInt() != item.getValue()) {
                throw new IllegalStateException(
                        "错误响应格式不符合预期：" + response.body());
            }

            System.out.println("通过：" + item.getKey());
        }

        System.out.println("全部接口检查通过");
    }
}