package com.neps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neps.dto.PollutionTraceGenerateRequest;
import com.neps.dto.PollutionTraceResponse;
import com.neps.entity.PollutionTrace;

import java.util.List;

public interface PollutionTraceService
        extends IService<PollutionTrace> {

    PollutionTraceResponse generate(
            PollutionTraceGenerateRequest request);

    List<PollutionTraceResponse> list(
            Long anomalyEventId);

    PollutionTraceResponse get(
            Long traceId);
}
