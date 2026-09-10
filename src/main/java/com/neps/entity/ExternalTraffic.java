package com.neps.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 导入的交通流量数据
 * </p>
 *
 * @author neps
 * @since 2026-09-10
 */
@TableName("biz_external_traffic")
public class ExternalTraffic implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 交通记录ID
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
     * 道路编码
     */
    private String roadCode;

    /**
     * 道路名称
     */
    private String roadName;

    /**
     * 经度
     */
    private BigDecimal longitude;

    /**
     * 纬度
     */
    private BigDecimal latitude;

    /**
     * 统计时间
     */
    private LocalDateTime observedAt;

    /**
     * 每小时车辆数
     */
    private Integer trafficFlow;

    /**
     * LOW、MEDIUM或HIGH
     */
    private String congestionLevel;

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

    public String getRoadCode() {
        return roadCode;
    }

    public void setRoadCode(String roadCode) {
        this.roadCode = roadCode;
    }

    public String getRoadName() {
        return roadName;
    }

    public void setRoadName(String roadName) {
        this.roadName = roadName;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public LocalDateTime getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(LocalDateTime observedAt) {
        this.observedAt = observedAt;
    }

    public Integer getTrafficFlow() {
        return trafficFlow;
    }

    public void setTrafficFlow(Integer trafficFlow) {
        this.trafficFlow = trafficFlow;
    }

    public String getCongestionLevel() {
        return congestionLevel;
    }

    public void setCongestionLevel(String congestionLevel) {
        this.congestionLevel = congestionLevel;
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
        return "ExternalTraffic{" +
            "id = " + id +
            ", importBatchId = " + importBatchId +
            ", recordCode = " + recordCode +
            ", regionId = " + regionId +
            ", roadCode = " + roadCode +
            ", roadName = " + roadName +
            ", longitude = " + longitude +
            ", latitude = " + latitude +
            ", observedAt = " + observedAt +
            ", trafficFlow = " + trafficFlow +
            ", congestionLevel = " + congestionLevel +
            ", qualityFlag = " + qualityFlag +
            ", sourceName = " + sourceName +
            ", isDemo = " + isDemo +
            ", createdAt = " + createdAt +
        "}";
    }
}
