package com.neps.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * <p>
 * 区域次日AQI预测结果
 * </p>
 *
 * @author neps
 * @since 2026-09-10
 */
@TableName("biz_aqi_prediction")
public class AqiPrediction implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 预测记录ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 预测区域ID
     */
    private Long regionId;

    /**
     * 历史数据开始日期
     */
    private LocalDate historyStartDate;

    /**
     * 历史数据结束日期
     */
    private LocalDate historyEndDate;

    /**
     * 预测目标日期
     */
    private LocalDate targetDate;

    /**
     * 预测方法，例如MA7
     */
    private String method;

    /**
     * 参与计算的有效日数
     */
    private Integer sampleCount;

    /**
     * 预测AQI
     */
    private Short predictedAqi;

    /**
     * 目标日期实际AQI
     */
    private Short actualAqi;

    /**
     * 预测绝对误差
     */
    private BigDecimal absoluteError;

    /**
     * 参与计算的AQI记录ID
     */
    private String inputRecordIds;

    /**
     * 输入数据来源
     */
    private String sourceName;

    /**
     * 是否演示数据
     */
    private Byte isDemo;

    /**
     * 生成人员ID
     */
    private Long generatedBy;

    /**
     * 生成时间
     */
    private LocalDateTime generatedAt;

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

    public LocalDate getHistoryStartDate() {
        return historyStartDate;
    }

    public void setHistoryStartDate(LocalDate historyStartDate) {
        this.historyStartDate = historyStartDate;
    }

    public LocalDate getHistoryEndDate() {
        return historyEndDate;
    }

    public void setHistoryEndDate(LocalDate historyEndDate) {
        this.historyEndDate = historyEndDate;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Integer getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(Integer sampleCount) {
        this.sampleCount = sampleCount;
    }

    public Short getPredictedAqi() {
        return predictedAqi;
    }

    public void setPredictedAqi(Short predictedAqi) {
        this.predictedAqi = predictedAqi;
    }

    public Short getActualAqi() {
        return actualAqi;
    }

    public void setActualAqi(Short actualAqi) {
        this.actualAqi = actualAqi;
    }

    public BigDecimal getAbsoluteError() {
        return absoluteError;
    }

    public void setAbsoluteError(BigDecimal absoluteError) {
        this.absoluteError = absoluteError;
    }

    public String getInputRecordIds() {
        return inputRecordIds;
    }

    public void setInputRecordIds(String inputRecordIds) {
        this.inputRecordIds = inputRecordIds;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public Byte getIsDemo() {
        return isDemo;
    }

    public void setIsDemo(Byte isDemo) {
        this.isDemo = isDemo;
    }

    public Long getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(Long generatedBy) {
        this.generatedBy = generatedBy;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    @Override
    public String toString() {
        return "AqiPrediction{" +
            "id = " + id +
            ", regionId = " + regionId +
            ", historyStartDate = " + historyStartDate +
            ", historyEndDate = " + historyEndDate +
            ", targetDate = " + targetDate +
            ", method = " + method +
            ", sampleCount = " + sampleCount +
            ", predictedAqi = " + predictedAqi +
            ", actualAqi = " + actualAqi +
            ", absoluteError = " + absoluteError +
            ", inputRecordIds = " + inputRecordIds +
            ", sourceName = " + sourceName +
            ", isDemo = " + isDemo +
            ", generatedBy = " + generatedBy +
            ", generatedAt = " + generatedAt +
        "}";
    }
}
