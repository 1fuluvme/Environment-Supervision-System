package com.neps.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 业务附件
 * </p>
 *
 * @author neps
 * @since 2026-09-07
 */
@TableName("biz_attachment")
public class Attachment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 附件ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务类型，例如FEEDBACK
     */
    private String businessType;

    /**
     * 所属业务记录ID
     */
    private Long businessId;

    /**
     * 上传用户ID
     */
    private Long uploaderId;

    /**
     * 用户上传时的文件名
     */
    private String originalName;

    /**
     * 服务器中的相对存储路径
     */
    private String storagePath;

    /**
     * 后端检测出的文件类型
     */
    private String contentType;

    /**
     * 文件字节数
     */
    private Long sizeBytes;

    /**
     * 上传时间
     */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public Long getBusinessId() {
        return businessId;
    }

    public void setBusinessId(Long businessId) {
        this.businessId = businessId;
    }

    public Long getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(Long uploaderId) {
        this.uploaderId = uploaderId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Attachment{" +
            "id = " + id +
            ", businessType = " + businessType +
            ", businessId = " + businessId +
            ", uploaderId = " + uploaderId +
            ", originalName = " + originalName +
            ", storagePath = " + storagePath +
            ", contentType = " + contentType +
            ", sizeBytes = " + sizeBytes +
            ", createdAt = " + createdAt +
        "}";
    }
}
