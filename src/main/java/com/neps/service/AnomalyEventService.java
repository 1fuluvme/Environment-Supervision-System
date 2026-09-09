package com.neps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neps.dto.AnomalyEventResponse;
import com.neps.dto.ReviewAnomalyRequest;
import com.neps.entity.AnomalyEvent;

import java.util.List;

public interface AnomalyEventService
        extends IService<AnomalyEvent> {

    List<AnomalyEventResponse> listForAdmin(
            String status,
            String priority);

    AnomalyEventResponse getForAdmin(
            Long eventId);

    AnomalyEventResponse review(
            Long eventId,
            ReviewAnomalyRequest request);
}
