package com.neps.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 疑似污染溯源结果
 * </p>
 *
 * @author neps
 * @since 2026-09-10
 */
@TableName("biz_pollution_trace")
public class PollutionTrace implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 疑似溯源结果ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 异常事件ID
     */
    private Long anomalyEventId;

    /**
     * 异常位置经度
     */
    private BigDecimal targetLongitude;

    /**
     * 异常位置纬度
     */
    private BigDecimal targetLatitude;

    /**
     * 异常观测时间
     */
    private LocalDateTime eventObservedAt;

    /**
     * 搜索窗口开始时间
     */
    private LocalDateTime windowStart;

    /**
     * 搜索窗口结束时间
     */
    private LocalDateTime windowEnd;

    /**
     * 前后搜索小时数
     */
    private Integer timeWindowHours;

    /**
     * 最大搜索距离km
     */
    private BigDecimal maxDistanceKm;

    /**
     * 风向容差角度
     */
    private BigDecimal directionToleranceDeg;

    /**
     * 使用的气象记录ID
     */
    private Long weatherRecordId;

    /**
     * 使用的风向角度
     */
    private BigDecimal windDirection;

    /**
     * 使用的风速m/s
     */
    private BigDecimal windSpeed;

    /**
     * 参与筛选的排污记录ID
     */
    private String emissionRecordIds;

    /**
     * 候选来源JSON快照
     */
    private String candidateSnapshot;

    /**
     * SUCCEEDED或INSUFFICIENT
     */
    private String status;

    /**
     * 资料不足原因
     */
    private String failureReason;

    /**
     * 溯源规则版本
     */
    private String methodVersion;

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

    public Long getAnomalyEventId() {
        return anomalyEventId;
    }

    public void setAnomalyEventId(Long anomalyEventId) {
        this.anomalyEventId = anomalyEventId;
    }

    public BigDecimal getTargetLongitude() {
        return targetLongitude;
    }

    public void setTargetLongitude(BigDecimal targetLongitude) {
        this.targetLongitude = targetLongitude;
    }

    public BigDecimal getTargetLatitude() {
        return targetLatitude;
    }

    public void setTargetLatitude(BigDecimal targetLatitude) {
        this.targetLatitude = targetLatitude;
    }

    public LocalDateTime getEventObservedAt() {
        return eventObservedAt;
    }

    public void setEventObservedAt(LocalDateTime eventObservedAt) {
        this.eventObservedAt = eventObservedAt;
    }

    public LocalDateTime getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(LocalDateTime windowStart) {
        this.windowStart = windowStart;
    }

    public LocalDateTime getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(LocalDateTime windowEnd) {
        this.windowEnd = windowEnd;
    }

    public Integer getTimeWindowHours() {
        return timeWindowHours;
    }

    public void setTimeWindowHours(Integer timeWindowHours) {
        this.timeWindowHours = timeWindowHours;
    }

    public BigDecimal getMaxDistanceKm() {
        return maxDistanceKm;
    }

    public void setMaxDistanceKm(BigDecimal maxDistanceKm) {
        this.maxDistanceKm = maxDistanceKm;
    }

    public BigDecimal getDirectionToleranceDeg() {
        return directionToleranceDeg;
    }

    public void setDirectionToleranceDeg(BigDecimal directionToleranceDeg) {
        this.directionToleranceDeg = directionToleranceDeg;
    }

    public Long getWeatherRecordId() {
        return weatherRecordId;
    }

    public void setWeatherRecordId(Long weatherRecordId) {
        this.weatherRecordId = weatherRecordId;
    }

    public BigDecimal getWindDirection() {
        return windDirection;
    }

    public void setWindDirection(BigDecimal windDirection) {
        this.windDirection = windDirection;
    }

    public BigDecimal getWindSpeed() {
        return windSpeed;
    }

    public void setWindSpeed(BigDecimal windSpeed) {
        this.windSpeed = windSpeed;
    }

    public String getEmissionRecordIds() {
        return emissionRecordIds;
    }

    public void setEmissionRecordIds(String emissionRecordIds) {
        this.emissionRecordIds = emissionRecordIds;
    }

    public String getCandidateSnapshot() {
        return candidateSnapshot;
    }

    public void setCandidateSnapshot(String candidateSnapshot) {
        this.candidateSnapshot = candidateSnapshot;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getMethodVersion() {
        return methodVersion;
    }

    public void setMethodVersion(String methodVersion) {
        this.methodVersion = methodVersion;
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
        return "PollutionTrace{" +
            "id = " + id +
            ", anomalyEventId = " + anomalyEventId +
            ", targetLongitude = " + targetLongitude +
            ", targetLatitude = " + targetLatitude +
            ", eventObservedAt = " + eventObservedAt +
            ", windowStart = " + windowStart +
            ", windowEnd = " + windowEnd +
            ", timeWindowHours = " + timeWindowHours +
            ", maxDistanceKm = " + maxDistanceKm +
            ", directionToleranceDeg = " + directionToleranceDeg +
            ", weatherRecordId = " + weatherRecordId +
            ", windDirection = " + windDirection +
            ", windSpeed = " + windSpeed +
            ", emissionRecordIds = " + emissionRecordIds +
            ", candidateSnapshot = " + candidateSnapshot +
            ", status = " + status +
            ", failureReason = " + failureReason +
            ", methodVersion = " + methodVersion +
            ", isDemo = " + isDemo +
            ", generatedBy = " + generatedBy +
            ", generatedAt = " + generatedAt +
        "}";
    }
}
