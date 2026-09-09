package com.neps.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 网格员检测记录
 * </p>
 *
 * @author neps
 * @since 2026-09-08
 */
@TableName("biz_measurement")
public class Measurement implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 检测记录ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 核查任务ID
     */
    private Long taskId;

    /**
     * 公众反馈ID
     */
    private Long feedbackId;

    /**
     * 提交检测的网格员ID
     */
    private Long submitterId;

    /**
     * 同一任务的提交版本号
     */
    private Integer versionNo;

    /**
     * 检测时间
     */
    private LocalDateTime measuredAt;

    /**
     * 检测位置
     */
    private String location;

    /**
     * INSTANT瞬时、REALTIME实时报、DAILY日报
     */
    private String reportType;

    /**
     * 数据来源
     */
    private String dataSource;

    /**
     * SO2，单位μg/m³
     */
    private BigDecimal so2;

    /**
     * NO2，单位μg/m³
     */
    private BigDecimal no2;

    /**
     * CO，单位mg/m³
     */
    private BigDecimal co;

    /**
     * O3，单位μg/m³
     */
    private BigDecimal o3;

    /**
     * PM10，单位μg/m³
     */
    private BigDecimal pm10;

    /**
     * PM2.5，单位μg/m³
     */
    private BigDecimal pm25;

    /**
     * 缺测原因
     */
    private String missingReason;

    /**
     * 是否满足统计有效性
     */
    private Byte statisticallyValid;

    /**
     * 统计无效原因
     */
    private String invalidReason;

    /**
     * VALID或INVALID
     */
    private String qualityFlag;

    /**
     * SO2分指数
     */
    private Integer so2Iaqi;

    /**
     * NO2分指数
     */
    private Integer no2Iaqi;

    /**
     * CO分指数
     */
    private Integer coIaqi;

    /**
     * O3分指数
     */
    private Integer o3Iaqi;

    /**
     * PM10分指数
     */
    private Integer pm10Iaqi;

    /**
     * PM2.5分指数
     */
    private Integer pm25Iaqi;

    /**
     * AQI是否可计算
     */
    private Byte aqiCalculable;

    /**
     * AQI
     */
    private Integer aqi;

    /**
     * AQI级别1至6
     */
    private Byte aqiLevel;

    /**
     * 优、良等类别
     */
    private String aqiCategory;

    /**
     * 并列首要污染物
     */
    private String primaryPollutants;

    /**
     * 不能计算AQI的原因
     */
    private String calculationReason;

    private String standardVersion;

    /**
     * 现场情况说明
     */
    private String siteNote;

    /**
     * PENDING待复核、APPROVED通过、RETURNED退回
     */
    private String reviewStatus;

    private LocalDateTime submittedAt;

    private LocalDateTime updatedAt;

    private String ruleStatus;

    private String ruleReason;

    private String suggestedPriority;

    private String ruleVersion;

    private LocalDateTime ruleEvaluatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getFeedbackId() {
        return feedbackId;
    }

    public void setFeedbackId(Long feedbackId) {
        this.feedbackId = feedbackId;
    }

    public Long getSubmitterId() {
        return submitterId;
    }

    public void setSubmitterId(Long submitterId) {
        this.submitterId = submitterId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public LocalDateTime getMeasuredAt() {
        return measuredAt;
    }

    public void setMeasuredAt(LocalDateTime measuredAt) {
        this.measuredAt = measuredAt;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public BigDecimal getSo2() {
        return so2;
    }

    public void setSo2(BigDecimal so2) {
        this.so2 = so2;
    }

    public BigDecimal getNo2() {
        return no2;
    }

    public void setNo2(BigDecimal no2) {
        this.no2 = no2;
    }

    public BigDecimal getCo() {
        return co;
    }

    public void setCo(BigDecimal co) {
        this.co = co;
    }

    public BigDecimal geto3() {
        return o3;
    }

    public void seto3(BigDecimal o3) {
        this.o3 = o3;
    }

    public BigDecimal getPm10() {
        return pm10;
    }

    public void setPm10(BigDecimal pm10) {
        this.pm10 = pm10;
    }

    public BigDecimal getPm25() {
        return pm25;
    }

    public void setPm25(BigDecimal pm25) {
        this.pm25 = pm25;
    }

    public String getMissingReason() {
        return missingReason;
    }

    public void setMissingReason(String missingReason) {
        this.missingReason = missingReason;
    }

    public Byte getStatisticallyValid() {
        return statisticallyValid;
    }

    public void setStatisticallyValid(Byte statisticallyValid) {
        this.statisticallyValid = statisticallyValid;
    }

    public String getInvalidReason() {
        return invalidReason;
    }

    public void setInvalidReason(String invalidReason) {
        this.invalidReason = invalidReason;
    }

    public String getQualityFlag() {
        return qualityFlag;
    }

    public void setQualityFlag(String qualityFlag) {
        this.qualityFlag = qualityFlag;
    }

    public Integer getSo2Iaqi() {
        return so2Iaqi;
    }

    public void setSo2Iaqi(Integer so2Iaqi) {
        this.so2Iaqi = so2Iaqi;
    }

    public Integer getNo2Iaqi() {
        return no2Iaqi;
    }

    public void setNo2Iaqi(Integer no2Iaqi) {
        this.no2Iaqi = no2Iaqi;
    }

    public Integer getCoIaqi() {
        return coIaqi;
    }

    public void setCoIaqi(Integer coIaqi) {
        this.coIaqi = coIaqi;
    }

    public Integer geto3Iaqi() {
        return o3Iaqi;
    }

    public void seto3Iaqi(Integer o3Iaqi) {
        this.o3Iaqi = o3Iaqi;
    }

    public Integer getPm10Iaqi() {
        return pm10Iaqi;
    }

    public void setPm10Iaqi(Integer pm10Iaqi) {
        this.pm10Iaqi = pm10Iaqi;
    }

    public Integer getPm25Iaqi() {
        return pm25Iaqi;
    }

    public void setPm25Iaqi(Integer pm25Iaqi) {
        this.pm25Iaqi = pm25Iaqi;
    }

    public Byte getAqiCalculable() {
        return aqiCalculable;
    }

    public void setAqiCalculable(Byte aqiCalculable) {
        this.aqiCalculable = aqiCalculable;
    }

    public Integer getAqi() {
        return aqi;
    }

    public void setAqi(Integer aqi) {
        this.aqi = aqi;
    }

    public Byte getAqiLevel() {
        return aqiLevel;
    }

    public void setAqiLevel(Byte aqiLevel) {
        this.aqiLevel = aqiLevel;
    }

    public String getAqiCategory() {
        return aqiCategory;
    }

    public void setAqiCategory(String aqiCategory) {
        this.aqiCategory = aqiCategory;
    }

    public String getPrimaryPollutants() {
        return primaryPollutants;
    }

    public void setPrimaryPollutants(String primaryPollutants) {
        this.primaryPollutants = primaryPollutants;
    }

    public String getCalculationReason() {
        return calculationReason;
    }

    public void setCalculationReason(String calculationReason) {
        this.calculationReason = calculationReason;
    }

    public String getStandardVersion() {
        return standardVersion;
    }

    public void setStandardVersion(String standardVersion) {
        this.standardVersion = standardVersion;
    }

    public String getSiteNote() {
        return siteNote;
    }

    public void setSiteNote(String siteNote) {
        this.siteNote = siteNote;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getRuleStatus() {
        return ruleStatus;
    }

    public void setRuleStatus(String ruleStatus) {
        this.ruleStatus = ruleStatus;
    }

    public String getRuleReason() {
        return ruleReason;
    }

    public void setRuleReason(String ruleReason) {
        this.ruleReason = ruleReason;
    }

    public String getSuggestedPriority() {
        return suggestedPriority;
    }

    public void setSuggestedPriority(
            String suggestedPriority) {
        this.suggestedPriority = suggestedPriority;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public LocalDateTime getRuleEvaluatedAt() {
        return ruleEvaluatedAt;
    }

    public void setRuleEvaluatedAt(
            LocalDateTime ruleEvaluatedAt) {
        this.ruleEvaluatedAt = ruleEvaluatedAt;
    }

    @Override
    public String toString() {
        return "Measurement{" +
            "id = " + id +
            ", taskId = " + taskId +
            ", feedbackId = " + feedbackId +
            ", submitterId = " + submitterId +
            ", versionNo = " + versionNo +
            ", measuredAt = " + measuredAt +
            ", location = " + location +
            ", reportType = " + reportType +
            ", dataSource = " + dataSource +
            ", so2 = " + so2 +
            ", no2 = " + no2 +
            ", co = " + co +
            ", o3 = " + o3 +
            ", pm10 = " + pm10 +
            ", pm25 = " + pm25 +
            ", missingReason = " + missingReason +
            ", statisticallyValid = " + statisticallyValid +
            ", invalidReason = " + invalidReason +
            ", qualityFlag = " + qualityFlag +
            ", so2Iaqi = " + so2Iaqi +
            ", no2Iaqi = " + no2Iaqi +
            ", coIaqi = " + coIaqi +
            ", o3Iaqi = " + o3Iaqi +
            ", pm10Iaqi = " + pm10Iaqi +
            ", pm25Iaqi = " + pm25Iaqi +
            ", aqiCalculable = " + aqiCalculable +
            ", aqi = " + aqi +
            ", aqiLevel = " + aqiLevel +
            ", aqiCategory = " + aqiCategory +
            ", primaryPollutants = " + primaryPollutants +
            ", calculationReason = " + calculationReason +
            ", standardVersion = " + standardVersion +
            ", siteNote = " + siteNote +
            ", reviewStatus = " + reviewStatus +
            ", submittedAt = " + submittedAt +
            ", updatedAt = " + updatedAt +
        "}";
    }
}
