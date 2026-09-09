package com.neps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neps.dto.MeasurementResponse;
import com.neps.dto.MeasurementReviewResponse;
import com.neps.dto.ReviewMeasurementRequest;
import com.neps.dto.SubmitMeasurementRequest;
import com.neps.entity.Measurement;

import java.util.List;

public interface MeasurementService
        extends IService<Measurement> {

    MeasurementResponse submit(
            Long taskId,
            SubmitMeasurementRequest request);

    List<MeasurementResponse> listForAdmin(
            String reviewStatus);

    MeasurementResponse getForAdmin(
            Long measurementId);

    MeasurementReviewResponse review(
            Long measurementId,
            ReviewMeasurementRequest request);
}
