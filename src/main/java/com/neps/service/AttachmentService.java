package com.neps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neps.dto.AttachmentContent;
import com.neps.dto.AttachmentResponse;
import com.neps.entity.Attachment;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AttachmentService extends IService<Attachment> {

    AttachmentResponse uploadFeedbackImage(
            Long feedbackId,
            MultipartFile file);

    List<AttachmentResponse> listFeedbackImages(Long feedbackId);

    AttachmentContent getFeedbackImage(Long attachmentId);
}
