package com.neps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neps.dto.WarningResponse;
import com.neps.entity.Warning;

import java.util.List;

public interface WarningService
        extends IService<Warning> {

    List<WarningResponse> listForAdmin(
            String status,
            String level);

    WarningResponse getForAdmin(
            Long warningId);
}
