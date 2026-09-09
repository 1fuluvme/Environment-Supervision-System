package com.neps.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * <p>
 * 
 * </p>
 *
 * @author neps
 * @since 2026-09-04
 */
@TableName("sys_user_grid")
public class UserGrid implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 关联ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 网格员用户ID
     */
    private Long userId;

    /**
     * 负责的网格ID
     */
    private Long gridId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getGridId() {
        return gridId;
    }

    public void setGridId(Long gridId) {
        this.gridId = gridId;
    }

    @Override
    public String toString() {
        return "UserGrid{" +
            "id = " + id +
            ", userId = " + userId +
            ", gridId = " + gridId +
        "}";
    }
}
