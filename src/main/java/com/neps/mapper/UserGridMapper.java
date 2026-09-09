package com.neps.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neps.entity.Grid;
import com.neps.entity.UserGrid;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserGridMapper extends BaseMapper<UserGrid> {

    @Select("""
            WITH RECURSIVE region_chain AS (
                SELECT g.id AS grid_id, r.id, r.parent_id, r.enabled
                FROM sys_user_grid ug
                JOIN sys_grid g ON g.id = ug.grid_id
                JOIN sys_region r ON r.id = g.region_id
                WHERE ug.user_id = #{userId}
                  AND g.enabled = 1

                UNION DISTINCT

                SELECT c.grid_id, r.id, r.parent_id, r.enabled
                FROM region_chain c
                JOIN sys_region r ON r.id = c.parent_id
            )
            SELECT g.id, g.region_id, g.code, g.name, g.enabled
            FROM sys_user_grid ug
            JOIN sys_grid g ON g.id = ug.grid_id
            WHERE ug.user_id = #{userId}
              AND g.enabled = 1
              AND NOT EXISTS (
                  SELECT 1
                  FROM region_chain c
                  WHERE c.grid_id = g.id
                    AND c.enabled <> 1
              )
            ORDER BY g.id
            """)
    List<Grid> selectMyGrids(@Param("userId") Long userId);
}
