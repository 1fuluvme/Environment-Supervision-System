package com.neps.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neps.entity.Measurement;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.neps.dto.StatisticsMeasurementRow;

import java.time.LocalDateTime;
import java.util.List;

public interface MeasurementMapper
        extends BaseMapper<Measurement> {

    @Select("""
            SELECT m.aqi
            FROM biz_measurement m
            JOIN biz_feedback f
              ON f.id = m.feedback_id
            WHERE f.grid_id = #{gridId}
              AND m.report_type = #{reportType}
              AND m.id <> #{measurementId}
              AND m.aqi_calculable = 1
              AND m.review_status = 'APPROVED'
            ORDER BY m.measured_at DESC, m.id DESC
            LIMIT 1
            """)
    Integer selectPreviousComparableAqi(
            @Param("gridId") Long gridId,
            @Param("reportType") String reportType,
            @Param("measurementId") Long measurementId);

    @Select("""
        WITH RECURSIVE region_tree AS (
            SELECT id
            FROM sys_region
            WHERE id = #{regionId}
              AND enabled = 1

            UNION ALL

            SELECT r.id
            FROM sys_region r
            JOIN region_tree parent
              ON r.parent_id = parent.id
            WHERE r.enabled = 1
        ),
        selected_measurements AS (
            SELECT
                f.grid_id,
                m.id,
                m.measured_at,
                m.aqi,
                m.aqi_level,
                CASE
                    WHEN m.review_status = 'APPROVED'
                     AND m.statistically_valid = 1
                     AND m.quality_flag = 'VALID'
                     AND m.aqi_calculable = 1
                     AND m.aqi IS NOT NULL
                     AND m.aqi_level BETWEEN 1 AND 6
                    THEN 1
                    ELSE 0
                END AS valid_aqi
            FROM biz_measurement m
            JOIN biz_feedback f
              ON f.id = m.feedback_id
            WHERE m.report_type = #{reportType}
              AND m.measured_at >= #{startTime}
              AND m.measured_at < #{endTime}
        )
        SELECT
            g.id AS grid_id,
            selected.id AS measurement_id,
            selected.measured_at,
            selected.aqi,
            selected.aqi_level,
            selected.valid_aqi
        FROM sys_grid g
        LEFT JOIN selected_measurements selected
          ON selected.grid_id = g.id
        WHERE g.region_id IN (
            SELECT id FROM region_tree
        )
          AND g.enabled = 1
        ORDER BY g.id, selected.measured_at, selected.id
        """)
    List<StatisticsMeasurementRow> selectStatisticsRows(
            @Param("regionId") Long regionId,
            @Param("reportType") String reportType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
