package com.neps.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 导入的气象观测数据
 * </p>
 *
 * @author neps
 * @since 2026-09-10
 */
@TableName("biz_external_weather")
public class ExternalWeather implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 气象记录ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 导入批次ID
     */
    private Long importBatchId;

    /**
     * 来源方记录标识
     */
    private String recordCode;

    /**
     * 所属区域ID
     */
    private Long regionId;

    /**
     * 观测时间
     */
    private LocalDateTime observedAt;

    /**
     * 风向角度，0至360度
     */
    private BigDecimal windDirection;

    /**
     * 风速，单位m/s
     */
    private BigDecimal windSpeed;

    /**
     * 温度，单位摄氏度
     */
    private BigDecimal temperature;

    /**
     * 相对湿度，单位百分比
     */
    private BigDecimal humidity;

    /**
     * VALID或INVALID
     */
    private String qualityFlag;

    /**
     * 数据来源
     */
    private String sourceName;

    /**
     * 是否演示数据
     */
    private Byte isDemo;

    /**
     * 保存时间
     */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getImportBatchId() {
        return importBatchId;
    }

    public void setImportBatchId(Long importBatchId) {
        this.importBatchId = importBatchId;
    }

    public String getRecordCode() {
        return recordCode;
    }

    public void setRecordCode(String recordCode) {
        this.recordCode = recordCode;
    }

    public Long getRegionId() {
        return regionId;
    }

    public void setRegionId(Long regionId) {
        this.regionId = regionId;
    }

    public LocalDateTime getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(LocalDateTime observedAt) {
        this.observedAt = observedAt;
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

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public BigDecimal getHumidity() {
        return humidity;
    }

    public void setHumidity(BigDecimal humidity) {
        this.humidity = humidity;
    }

    public String getQualityFlag() {
        return qualityFlag;
    }

    public void setQualityFlag(String qualityFlag) {
        this.qualityFlag = qualityFlag;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "ExternalWeather{" +
            "id = " + id +
            ", importBatchId = " + importBatchId +
            ", recordCode = " + recordCode +
            ", regionId = " + regionId +
            ", observedAt = " + observedAt +
            ", windDirection = " + windDirection +
            ", windSpeed = " + windSpeed +
            ", temperature = " + temperature +
            ", humidity = " + humidity +
            ", qualityFlag = " + qualityFlag +
            ", sourceName = " + sourceName +
            ", isDemo = " + isDemo +
            ", createdAt = " + createdAt +
        "}";
    }
}
