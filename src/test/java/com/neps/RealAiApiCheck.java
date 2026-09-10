package com.neps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;

public class RealAiApiCheck {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        AnalysisTarget target = prepareOneAnalysis();
        awaitRealResult(target);

        System.out.println(
                "真实AI文字、图片、结构化结果和数据库状态检查通过");
    }

    private static AnalysisTarget prepareOneAnalysis()
            throws Exception {

        try (Connection connection = connection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM biz_ai_analysis "
                            + "WHERE status IN ('PENDING','RUNNING')")) {

                try (ResultSet result = statement.executeQuery()) {
                    result.next();

                    if (result.getLong(1) != 0) {
                        throw new IllegalStateException(
                                "数据库还有待处理AI记录。为避免多次真实计费，"
                                        + "请先用Mock模式处理完再运行本检查");
                    }
                }
            }

            AnalysisTarget target;

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT a.id, a.target_id, a.attempt_count "
                            + "FROM biz_ai_analysis a "
                            + "JOIN biz_feedback f ON f.id = a.target_id "
                            + "JOIN sys_user u ON u.id = f.submitter_id "
                            + "WHERE a.target_type = 'FEEDBACK' "
                            + "AND u.phone = '18800000001' "
                            + "AND EXISTS (SELECT 1 FROM biz_attachment x "
                            + "WHERE x.business_type = 'FEEDBACK' "
                            + "AND x.business_id = f.id) "
                            + "ORDER BY a.id DESC LIMIT 1")) {

                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new IllegalStateException(
                                "找不到带图片的固定测试反馈。"
                                        + "请先在Mock模式运行AdminUserApiCheck");
                    }

                    target = new AnalysisTarget(
                            result.getLong("id"),
                            result.getLong("target_id"),
                            result.getInt("attempt_count") + 1);
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE biz_ai_analysis SET status = 'PENDING', "
                            + "provider = NULL, model_name = NULL, "
                            + "input_snapshot = NULL, result_text = NULL, "
                            + "failure_reason = NULL, is_demo = 0, "
                            + "started_at = NULL, completed_at = NULL, "
                            + "updated_at = NOW() WHERE id = ?")) {

                statement.setLong(1, target.analysisId());

                if (statement.executeUpdate() != 1) {
                    throw new IllegalStateException(
                            "真实AI测试记录重新排队失败");
                }
            }

            return target;
        }
    }

    private static void awaitRealResult(
            AnalysisTarget target) throws Exception {

        long deadline = System.nanoTime()
                + Duration.ofSeconds(90).toNanos();

        while (System.nanoTime() < deadline) {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT status, provider, model_name, "
                                 + "input_snapshot, result_text, "
                                 + "failure_reason, is_demo, attempt_count, "
                                 + "started_at, completed_at "
                                 + "FROM biz_ai_analysis WHERE id = ?")) {

                statement.setLong(1, target.analysisId());

                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new IllegalStateException(
                                "真实AI测试记录不存在");
                    }

                    String status = result.getString("status");

                    if ("FAILED".equals(status)) {
                        throw new IllegalStateException(
                                "真实AI分析失败："
                                        + result.getString("failure_reason"));
                    }

                    if ("SUCCEEDED".equals(status)) {
                        JsonNode input = JSON.readTree(
                                result.getString("input_snapshot"));
                        JsonNode output = JSON.readTree(
                                result.getString("result_text"));

                        boolean invalid =
                                !"QWEN".equals(
                                        result.getString("provider"))
                                        || result.getString("model_name") == null
                                        || result.getInt("is_demo") != 0
                                        || result.getInt("attempt_count")
                                        != target.expectedAttemptCount()
                                        || result.getTimestamp("started_at") == null
                                        || result.getTimestamp("completed_at") == null
                                        || input.path("targetId").asLong()
                                        != target.feedbackId()
                                        || input.path("attachmentCount").asInt() < 1
                                        || output.path("summary").asText().isBlank()
                                        || output.path("suspectedPhenomenon")
                                        .asText().isBlank()
                                        || !output.path("checkItems").isArray()
                                        || output.path("checkItems").isEmpty()
                                        || output.path("safetyNotice")
                                        .asText().isBlank();

                        if (invalid) {
                            throw new IllegalStateException(
                                    "真实AI结果不符合预期");
                        }
                        return;
                    }
                }
            }

            Thread.sleep(500);
        }

        throw new IllegalStateException(
                "等待真实AI分析超时");
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/neps"
                        + "?characterEncoding=UTF-8"
                        + "&serverTimezone=Asia/Shanghai",
                env("DB_USERNAME"),
                env("DB_PASSWORD"));
    }

    private static String env(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "缺少环境变量：" + name);
        }
        return value;
    }

    private record AnalysisTarget(
            long analysisId,
            long feedbackId,
            int expectedAttemptCount) {
    }
}
