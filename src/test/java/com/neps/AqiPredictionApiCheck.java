package com.neps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neps.aqi.AqiCalculator;

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
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class AqiPredictionApiCheck {

    private static final String BASE = "http://localhost:8080";
    private static final String SOURCE = "NEPS_PREDICTION_CHECK_FULL";
    private static final String SHORT_SOURCE = "NEPS_PREDICTION_CHECK_SHORT";
    private static final String OUTSIDE_SOURCE = "NEPS_PREDICTION_CHECK_OUTSIDE";
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 8);
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        cleanup();

        try {
            long insideRegionId = regionId(envLong("TEST_INSIDE_GRID_ID"));
            long outsideRegionId = regionId(envLong("TEST_OUTSIDE_GRID_ID"));
            long adminId = userId(env("ADMIN_PHONE"));

            List<Long> expectedInputIds = seedFullData(insideRegionId, adminId);
            seedShortData(insideRegionId, adminId);
            long outsidePredictionId = seedOutsidePrediction(outsideRegionId, adminId);

            HttpClient anonymous = client();
            get(anonymous, "/api/aqi-predictions", 401);

            HttpClient admin = client();
            login(admin, env("ADMIN_PHONE"), env("ADMIN_PASSWORD"));

            JsonNode created = post(
                    admin,
                    requestBody(insideRegionId, TARGET_DATE, SOURCE),
                    201);

            long predictionId = created.path("id").asLong();

            requirePrediction(created, insideRegionId, expectedInputIds);
            checkDatabase(predictionId, expectedInputIds);

            post(
                    admin,
                    requestBody(insideRegionId, TARGET_DATE, SOURCE),
                    409);

            post(
                    admin,
                    requestBody(insideRegionId, TARGET_DATE, SHORT_SOURCE),
                    422);

            if (predictionCount(SHORT_SOURCE) != 0) {
                throw new IllegalStateException("数据不足时不应保存预测结果");
            }

            post(
                    admin,
                    requestBody(
                            insideRegionId,
                            LocalDate.now().plusDays(2),
                            SOURCE),
                    400);

            post(
                    admin,
                    requestBody(outsideRegionId, TARGET_DATE, OUTSIDE_SOURCE),
                    403);

            JsonNode detail = getJson(
                    admin,
                    "/api/aqi-predictions/" + predictionId,
                    200);
            requirePrediction(detail, insideRegionId, expectedInputIds);

            JsonNode list = getJson(
                    admin,
                    "/api/aqi-predictions?regionId=" + insideRegionId,
                    200);

            boolean found = false;
            for (JsonNode item : list) {
                if (item.path("id").asLong() == predictionId) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                throw new IllegalStateException("授权区域列表缺少刚生成的预测");
            }

            get(
                    admin,
                    "/api/aqi-predictions?regionId=" + outsideRegionId,
                    403);
            get(
                    admin,
                    "/api/aqi-predictions/" + outsidePredictionId,
                    403);

            System.out.println("未来一天AQI趋势预测接口检查全部通过");
        } finally {
            cleanup();
        }
    }

    private static List<Long> seedFullData(
            long regionId,
            long adminId) throws Exception {

        try (Connection connection = connection()) {
            connection.setAutoCommit(false);

            try {
                long batchId = insertBatch(
                        connection,
                        SOURCE,
                        11,
                        adminId);

                java.util.ArrayList<Long> inputIds =
                        new java.util.ArrayList<>();

                for (int day = 1; day <= 7; day++) {
                    inputIds.add(insertAqi(
                            connection,
                            batchId,
                            "FULL-" + day,
                            regionId,
                            LocalDate.of(2026, 8, day).atStartOfDay(),
                            "DAILY",
                            40 + day * 10,
                            "VALID",
                            SOURCE));
                }

                insertAqi(
                        connection,
                        batchId,
                        "OLD",
                        regionId,
                        LocalDate.of(2026, 7, 31).atStartOfDay(),
                        "DAILY",
                        5,
                        "VALID",
                        SOURCE);

                insertAqi(
                        connection,
                        batchId,
                        "INVALID",
                        regionId,
                        LocalDate.of(2026, 8, 7).atStartOfDay(),
                        "DAILY",
                        500,
                        "INVALID",
                        SOURCE);

                insertAqi(
                        connection,
                        batchId,
                        "REALTIME",
                        regionId,
                        LocalDate.of(2026, 8, 7).atTime(12, 0),
                        "REALTIME",
                        500,
                        "VALID",
                        SOURCE);

                insertAqi(
                        connection,
                        batchId,
                        "ACTUAL",
                        regionId,
                        TARGET_DATE.atStartOfDay(),
                        "DAILY",
                        130,
                        "VALID",
                        SOURCE);

                connection.commit();
                return List.copyOf(inputIds);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void seedShortData(
            long regionId,
            long adminId) throws Exception {

        try (Connection connection = connection()) {
            connection.setAutoCommit(false);

            try {
                long batchId = insertBatch(
                        connection,
                        SHORT_SOURCE,
                        6,
                        adminId);

                for (int day = 1; day <= 6; day++) {
                    insertAqi(
                            connection,
                            batchId,
                            "SHORT-" + day,
                            regionId,
                            LocalDate.of(2026, 8, day).atStartOfDay(),
                            "DAILY",
                            60,
                            "VALID",
                            SHORT_SOURCE);
                }

                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static long insertBatch(
            Connection connection,
            String source,
            int count,
            long adminId) throws Exception {

        String sql = """
                INSERT INTO biz_import_batch (
                    batch_no, data_type, original_name, file_hash,
                    record_count, source_name, is_demo,
                    imported_by, imported_at
                ) VALUES (?, 'AQI', ?, ?, ?, ?, 1, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(
                sql,
                Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, source + ".csv");
            statement.setString(
                    3,
                    UUID.randomUUID().toString().replace("-", "")
                            + UUID.randomUUID().toString().replace("-", ""));
            statement.setInt(4, count);
            statement.setString(5, source);
            statement.setLong(6, adminId);
            statement.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static long insertAqi(
            Connection connection,
            long batchId,
            String recordCode,
            long regionId,
            LocalDateTime observedAt,
            String reportType,
            int aqi,
            String qualityFlag,
            String source) throws Exception {

        String sql = """
                INSERT INTO biz_external_aqi (
                    import_batch_id, record_code, region_id, observed_at,
                    report_type, aqi, aqi_level, aqi_category,
                    quality_flag, source_name, is_demo, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(
                sql,
                Statement.RETURN_GENERATED_KEYS)) {

            statement.setLong(1, batchId);
            statement.setString(2, recordCode);
            statement.setLong(3, regionId);
            statement.setTimestamp(4, Timestamp.valueOf(observedAt));
            statement.setString(5, reportType);
            statement.setInt(6, aqi);
            statement.setInt(7, AqiCalculator.level(aqi));
            statement.setString(8, AqiCalculator.category(aqi));
            statement.setString(9, qualityFlag);
            statement.setString(10, source);
            statement.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static long seedOutsidePrediction(
            long regionId,
            long adminId) throws Exception {

        String sql = """
                INSERT INTO biz_aqi_prediction (
                    region_id, history_start_date, history_end_date,
                    target_date, method, sample_count, predicted_aqi,
                    input_record_ids, source_name, is_demo,
                    generated_by, generated_at
                ) VALUES (?, '2026-08-01', '2026-08-07',
                    '2026-08-08', 'MA7', 7, 80,
                    '1,2,3,4,5,6,7', ?, 1, ?, ?)
                """;

        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     sql,
                     Statement.RETURN_GENERATED_KEYS)) {

            statement.setLong(1, regionId);
            statement.setString(2, OUTSIDE_SOURCE);
            statement.setLong(3, adminId);
            statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static void requirePrediction(
            JsonNode response,
            long regionId,
            List<Long> expectedInputIds) {

        Set<Long> actualInputIds = new HashSet<>();
        response.path("inputRecordIds")
                .forEach(node -> actualInputIds.add(node.asLong()));

        if (response.path("id").asLong() <= 0
                || response.path("regionId").asLong() != regionId
                || !"2026-08-01".equals(response.path("historyStartDate").asText())
                || !"2026-08-07".equals(response.path("historyEndDate").asText())
                || !"2026-08-08".equals(response.path("targetDate").asText())
                || !"MA7".equals(response.path("method").asText())
                || response.path("sampleCount").asInt() != 7
                || response.path("predictedAqi").asInt() != 80
                || response.path("actualAqi").asInt() != 130
                || response.path("absoluteError").asDouble() != 50.0
                || !SOURCE.equals(response.path("sourceName").asText())
                || !response.path("demo").asBoolean()
                || !actualInputIds.equals(new HashSet<>(expectedInputIds))) {

            throw new IllegalStateException("预测结果不正确：" + response);
        }
    }

    private static void checkDatabase(
            long predictionId,
            List<Long> expectedInputIds) throws Exception {

        String sql = """
                SELECT predicted_aqi, actual_aqi, absolute_error,
                       input_record_ids
                FROM biz_aqi_prediction
                WHERE id = ?
                """;

        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, predictionId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()
                        || result.getInt("predicted_aqi") != 80
                        || result.getInt("actual_aqi") != 130
                        || result.getBigDecimal("absolute_error").intValue() != 50) {
                    throw new IllegalStateException("预测数据库记录不正确");
                }

                Set<Long> storedIds = new HashSet<>();
                for (String id : result.getString("input_record_ids").split(",")) {
                    storedIds.add(Long.valueOf(id));
                }

                if (!storedIds.equals(new HashSet<>(expectedInputIds))) {
                    throw new IllegalStateException("预测输入记录追溯不正确");
                }
            }
        }
    }

    private static String requestBody(
            long regionId,
            LocalDate targetDate,
            String source) {

        return """
                {
                  "regionId": %d,
                  "targetDate": "%s",
                  "sourceName": "%s",
                  "demo": true
                }
                """.formatted(regionId, targetDate, source);
    }

    private static JsonNode post(
            HttpClient client,
            String body,
            int expected) throws Exception {

        JsonNode csrf = csrf(client);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/api/aqi-predictions"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header(
                        csrf.path("headerName").asText(),
                        csrf.path("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        requireStatus("POST /api/aqi-predictions", response, expected);
        return response.body().isBlank()
                ? JSON.nullNode()
                : JSON.readTree(response.body());
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/api/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
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

    private static long regionId(long gridId) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT region_id FROM sys_grid WHERE id = ?")) {

            statement.setLong(1, gridId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("测试网格不存在：" + gridId);
                }
                return result.getLong(1);
            }
        }
    }

    private static long userId(String phone) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
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

    private static long predictionCount(String source) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM biz_aqi_prediction WHERE source_name = ?")) {

            statement.setString(1, source);

            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void cleanup() throws Exception {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate(
                    "DELETE FROM biz_aqi_prediction "
                            + "WHERE source_name LIKE 'NEPS_PREDICTION_CHECK_%'");
            statement.executeUpdate(
                    "DELETE FROM biz_external_aqi "
                            + "WHERE source_name LIKE 'NEPS_PREDICTION_CHECK_%'");
            statement.executeUpdate(
                    "DELETE FROM biz_import_batch "
                            + "WHERE source_name LIKE 'NEPS_PREDICTION_CHECK_%'");
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
