package com.neps.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neps.entity.Region;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface RegionMapper
        extends BaseMapper<Region> {

    @Select("""
            WITH RECURSIVE region_tree AS (
                SELECT id
                FROM sys_region
                WHERE id = #{regionId}
                  AND enabled = 1

                UNION ALL

                SELECT child.id
                FROM sys_region child
                JOIN region_tree parent
                  ON child.parent_id = parent.id
                WHERE child.enabled = 1
            )
            SELECT id
            FROM region_tree
            ORDER BY id
            """)
    List<Long> selectEnabledIdsInTree(
            @Param("regionId") Long regionId);
}
