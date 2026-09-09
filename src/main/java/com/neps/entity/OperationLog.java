package com.neps.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 业务操作记录
 * </p>
 *
 * @author neps
 * @since 2026-09-07
 */
@TableName("biz_operation_log")
public class OperationLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 操作记录ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 业务类型
     */
    private String businessType;

    /**
     * 业务记录ID
     */
    private Long businessId;

    /**
     * 操作类型
     */
    private String action;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作前状态
     */
    private String fromStatus;

    /**
     * 操作后状态
     */
    private String toStatus;

    /**
     * 改派前负责人
     */
    private Long fromAssigneeId;

    /**
     * 指派后的负责人
     */
    private Long toAssigneeId;

    /**
     * 要求、原因或办理意见
     */
    private String remark;

    /**
     * 操作时间
     */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public Long getBusinessId() {
        return businessId;
    }

    public void setBusinessId(Long businessId) {
        this.businessId = businessId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public Long getFromAssigneeId() {
        return fromAssigneeId;
    }

    public void setFromAssigneeId(Long fromAssigneeId) {
        this.fromAssigneeId = fromAssigneeId;
    }

    public Long getToAssigneeId() {
        return toAssigneeId;
    }

    public void setToAssigneeId(Long toAssigneeId) {
        this.toAssigneeId = toAssigneeId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "OperationLog{" +
            "id = " + id +
            ", businessType = " + businessType +
            ", businessId = " + businessId +
            ", action = " + action +
            ", operatorId = " + operatorId +
            ", fromStatus = " + fromStatus +
            ", toStatus = " + toStatus +
            ", fromAssigneeId = " + fromAssigneeId +
            ", toAssigneeId = " + toAssigneeId +
            ", remark = " + remark +
            ", createdAt = " + createdAt +
        "}";
    }
}
