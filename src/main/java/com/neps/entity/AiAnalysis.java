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
     * AI服务来源
     */
    private String provider;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 本次分析使用的输入快照
     */
    private String inputSnapshot;

    /**
     * 是否为演示结果
     */
    private Byte isDemo;

    /**
     * 尝试次数
     */
    private Integer attemptCount;

    /**
     * 开始分析时间
     */
    private LocalDateTime startedAt;

    /**
     * 完成分析时间
     */
    private LocalDateTime completedAt;

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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getInputSnapshot() {
        return inputSnapshot;
    }

    public void setInputSnapshot(String inputSnapshot) {
        this.inputSnapshot = inputSnapshot;
    }

    public Byte getIsDemo() {
        return isDemo;
    }

    public void setIsDemo(Byte isDemo) {
        this.isDemo = isDemo;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
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
