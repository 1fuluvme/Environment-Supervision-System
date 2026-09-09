package com.neps.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 异常处置工单
 * </p>
 *
 * @author neps
 * @since 2026-09-09
 */
@TableName("biz_work_order")
public class WorkOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 处置工单ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 异常事件ID
     */
    private Long anomalyEventId;

    /**
     * 公众反馈ID
     */
    private Long feedbackId;

    /**
     * 所属网格ID
     */
    private Long gridId;

    /**
     * 负责网格员ID
     */
    private Long assigneeId;

    /**
     * 指派管理员ID
     */
    private Long assignedBy;

    /**
     * 处置要求
     */
    private String requirement;

    /**
     * LOW、MEDIUM、HIGH
     */
    private String priority;

    /**
     * PENDING_CONFIRM待确认、PENDING待处理、PENDING_REVIEW待复核、CLOSED已关闭
     */
    private String status;

    /**
     * 指派时间
     */
    private LocalDateTime assignedAt;

    /**
     * 实际处理时间
     */
    private LocalDateTime handledAt;

    /**
     * 处置措施
     */
    private String measures;

    /**
     * 处置结果
     */
    private String result;

    /**
     * 处置结果提交时间
     */
    private LocalDateTime submittedAt;

    /**
     * 复核管理员ID
     */
    private Long reviewedBy;

    /**
     * 复核时间
     */
    private LocalDateTime reviewedAt;

    /**
     * 复核意见
     */
    private String reviewOpinion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAnomalyEventId() {
        return anomalyEventId;
    }

    public void setAnomalyEventId(Long anomalyEventId) {
        this.anomalyEventId = anomalyEventId;
    }

    public Long getFeedbackId() {
        return feedbackId;
    }

    public void setFeedbackId(Long feedbackId) {
        this.feedbackId = feedbackId;
    }

    public Long getGridId() {
        return gridId;
    }

    public void setGridId(Long gridId) {
        this.gridId = gridId;
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

    public LocalDateTime getHandledAt() {
        return handledAt;
    }

    public void setHandledAt(LocalDateTime handledAt) {
        this.handledAt = handledAt;
    }

    public String getMeasures() {
        return measures;
    }

    public void setMeasures(String measures) {
        this.measures = measures;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(Long reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewOpinion() {
        return reviewOpinion;
    }

    public void setReviewOpinion(String reviewOpinion) {
        this.reviewOpinion = reviewOpinion;
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
        return "WorkOrder{" +
            "id = " + id +
            ", anomalyEventId = " + anomalyEventId +
            ", feedbackId = " + feedbackId +
            ", gridId = " + gridId +
            ", assigneeId = " + assigneeId +
            ", assignedBy = " + assignedBy +
            ", requirement = " + requirement +
            ", priority = " + priority +
            ", status = " + status +
            ", assignedAt = " + assignedAt +
            ", handledAt = " + handledAt +
            ", measures = " + measures +
            ", result = " + result +
            ", submittedAt = " + submittedAt +
            ", reviewedBy = " + reviewedBy +
            ", reviewedAt = " + reviewedAt +
            ", reviewOpinion = " + reviewOpinion +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
        "}";
    }
}
