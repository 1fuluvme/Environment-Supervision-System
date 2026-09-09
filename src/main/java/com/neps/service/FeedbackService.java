package com.neps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neps.dto.CreateFeedbackRequest;
import com.neps.dto.FeedbackResponse;
import com.neps.entity.Feedback;
import com.neps.dto.AdminFeedbackResponse;
import java.time.LocalDateTime;

import java.util.List;

public interface FeedbackService extends IService<Feedback> {

    FeedbackResponse createFeedback(CreateFeedbackRequest request);

    List<FeedbackResponse> listMine();

    FeedbackResponse getMine(Long id);

    List<AdminFeedbackResponse> listForAdmin(
            String status,
            Long regionId,
            LocalDateTime from,
            LocalDateTime to);

    AdminFeedbackResponse getForAdmin(Long id);
}
