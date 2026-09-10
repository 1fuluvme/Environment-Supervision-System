package com.neps.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 外部数据导入批次
 * </p>
 *
 * @author neps
 * @since 2026-09-10
 */
@TableName("biz_import_batch")
public class ImportBatch implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 导入批次ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 批次编号
     */
    private String batchNo;

    /**
     * 数据类型，例如AQI
     */
    private String dataType;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 文件SHA-256
     */
    private String fileHash;

    /**
     * 成功导入记录数
     */
    private Integer recordCount;

    /**
     * 数据来源
     */
    private String sourceName;

    /**
     * 是否演示数据
     */
    private Byte isDemo;

    /**
     * 导入管理员ID
     */
    private Long importedBy;

    /**
     * 导入时间
     */
    private LocalDateTime importedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public Integer getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Integer recordCount) {
        this.recordCount = recordCount;
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

    public Long getImportedBy() {
        return importedBy;
    }

    public void setImportedBy(Long importedBy) {
        this.importedBy = importedBy;
    }

    public LocalDateTime getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(LocalDateTime importedAt) {
        this.importedAt = importedAt;
    }

    @Override
    public String toString() {
        return "ImportBatch{" +
            "id = " + id +
            ", batchNo = " + batchNo +
            ", dataType = " + dataType +
            ", originalName = " + originalName +
            ", fileHash = " + fileHash +
            ", recordCount = " + recordCount +
            ", sourceName = " + sourceName +
            ", isDemo = " + isDemo +
            ", importedBy = " + importedBy +
            ", importedAt = " + importedAt +
        "}";
    }
}
