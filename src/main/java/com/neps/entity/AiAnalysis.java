package com.neps.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * AI分析记录
 * </p>
 *
 * @author neps
 * @since 2026-09-06
 */
@TableName("biz_ai_analysis")
public class AiAnalysis implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 分析记录ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 分析对象类型
     */
    private String targetType;

    /**
     * 分析对象ID
     */
    private Long targetId;

    /**
     * PENDING待分析，RUNNING分析中，SUCCEEDED成功，FAILED失败
     */
    private String status;

    /**
     * 分析结果
     */
    private String resultText;

    /**
     * 失败原因
     */
    private String failureReason;

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

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResultText() {
        return resultText;
    }

    public void setResultText(String resultText) {
        this.resultText = resultText;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
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
        return "AiAnalysis{" +
            "id = " + id +
            ", targetType = " + targetType +
            ", targetId = " + targetId +
            ", status = " + status +
            ", resultText = " + resultText +
            ", failureReason = " + failureReason +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
        "}";
    }
}
