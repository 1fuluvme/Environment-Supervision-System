package com.neps.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neps.dto.AttachmentContent;
import com.neps.dto.AttachmentResponse;
import com.neps.entity.Attachment;
import com.neps.entity.Feedback;
import com.neps.entity.User;
import com.neps.mapper.AttachmentMapper;
import com.neps.mapper.FeedbackMapper;
import com.neps.mapper.UserMapper;
import com.neps.service.AttachmentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.neps.entity.Grid;
import com.neps.entity.InspectionTask;
import com.neps.mapper.GridMapper;
import com.neps.mapper.InspectionTaskMapper;
import com.neps.mapper.UserRegionMapper;

@Service
public class AttachmentServiceImpl
        extends ServiceImpl<AttachmentMapper, Attachment>
        implements AttachmentService {

    private static final long MAX_FILE_SIZE =
            5L * 1024L * 1024L;

    private static final long MAX_IMAGE_PIXELS =
            25_000_000L;

    private final UserMapper userMapper;
    private final FeedbackMapper feedbackMapper;
    private final GridMapper gridMapper;
    private final InspectionTaskMapper inspectionTaskMapper;
    private final UserRegionMapper userRegionMapper;
    private final Path uploadRoot;

    public AttachmentServiceImpl(
            UserMapper userMapper,
            FeedbackMapper feedbackMapper,
            GridMapper gridMapper,
            InspectionTaskMapper inspectionTaskMapper,
            UserRegionMapper userRegionMapper,
            @Value("${app.upload.directory}")
            String uploadDirectory) {

        this.userMapper = userMapper;
        this.feedbackMapper = feedbackMapper;
        this.gridMapper = gridMapper;
        this.inspectionTaskMapper = inspectionTaskMapper;
        this.userRegionMapper = userRegionMapper;

        this.uploadRoot = Path.of(uploadDirectory)
                .toAbsolutePath()
                .normalize();

        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "无法创建上传目录：" + uploadRoot,
                    exception);
        }
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('PUBLIC')")
    public AttachmentResponse uploadFeedbackImage(
            Long feedbackId,
            MultipartFile file) {

        User user = currentPublicUser();
        Feedback feedback = requireOwnedFeedback(feedbackId, user);

        if (!"PENDING_ASSIGN".equals(feedback.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "反馈已进入办理流程，不能继续添加图片");
        }

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "请选择图片文件");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "图片不能超过5MB");
        }

        String originalName =
                cleanOriginalName(file.getOriginalFilename());

        ImageType imageType = detectImageType(file);

        String relativePath =
                "feedback/" + UUID.randomUUID()
                        + imageType.extension();

        Path destination = resolveStoragePath(relativePath);

        try {
            Files.createDirectories(destination.getParent());

            try (InputStream input = file.getInputStream()) {
                Files.copy(input, destination);
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "图片保存失败",
                    exception);
        }

        Attachment attachment = new Attachment();
        attachment.setBusinessType("FEEDBACK");
        attachment.setBusinessId(feedback.getId());
        attachment.setUploaderId(user.getId());
        attachment.setOriginalName(originalName);
        attachment.setStoragePath(relativePath);
        attachment.setContentType(imageType.contentType());
        attachment.setSizeBytes(file.getSize());

        try {
            if (!save(attachment)) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "附件记录保存失败");
            }

            return toResponse(getById(attachment.getId()));
        } catch (RuntimeException exception) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException deleteException) {
                exception.addSuppressed(deleteException);
            }

            throw exception;
        }
    }

    @Override
    @PreAuthorize(
            "hasAnyRole('PUBLIC','ADMIN','GRID')")
    public List<AttachmentResponse> listFeedbackImages(
            Long feedbackId) {

        User user = currentUser();
        Feedback feedback =
                requireReadableFeedback(feedbackId, user);

        return lambdaQuery()
                .eq(Attachment::getBusinessType, "FEEDBACK")
                .eq(Attachment::getBusinessId, feedback.getId())
                .orderByAsc(Attachment::getId)
                .list()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @PreAuthorize(
            "hasAnyRole('PUBLIC','ADMIN','GRID')")
    public AttachmentContent getFeedbackImage(
            Long attachmentId) {

        if (attachmentId == null || attachmentId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "附件ID必须是正整数");
        }

        User user = currentUser();

        Attachment attachment = lambdaQuery()
                .eq(Attachment::getId, attachmentId)
                .eq(Attachment::getBusinessType, "FEEDBACK")
                .one();

        if (attachment == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "附件不存在");
        }

        requireReadableFeedback(
                attachment.getBusinessId(),
                user);

        Path path = resolveStoragePath(
                attachment.getStoragePath());

        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "附件文件不存在");
        }

        try {
            Resource resource =
                    new UrlResource(path.toUri());

            return new AttachmentContent(
                    attachment.getOriginalName(),
                    attachment.getContentType(),
                    attachment.getSizeBytes(),
                    resource);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "附件读取失败",
                    exception);
        }
    }

    private User currentPublicUser() {
        String phone = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getPhone, phone)
                        .eq(User::getRole, "PUBLIC")
                        .eq(User::getEnabled, 1));

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "当前公众账号不可用");
        }

        return user;
    }

    private User currentUser() {
        String phone = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getPhone, phone)
                        .eq(User::getEnabled, 1));

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "当前账号不可用");
        }

        return user;
    }

    private Feedback requireOwnedFeedback(
            Long feedbackId,
            User user) {

        if (feedbackId == null || feedbackId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "反馈ID必须是正整数");
        }

        Feedback feedback = feedbackMapper.selectOne(
                Wrappers.<Feedback>lambdaQuery()
                        .eq(Feedback::getId, feedbackId)
                        .eq(Feedback::getSubmitterId, user.getId()));

        if (feedback == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "反馈不存在");
        }

        return feedback;
    }

    private Feedback requireReadableFeedback(
            Long feedbackId,
            User user) {

        if (feedbackId == null || feedbackId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "反馈ID必须是正整数");
        }

        Feedback feedback =
                feedbackMapper.selectById(feedbackId);

        if (feedback == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "反馈不存在");
        }

        switch (user.getRole()) {
            case "PUBLIC" -> {
                if (!user.getId().equals(
                        feedback.getSubmitterId())) {

                    // 不向其他公众暴露反馈是否存在。
                    throw new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "反馈不存在");
                }
            }

            case "ADMIN" -> {
                Grid grid = gridMapper.selectById(
                        feedback.getGridId());

                if (grid == null) {
                    throw new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "反馈关联的网格不存在");
                }

                if (userRegionMapper.countAccessibleRegion(
                        user.getId(),
                        grid.getRegionId()) == 0) {

                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "反馈不在当前管理员授权范围内");
                }
            }

            case "GRID" -> {
                Long taskCount =
                        inspectionTaskMapper.selectCount(
                                Wrappers
                                        .<InspectionTask>lambdaQuery()
                                        .eq(
                                                InspectionTask::getFeedbackId,
                                                feedback.getId())
                                        .eq(
                                                InspectionTask::getAssigneeId,
                                                user.getId()));

                if (taskCount == null || taskCount == 0) {
                    // 不向非负责人暴露反馈是否有附件。
                    throw new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "反馈不存在");
                }
            }

            default -> throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "当前角色不能读取反馈附件");
        }

        return feedback;
    }

    private ImageType detectImageType(MultipartFile file) {
        try (InputStream input = file.getInputStream();
             ImageInputStream imageInput =
                     ImageIO.createImageInputStream(input)) {

            if (imageInput == null) {
                throw invalidImage();
            }

            Iterator<ImageReader> readers =
                    ImageIO.getImageReaders(imageInput);

            if (!readers.hasNext()) {
                throw invalidImage();
            }

            ImageReader reader = readers.next();

            try {
                reader.setInput(imageInput, true, true);

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width <= 0
                        || height <= 0
                        || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "图片尺寸无效或像素过大");
                }

                String format = reader.getFormatName()
                        .toUpperCase(Locale.ROOT);

                return switch (format) {
                    case "JPEG", "JPG" ->
                            new ImageType(".jpg", "image/jpeg");
                    case "PNG" ->
                            new ImageType(".png", "image/png");
                    default -> throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "只支持JPEG和PNG图片");
                };
            } finally {
                reader.dispose();
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalidImage();
        }
    }

    private ResponseStatusException invalidImage() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "文件内容不是有效的JPEG或PNG图片");
    }

    private String cleanOriginalName(String suppliedName) {
        String name = suppliedName == null
                ? ""
                : suppliedName.trim();

        int separator = Math.max(
                name.lastIndexOf('/'),
                name.lastIndexOf('\\'));

        if (separator >= 0) {
            name = name.substring(separator + 1);
        }

        if (name.isBlank()
                || name.length() > 255
                || name.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "图片文件名无效");
        }

        return name;
    }

    private Path resolveStoragePath(String relativePath) {
        Path resolved = uploadRoot
                .resolve(relativePath)
                .normalize();

        if (!resolved.startsWith(uploadRoot)) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "附件存储路径无效");
        }

        return resolved;
    }

    private AttachmentResponse toResponse(
            Attachment attachment) {

        return new AttachmentResponse(
                attachment.getId(),
                attachment.getOriginalName(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getCreatedAt(),
                "/api/attachments/"
                        + attachment.getId()
                        + "/content");
    }

    private record ImageType(
            String extension,
            String contentType) {
    }
}
