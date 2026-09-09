package com.neps.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neps.entity.Measurement;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
