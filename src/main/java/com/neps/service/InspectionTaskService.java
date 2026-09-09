package com.neps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neps.dto.AssignInspectionTaskRequest;
import com.neps.dto.InspectionTaskResponse;
import com.neps.entity.InspectionTask;

import java.util.List;

public interface InspectionTaskService
        extends IService<InspectionTask> {

    InspectionTaskResponse assign(
            Long feedbackId,
            AssignInspectionTaskRequest request);

    List<InspectionTaskResponse> listMine();
}
