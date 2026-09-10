package com.neps.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 导入的企业排污数据
 * </p>
 *
 * @author neps
 * @since 2026-09-10
 */
@TableName("biz_external_emission")
public class ExternalEmission implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 企业排污记录ID
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
     * 企业编码
     */
    private String enterpriseCode;

    /**
     * 企业名称
     */
    private String enterpriseName;

    /**
     * 经度
     */
    private BigDecimal longitude;

    /**
     * 纬度
     */
    private BigDecimal latitude;

    /**
     * 监测时间
     */
    private LocalDateTime observedAt;

    /**
     * 污染物编码
     */
    private String pollutantCode;

    /**
     * 排放量
     */
    private BigDecimal emissionValue;

    /**
     * 排放量单位
     */
    private String emissionUnit;

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

    public String getEnterpriseCode() {
        return enterpriseCode;
    }

    public void setEnterpriseCode(String enterpriseCode) {
        this.enterpriseCode = enterpriseCode;
    }

    public String getEnterpriseName() {
        return enterpriseName;
    }

    public void setEnterpriseName(String enterpriseName) {
        this.enterpriseName = enterpriseName;
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

    public String getPollutantCode() {
        return pollutantCode;
    }

    public void setPollutantCode(String pollutantCode) {
        this.pollutantCode = pollutantCode;
    }

    public BigDecimal getEmissionValue() {
        return emissionValue;
    }

    public void setEmissionValue(BigDecimal emissionValue) {
        this.emissionValue = emissionValue;
    }

    public String getEmissionUnit() {
        return emissionUnit;
    }

    public void setEmissionUnit(String emissionUnit) {
        this.emissionUnit = emissionUnit;
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
        return "ExternalEmission{" +
            "id = " + id +
            ", importBatchId = " + importBatchId +
            ", recordCode = " + recordCode +
            ", regionId = " + regionId +
            ", enterpriseCode = " + enterpriseCode +
            ", enterpriseName = " + enterpriseName +
            ", longitude = " + longitude +
            ", latitude = " + latitude +
            ", observedAt = " + observedAt +
            ", pollutantCode = " + pollutantCode +
            ", emissionValue = " + emissionValue +
            ", emissionUnit = " + emissionUnit +
            ", qualityFlag = " + qualityFlag +
            ", sourceName = " + sourceName +
            ", isDemo = " + isDemo +
            ", createdAt = " + createdAt +
        "}";
    }
}
