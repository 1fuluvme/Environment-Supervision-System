package com.neps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neps.dto.AssignWorkOrderRequest;
import com.neps.dto.WorkOrderResponse;
import com.neps.entity.WorkOrder;
import com.neps.dto.SubmitWorkOrderResultRequest;
import com.neps.dto.ReviewWorkOrderRequest;

import java.util.List;

public interface WorkOrderService
        extends IService<WorkOrder> {

    List<WorkOrderResponse> listForAdmin(
            String status,
            String priority);

    WorkOrderResponse getForAdmin(
            Long workOrderId);

    WorkOrderResponse assign(
            Long workOrderId,
            AssignWorkOrderRequest request);

    List<WorkOrderResponse> listMine();

    WorkOrderResponse getMine(
            Long workOrderId);

    WorkOrderResponse submitResult(
            Long workOrderId,
            SubmitWorkOrderResultRequest request);

    WorkOrderResponse review(
            Long workOrderId,
            ReviewWorkOrderRequest request);
}
