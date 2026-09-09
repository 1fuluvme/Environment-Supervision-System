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
 * @since 2026-09-03
 */
@TableName("sys_grid")
public class Grid implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 网格ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属区域ID
     */
    private Long regionId;

    /**
     * 网格编码
     */
    private String code;

    /**
     * 网格名称
     */
    private String name;

    /**
     * 1启用，0停用
     */
    private Byte enabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRegionId() {
        return regionId;
    }

    public void setRegionId(Long regionId) {
        this.regionId = regionId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Byte getEnabled() {
        return enabled;
    }

    public void setEnabled(Byte enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return "Grid{" +
            "id = " + id +
            ", regionId = " + regionId +
            ", code = " + code +
            ", name = " + name +
            ", enabled = " + enabled +
        "}";
    }
}
