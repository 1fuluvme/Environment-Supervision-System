package com.neps.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neps.dto.AiAgentAuditResponse;
import com.neps.dto.AiQuestionRequest;
import com.neps.dto.AiQuestionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.neps.dto.AiAgentAuditSummaryResponse;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Service
public class AiAgentAuditService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    AiAgentAuditService.class);

    private static final Set<String> STATUSES =
            Set.of("SUCCEEDED", "FAILED");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AiAgentAuditService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {

        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void recordSucceeded(
            AiQuestionRequest request,
            String question,
            List<AiQuestionResponse.Source> sources,
            String provider,
            String modelName,
            long durationMs) {

        List<String> sourceTypes =
                sources.stream()
                        .map(
                                AiQuestionResponse.Source
                                        ::type)
                        .distinct()
                        .toList();

        record(
                request,
                question,
                "SUCCEEDED",
                provider,
                modelName,
                sourceTypes,
                durationMs,
                null);
    }

    public void recordFailed(
            AiQuestionRequest request,
            String question,
            String provider,
            String modelName,
            long durationMs,
            Exception exception) {

        String failureReason =
                exception.getMessage() == null
                        ? exception
                        .getClass()
                        .getSimpleName()
                        : exception.getMessage();

        if (failureReason.length() > 500) {
            failureReason =
                    failureReason.substring(
                            0,
                            500);
        }

        record(
                request,
                question,
                "FAILED",
                provider,
                modelName,
                List.of(),
                durationMs,
                failureReason);
    }

    public List<AiAgentAuditResponse> listForAdmin(
            String status) {

        String normalizedStatus =
                status == null
                        || status.isBlank()
                        ? null
                        : status.trim()
                        .toUpperCase(
                                Locale.ROOT);

        if (normalizedStatus != null
                && !STATUSES.contains(
                normalizedStatus)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Agent审计状态不正确");
        }

        return jdbcTemplate.query(
                """
                SELECT
                    a.id,
                    a.user_id,
                    u.phone AS user_phone,
                    a.conversation_id,
                    a.region_id,
                    a.start_date,
                    a.end_date,
                    a.report_type,
                    a.question,
                    a.status,
                    a.provider,
                    a.model_name,
                    a.source_types_json,
                    a.duration_ms,
                    a.failure_reason,
                    a.created_at
                FROM biz_ai_agent_audit a
                JOIN sys_user u
                  ON u.id = a.user_id
                WHERE (? IS NULL OR a.status = ?)
                ORDER BY a.id DESC
                LIMIT 200
                """,
                (result, rowNumber) ->
                        new AiAgentAuditResponse(
                                result.getLong("id"),
                                result.getLong(
                                        "user_id"),
                                result.getString(
                                        "user_phone"),
                                result.getString(
                                        "conversation_id"),
                                result.getLong(
                                        "region_id"),
                                result.getDate(
                                                "start_date")
                                        .toLocalDate(),
                                result.getDate(
                                                "end_date")
                                        .toLocalDate(),
                                result.getString(
                                        "report_type"),
                                result.getString(
                                        "question"),
                                result.getString(
                                        "status"),
                                result.getString(
                                        "provider"),
                                result.getString(
                                        "model_name"),
                                readSourceTypes(
                                        result.getString(
                                                "source_types_json")),
                                result.getLong(
                                        "duration_ms"),
                                result.getString(
                                        "failure_reason"),
                                result.getTimestamp(
                                                "created_at")
                                        .toLocalDateTime()),
                normalizedStatus,
                normalizedStatus);
    }

    public AiAgentAuditSummaryResponse summaryForAdmin(
            Integer days) {

        if (days == null
                || days < 1
                || days > 30) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "统计天数必须在1到30之间");
        }

        LocalDateTime since =
                LocalDateTime.now()
                        .minusDays(days);

        return jdbcTemplate.queryForObject(
                """
                SELECT
                    COUNT(*) AS total_count,
                    COALESCE(
                        SUM(
                            CASE
                                WHEN status = 'SUCCEEDED'
                                THEN 1
                                ELSE 0
                            END
                        ),
                        0
                    ) AS succeeded_count,
                    COALESCE(
                        SUM(
                            CASE
                                WHEN status = 'FAILED'
                                THEN 1
                                ELSE 0
                            END
                        ),
                        0
                    ) AS failed_count,
                    COALESCE(
                        ROUND(AVG(duration_ms)),
                        0
                    ) AS average_duration_ms
                FROM biz_ai_agent_audit
                WHERE created_at >= ?
                """,
                (result, rowNumber) -> {
                    long total =
                            result.getLong(
                                    "total_count");

                    long succeeded =
                            result.getLong(
                                    "succeeded_count");

                    long failed =
                            result.getLong(
                                    "failed_count");

                    double successRate =
                            total == 0
                                    ? 0
                                    : Math.round(
                                    succeeded
                                            * 1000.0
                                            / total)
                                    / 10.0;

                    return new AiAgentAuditSummaryResponse(
                            days,
                            total,
                            succeeded,
                            failed,
                            successRate,
                            result.getLong(
                                    "average_duration_ms"),
                            since);
                },
                Timestamp.valueOf(since));
    }

    private void record(
            AiQuestionRequest request,
            String question,
            String status,
            String provider,
            String modelName,
            List<String> sourceTypes,
            long durationMs,
            String failureReason) {

        String phone =
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getName();

        try {
            String sourceTypesJson =
                    objectMapper
                            .writeValueAsString(
                                    sourceTypes);

            int inserted =
                    jdbcTemplate.update(
                            """
                            INSERT INTO biz_ai_agent_audit
                            (
                                user_id,
                                conversation_id,
                                region_id,
                                start_date,
                                end_date,
                                report_type,
                                question,
                                status,
                                provider,
                                model_name,
                                source_types_json,
                                duration_ms,
                                failure_reason,
                                created_at
                            )
                            SELECT
                                id,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                ?,
                                NOW()
                            FROM sys_user
                            WHERE phone = ?
                            """,
                            request.conversationId(),
                            request.regionId(),
                            request.startDate(),
                            request.endDate(),
                            request.reportType()
                                    .trim()
                                    .toUpperCase(
                                            Locale.ROOT),
                            question,
                            status,
                            provider,
                            modelName,
                            sourceTypesJson,
                            durationMs,
                            failureReason,
                            phone);

            if (inserted != 1) {
                LOGGER.error(
                        "Agent审计日志未写入，用户={}",
                        phone);
            }
        } catch (RuntimeException
                 | JsonProcessingException exception) {

            // 审计异常不能覆盖已经生成的Agent回答。
            LOGGER.error(
                    "Agent审计日志写入失败，用户={}",
                    phone,
                    exception);
        }
    }

    private List<String> readSourceTypes(
            String value)
            throws SQLException {

        try {
            return objectMapper.readValue(
                    value,
                    new TypeReference<>() {
                    });
        } catch (JsonProcessingException exception) {
            throw new SQLException(
                    "Agent审计来源数据格式错误",
                    exception);
        }
    }
}
