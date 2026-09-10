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
import java.time.Duration;

public class AqiImportApiCheck {

    private static final String BASE = "http://localhost:8080";
    private static final String SOURCE = "NEPS_API_CHECK";
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        cleanup();

        String insideRegion = regionCode(envLong("TEST_INSIDE_GRID_ID"));
        String outsideRegion = regionCode(envLong("TEST_OUTSIDE_GRID_ID"));

        HttpClient admin = client();
        login(admin, env("ADMIN_PHONE"), env("ADMIN_PASSWORD"));

        get(client(), "/api/admin/imports/aqi/template", 401);

        HttpResponse<String> template = get(
                admin, "/api/admin/imports/aqi/template", 200);

        if (!template.body().contains("record_code,region_code")
                || !template.body().contains("report_type")
                || !template.body().contains("source_name,is_demo")) {
            throw new IllegalStateException("AQI导入模板内容不正确");
        }

        long before = importedRowCount();

        JsonNode badRows = upload(
                admin,
                "bad.csv",
                "record_code,region_code,observed_at,report_type,aqi,quality_flag,source_name,is_demo\n"
                        + "BAD-1," + insideRegion + ",wrong,DAILY,50,VALID," + SOURCE + ",1\n"
                        + "BAD-2," + insideRegion + ",2026-08-01T00:00:00,DAILY,501,VALID," + SOURCE + ",1\n",
                400);

        if (badRows.path("success").asBoolean()
                || badRows.path("errors").size() != 2
                || importedRowCount() != before) {
            throw new IllegalStateException("错误行报告或整批回滚不正确：" + badRows);
        }

        JsonNode outside = upload(
                admin,
                "outside.csv",
                csvRow("OUTSIDE-1", outsideRegion, "2026-08-01T00:00:00", 60),
                400);

        if (outside.path("success").asBoolean()
                || importedRowCount() != before) {
            throw new IllegalStateException("越权区域数据不应写入");
        }

        String valid = validSevenDays(insideRegion);

        JsonNode imported = upload(
                admin, "aqi-seven-days.csv", valid, 201);

        long batchId = imported.path("batchId").asLong();

        if (!imported.path("success").asBoolean()
                || batchId <= 0
                || imported.path("recordCount").asInt() != 7
                || !imported.path("errors").isArray()
                || !imported.path("errors").isEmpty()
                || importedRowCount() != before + 7) {
            throw new IllegalStateException("正常AQI导入结果不正确：" + imported);
        }

        upload(admin, "renamed.csv", valid, 409);

        String duplicateRecord = csvRow(
                "AQI-DEMO-01",
                insideRegion,
                "2026-08-08T00:00:00",
                88);

        JsonNode duplicate = upload(
                admin, "duplicate-record.csv", duplicateRecord, 400);

        if (duplicate.path("success").asBoolean()
                || importedRowCount() != before + 7) {
            throw new IllegalStateException("重复记录不应再次写入");
        }

        checkDatabase(batchId);

        System.out.println(
                "AQI模板、整批校验、区域权限和重复导入检查通过");
    }

    private static String validSevenDays(String regionCode) {
        StringBuilder csv = new StringBuilder(header());

        for (int day = 1; day <= 7; day++) {
            csv.append("AQI-DEMO-0").append(day).append(',')
                    .append(regionCode).append(',')
                    .append("2026-08-0").append(day)
                    .append("T00:00:00,DAILY,")
                    .append(50 + day * 10)
                    .append(",VALID,")
                    .append(SOURCE).append(",1\n");
        }
        return csv.toString();
    }

    private static String csvRow(
            String recordCode,
            String regionCode,
            String observedAt,
            int aqi) {

        return header()
                + recordCode + ","
                + regionCode + ","
                + observedAt + ",DAILY,"
                + aqi + ",VALID,"
                + SOURCE + ",1\n";
    }

    private static String header() {
        return "record_code,region_code,observed_at,report_type,aqi,quality_flag,source_name,is_demo\n";
    }

    private static JsonNode upload(
            HttpClient client,
            String filename,
            String csv,
            int expected) throws Exception {

        JsonNode csrf = csrf(client);
        String boundary = "----neps-aqi-import";
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\""
                + filename + "\"\r\n"
                + "Content-Type: text/csv\r\n\r\n"
                + csv
                + "\r\n--" + boundary + "--\r\n";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/api/admin/imports/aqi"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != expected) {
            throw new IllegalStateException(
                    "/api/admin/imports/aqi 预期 " + expected
                            + "，实际 " + response.statusCode()
                            + "，响应：" + response.body());
        }

        System.out.println("通过：/api/admin/imports/aqi → " + expected);
        return response.body().isBlank()
                ? JSON.nullNode()
                : JSON.readTree(response.body());
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
                    path + " 预期 " + expected
                            + "，实际 " + response.statusCode());
        }
        System.out.println("通过：" + path + " → " + expected);
        return response;
    }

    private static void login(
            HttpClient client,
            String phone,
            String password) throws Exception {

        JsonNode csrf = csrf(client);
        String form = "phone=" + URLEncoder.encode(phone, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/api/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        if (client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() != 204) {
            throw new IllegalStateException("管理员登录失败");
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

    private static String regionCode(long gridId) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT r.code FROM sys_grid g "
                             + "JOIN sys_region r ON r.id = g.region_id "
                             + "WHERE g.id = ?")) {

            statement.setLong(1, gridId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("测试网格不存在：" + gridId);
                }
                return result.getString(1);
            }
        }
    }

    private static long importedRowCount() throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM biz_external_aqi WHERE source_name = ?")) {

            statement.setString(1, SOURCE);

            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void checkDatabase(long batchId) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT b.record_count, b.is_demo, COUNT(a.id) AS actual_count "
                             + "FROM biz_import_batch b "
                             + "JOIN biz_external_aqi a ON a.import_batch_id = b.id "
                             + "WHERE b.id = ? AND b.data_type = 'AQI' "
                             + "AND b.source_name = ? "
                             + "GROUP BY b.id, b.record_count, b.is_demo")) {

            statement.setLong(1, batchId);
            statement.setString(2, SOURCE);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()
                        || result.getInt("record_count") != 7
                        || result.getInt("actual_count") != 7
                        || result.getInt("is_demo") != 1) {
                    throw new IllegalStateException("导入批次数据库核对失败");
                }
            }
        }
    }

    private static void cleanup() throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate(
                    "DELETE FROM biz_external_aqi WHERE source_name = '"
                            + SOURCE + "'");
            statement.executeUpdate(
                    "DELETE FROM biz_import_batch WHERE source_name = '"
                            + SOURCE + "'");
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
            throw new IllegalStateException("缺少环境变量：" + name);
        }
        return value;
    }
}
