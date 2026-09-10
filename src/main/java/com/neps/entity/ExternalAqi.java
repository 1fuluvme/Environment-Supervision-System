package com.neps.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 导入的历史AQI数据
 * </p>
 *
 * @author neps
 * @since 2026-09-10
 */
@TableName("biz_external_aqi")
public class ExternalAqi implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 历史AQI记录ID
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
     * 区域ID
     */
    private Long regionId;

    /**
     * 监测时间
     */
    private LocalDateTime observedAt;

    /**
     * DAILY或REALTIME
     */
    private String reportType;

    /**
     * AQI，0至500
     */
    private Short aqi;

    /**
     * AQI级别，1至6
     */
    private Byte aqiLevel;

    /**
     * AQI类别
     */
    private String aqiCategory;

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

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public Short getAqi() {
        return aqi;
    }

    public void setAqi(Short aqi) {
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
        return "ExternalAqi{" +
            "id = " + id +
            ", importBatchId = " + importBatchId +
            ", recordCode = " + recordCode +
            ", regionId = " + regionId +
            ", observedAt = " + observedAt +
            ", reportType = " + reportType +
            ", aqi = " + aqi +
            ", aqiLevel = " + aqiLevel +
            ", aqiCategory = " + aqiCategory +
            ", qualityFlag = " + qualityFlag +
            ", sourceName = " + sourceName +
            ", isDemo = " + isDemo +
            ", createdAt = " + createdAt +
        "}";
    }
}
