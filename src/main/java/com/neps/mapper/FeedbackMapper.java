package com.neps.mapper;

import com.neps.entity.Feedback;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 公众反馈 Mapper 接口
 * </p>
 *
 * @author neps
 * @since 2026-09-06
 */
public interface FeedbackMapper extends BaseMapper<Feedback> {
    List<Feedback> selectAccessibleForAdmin(
            @Param("adminId") Long adminId,
            @Param("status") String status,
            @Param("regionId") Long regionId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
