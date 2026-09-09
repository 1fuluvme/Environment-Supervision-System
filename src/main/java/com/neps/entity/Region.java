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
@TableName("sys_region")
public class Region implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 区域ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 上级区域ID，省级为空
     */
    private Long parentId;

    /**
     * 区域编码
     */
    private String code;

    /**
     * 区域名称
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

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
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
        return "Region{" +
            "id = " + id +
            ", parentId = " + parentId +
            ", code = " + code +
            ", name = " + name +
            ", enabled = " + enabled +
        "}";
    }
}
