package com.neps.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neps.entity.UserRegion;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.neps.entity.Region;
import java.util.List;

public interface UserRegionMapper extends BaseMapper<UserRegion> {

    @Select("""
            WITH RECURSIVE region_chain AS (
                SELECT id, parent_id, enabled
                FROM sys_region
                WHERE id = #{regionId}

                UNION DISTINCT

                SELECT r.id, r.parent_id, r.enabled
                FROM sys_region r
                JOIN region_chain c ON r.id = c.parent_id
            )
            SELECT COUNT(*)
            FROM sys_user_region ur
            JOIN region_chain c ON c.id = ur.region_id
            WHERE ur.user_id = #{userId}
              AND NOT EXISTS (
                  SELECT 1
                  FROM region_chain
                  WHERE enabled <> 1
              )
            """)

    long countAccessibleRegion(
            @Param("userId") Long userId,
            @Param("regionId") Long regionId);

    @Select("""
        WITH RECURSIVE grant_ancestors AS (
            SELECT ur.region_id AS grant_id,
                   r.id, r.parent_id, r.enabled
            FROM sys_user_region ur
            JOIN sys_region r ON r.id = ur.region_id
            WHERE ur.user_id = #{userId}

            UNION DISTINCT

            SELECT c.grant_id, r.id, r.parent_id, r.enabled
            FROM grant_ancestors c
            JOIN sys_region r ON r.id = c.parent_id
        ),
        accessible_regions AS (
            SELECT r.id, r.parent_id, r.code, r.name, r.enabled
            FROM sys_user_region ur
            JOIN sys_region r ON r.id = ur.region_id
            WHERE ur.user_id = #{userId}
              AND NOT EXISTS (
                  SELECT 1
                  FROM grant_ancestors c
                  WHERE c.grant_id = r.id
                    AND c.enabled <> 1
              )

            UNION DISTINCT

            SELECT r.id, r.parent_id, r.code, r.name, r.enabled
            FROM sys_region r
            JOIN accessible_regions p ON r.parent_id = p.id
            WHERE r.enabled = 1
        )
        SELECT id, parent_id, code, name, enabled
        FROM accessible_regions
        ORDER BY id
        """)

    List<Region> selectMyRegions(@Param("userId") Long userId);
}
