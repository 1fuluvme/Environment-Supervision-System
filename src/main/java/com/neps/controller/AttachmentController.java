package com.neps.controller;

import com.neps.dto.AttachmentContent;
import com.neps.dto.AttachmentResponse;
import com.neps.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "反馈附件")
@RestController
@RequestMapping("/api")
public class AttachmentController {

    private final AttachmentService attachmentService;


    public AttachmentController(
            AttachmentService attachmentService) {

        this.attachmentService = attachmentService;
    }

    @Operation(summary = "上传反馈图片")
    @PostMapping(
            value = "/feedbacks/{feedbackId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse upload(
            @PathVariable("feedbackId") Long feedbackId,
            @RequestPart("file") MultipartFile file) {

        return attachmentService.uploadFeedbackImage(
                feedbackId,
                file);
    }

    @Operation(summary = "查询反馈图片列表")
    @GetMapping("/feedbacks/{feedbackId}/attachments")
    public List<AttachmentResponse> list(
            @PathVariable("feedbackId") Long feedbackId) {

        return attachmentService.listFeedbackImages(feedbackId);
    }

    @Operation(summary = "下载反馈图片")
    @GetMapping("/attachments/{attachmentId}/content")
    public ResponseEntity<?> content(
            @PathVariable("attachmentId") Long attachmentId) {

        AttachmentContent content =
                attachmentService.getFeedbackImage(attachmentId);

        ContentDisposition disposition =
                ContentDisposition.attachment()
                        .filename(
                                content.originalName(),
                                StandardCharsets.UTF_8)
                        .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        content.contentType()))
                .contentLength(content.sizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString())
                .body(content.resource());
    }
}