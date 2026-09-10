package com.neps.mapper;

import com.neps.entity.ExternalAqi;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDate;
import java.util.List;

/**
 * <p>
 * 导入的历史AQI数据 Mapper 接口
 * </p>
 *
 * @author neps
 * @since 2026-09-10
 */
public interface ExternalAqiMapper extends BaseMapper<ExternalAqi> {

    @Select("""
        SELECT a.*
        FROM biz_external_aqi a
        JOIN (
            SELECT MAX(id) AS id
            FROM biz_external_aqi
            WHERE region_id = #{regionId}
              AND report_type = 'DAILY'
              AND quality_flag = 'VALID'
              AND source_name = #{sourceName}
              AND is_demo = #{isDemo}
              AND observed_at < #{targetDate}
            GROUP BY DATE(observed_at)
            ORDER BY DATE(observed_at) DESC
            LIMIT 7
        ) recent ON recent.id = a.id
        ORDER BY a.observed_at DESC, a.id DESC
        """)
    List<ExternalAqi> selectRecentValidDaily(
            @Param("regionId") Long regionId,
            @Param("targetDate") LocalDate targetDate,
            @Param("sourceName") String sourceName,
            @Param("isDemo") byte isDemo);
}
