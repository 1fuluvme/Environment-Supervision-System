package com.neps.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 反馈核查任务
 * </p>
 *
 * @author neps
 * @since 2026-09-07
 */
@TableName("biz_inspection_task")
public class InspectionTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 核查任务ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 对应公众反馈ID
     */
    private Long feedbackId;

    /**
     * 当前负责网格员ID
     */
    private Long assigneeId;

    /**
     * 最近一次指派的管理员ID
     */
    private Long assignedBy;

    /**
     * 核查要求
     */
    private String requirement;

    /**
     * LOW低，MEDIUM中，HIGH高
     */
    private String priority;

    /**
     * PENDING待处理，PENDING_REVIEW待复核，COMPLETED已完成
     */
    private String status;

    /**
     * 最近指派时间
     */
    private LocalDateTime assignedAt;

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

    public Long getFeedbackId() {
        return feedbackId;
    }

    public void setFeedbackId(Long feedbackId) {
        this.feedbackId = feedbackId;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }

    public Long getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(Long assignedBy) {
        this.assignedBy = assignedBy;
    }

    public String getRequirement() {
        return requirement;
    }

    public void setRequirement(String requirement) {
        this.requirement = requirement;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(LocalDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "InspectionTask{" +
            "id = " + id +
            ", feedbackId = " + feedbackId +
            ", assigneeId = " + assigneeId +
            ", assignedBy = " + assignedBy +
            ", requirement = " + requirement +
            ", priority = " + priority +
            ", status = " + status +
            ", assignedAt = " + assignedAt +
            ", updatedAt = " + updatedAt +
        "}";
    }
}
