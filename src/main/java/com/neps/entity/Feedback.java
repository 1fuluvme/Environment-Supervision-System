package com.neps.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 公众反馈
 * </p>
 *
 * @author neps
 * @since 2026-09-06
 */
@TableName("biz_feedback")
public class Feedback implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 反馈ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 提交人用户ID
     */
    private Long submitterId;

    /**
     * 发生地点所属网格ID
     */
    private Long gridId;

    /**
     * 详细地址
     */
    private String address;

    /**
     * 问题观测时间
     */
    private LocalDateTime observedAt;

    /**
     * 问题描述
     */
    private String description;

    /**
     * PENDING_ASSIGN待指派，CHECKING核查中，PENDING_REVIEW待复核，PROCESSING处理中，COMPLETED已完成
     */
    private String status;

    /**
     * 公众可见的办理说明
     */
    private String publicReply;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSubmitterId() {
        return submitterId;
    }

    public void setSubmitterId(Long submitterId) {
        this.submitterId = submitterId;
    }

    public Long getGridId() {
        return gridId;
    }

    public void setGridId(Long gridId) {
        this.gridId = gridId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDateTime getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(LocalDateTime observedAt) {
        this.observedAt = observedAt;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPublicReply() {
        return publicReply;
    }

    public void setPublicReply(String publicReply) {
        this.publicReply = publicReply;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "Feedback{" +
            "id = " + id +
            ", submitterId = " + submitterId +
            ", gridId = " + gridId +
            ", address = " + address +
            ", observedAt = " + observedAt +
            ", description = " + description +
            ", status = " + status +
            ", publicReply = " + publicReply +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
        "}";
    }
}
