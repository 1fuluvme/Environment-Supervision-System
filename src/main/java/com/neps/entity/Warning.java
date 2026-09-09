package com.neps.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 内部污染预警
 * </p>
 *
 * @author neps
 * @since 2026-09-09
 */
@TableName("biz_warning")
public class Warning implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 预警ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 异常事件ID
     */
    private Long anomalyEventId;

    /**
     * 所属网格ID
     */
    private Long gridId;

    /**
     * LOW、MEDIUM、HIGH
     */
    private String warningLevel;

    /**
     * 预警标题
     */
    private String title;

    /**
     * 预警内容
     */
    private String content;

    /**
     * ACTIVE生效中、CLOSED已关闭
     */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime closedAt;

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

    public Long getGridId() {
        return gridId;
    }

    public void setGridId(Long gridId) {
        this.gridId = gridId;
    }

    public String getWarningLevel() {
        return warningLevel;
    }

    public void setWarningLevel(String warningLevel) {
        this.warningLevel = warningLevel;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    @Override
    public String toString() {
        return "Warning{" +
            "id = " + id +
            ", anomalyEventId = " + anomalyEventId +
            ", gridId = " + gridId +
            ", warningLevel = " + warningLevel +
            ", title = " + title +
            ", content = " + content +
            ", status = " + status +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", closedAt = " + closedAt +
        "}";
    }
}
