package com.neps.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 处置工单复核历史
 * </p>
 *
 * @author neps
 * @since 2026-09-09
 */
@TableName("biz_work_order_review")
public class WorkOrderReview implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工单复核记录ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 处置工单ID
     */
    private Long workOrderId;

    /**
     * 复核管理员ID
     */
    private Long reviewerId;

    /**
     * RETURN退回、CLOSE关闭
     */
    private String decision;

    /**
     * 本次处置时间快照
     */
    private LocalDateTime handledAt;

    /**
     * 本次处置措施快照
     */
    private String measures;

    /**
     * 本次处置结果快照
     */
    private String result;

    /**
     * 复核意见
     */
    private String opinion;

    /**
     * 公众办理说明
     */
    private String publicReply;

    /**
     * 复核时间
     */
    private LocalDateTime reviewedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWorkOrderId() {
        return workOrderId;
    }

    public void setWorkOrderId(Long workOrderId) {
        this.workOrderId = workOrderId;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
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

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    public String getPublicReply() {
        return publicReply;
    }

    public void setPublicReply(String publicReply) {
        this.publicReply = publicReply;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    @Override
    public String toString() {
        return "WorkOrderReview{" +
            "id = " + id +
            ", workOrderId = " + workOrderId +
            ", reviewerId = " + reviewerId +
            ", decision = " + decision +
            ", handledAt = " + handledAt +
            ", measures = " + measures +
            ", result = " + result +
            ", opinion = " + opinion +
            ", publicReply = " + publicReply +
            ", reviewedAt = " + reviewedAt +
        "}";
    }
}
