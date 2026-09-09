package com.neps.dto;

import org.springframework.core.io.Resource;

public record AttachmentContent(
        String originalName,
        String contentType,
        Long sizeBytes,
        Resource resource
) {
}