package com.neps.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neps.aqi.AqiCalculator;
import com.neps.dto.MeasurementResponse;
import com.neps.dto.SubmitMeasurementRequest;
import com.neps.entity.AiAnalysis;
import com.neps.entity.Feedback;
import com.neps.entity.InspectionTask;
import com.neps.entity.Measurement;
import com.neps.entity.OperationLog;
import com.neps.entity.User;
import com.neps.mapper.AiAnalysisMapper;
import com.neps.mapper.FeedbackMapper;
import com.neps.mapper.InspectionTaskMapper;
import com.neps.mapper.MeasurementMapper;
import com.neps.mapper.OperationLogMapper;
import com.neps.mapper.UserMapper;
import com.neps.service.MeasurementService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.neps.dto.MeasurementReviewResponse;
import com.neps.dto.ReviewMeasurementRequest;
import com.neps.entity.Grid;
import com.neps.entity.MeasurementReview;
import com.neps.mapper.GridMapper;
import com.neps.mapper.MeasurementReviewMapper;
import com.neps.mapper.UserRegionMapper;
import com.neps.service.AnomalyRuleService;

import java.time.LocalDateTime;
import java.util.Set;

import com.neps.entity.AnomalyEvent;
import com.neps.mapper.AnomalyEventMapper;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class MeasurementServiceImpl
        extends ServiceImpl<MeasurementMapper, Measurement>
        implements MeasurementService {

    private final UserMapper userMapper;
    private final InspectionTaskMapper inspectionTaskMapper;
    private final FeedbackMapper feedbackMapper;
    private final AiAnalysisMapper aiAnalysisMapper;
    private final OperationLogMapper operationLogMapper;
    private final GridMapper gridMapper;
    private final UserRegionMapper userRegionMapper;
    private final MeasurementReviewMapper measurementReviewMapper;
    private final AnomalyRuleService anomalyRuleService;
    private final AnomalyEventMapper anomalyEventMapper;

    public MeasurementServiceImpl(
            UserMapper userMapper,
            InspectionTaskMapper inspectionTaskMapper,
            FeedbackMapper feedbackMapper,
            AiAnalysisMapper aiAnalysisMapper,
            OperationLogMapper operationLogMapper,GridMapper gridMapper,
            UserRegionMapper userRegionMapper,
            MeasurementReviewMapper measurementReviewMapper,
            AnomalyRuleService anomalyRuleService,
            AnomalyEventMapper anomalyEventMapper) {

        this.userMapper = userMapper;
        this.inspectionTaskMapper = inspectionTaskMapper;
        this.feedbackMapper = feedbackMapper;
        this.aiAnalysisMapper = aiAnalysisMapper;
        this.operationLogMapper = operationLogMapper;
        this.gridMapper = gridMapper;
        this.userRegionMapper = userRegionMapper;
        this.measurementReviewMapper = measurementReviewMapper;
        this.anomalyRuleService = anomalyRuleService;
        this.anomalyEventMapper = anomalyEventMapper;
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('GRID')")
    public MeasurementResponse submit(
            Long taskId,
            SubmitMeasurementRequest request) {

        if (taskId == null || taskId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "任务ID必须是正整数");
        }

        User worker = currentWorker();

        InspectionTask task = inspectionTaskMapper.selectOne(
                Wrappers.<InspectionTask>lambdaQuery()
                        .eq(InspectionTask::getId, taskId)
                        .last("FOR UPDATE"));

        /*
         * 对不存在和不属于当前网格员的任务统一返回404，
         * 避免泄露其他网格员的任务信息。
         */
        if (task == null
                || !Objects.equals(
                task.getAssigneeId(), worker.getId())) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "任务不存在");
        }

        if (!"PENDING".equals(task.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前任务状态不允许提交检测");
        }

        Feedback feedback = feedbackMapper.selectOne(
                Wrappers.<Feedback>lambdaQuery()
                        .eq(Feedback::getId, task.getFeedbackId())
                        .last("FOR UPDATE"));

        if (feedback == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "任务关联的反馈不存在");
        }

        if (!"CHECKING".equals(feedback.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前反馈状态不允许提交检测");
        }

        String reportType = request.reportType()
                .trim()
                .toUpperCase(Locale.ROOT);

        boolean hasMissingValue =
                request.so2() == null
                        || request.no2() == null
                        || request.co() == null
                        || request.o3() == null
                        || request.pm10() == null
                        || request.pm25() == null;

        boolean allMissing =
                request.so2() == null
                        && request.no2() == null
                        && request.co() == null
                        && request.o3() == null
                        && request.pm10() == null
                        && request.pm25() == null;

        if (allMissing) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "至少填写一项污染物浓度");
        }

        if (hasMissingValue
                && isBlank(request.missingReason())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "存在缺测项目时必须填写缺测原因");
        }

        if (!request.statisticallyValid()
                && isBlank(request.invalidReason())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "统计数据无效时必须填写无效原因");
        }

        boolean canTryCalculate =
                !"INSTANT".equals(reportType)
                        && request.statisticallyValid()
                        && !hasMissingValue;

        AqiCalculator.Result aqiResult;

        if (canTryCalculate) {
            AqiCalculator.ReportType calculatorType =
                    AqiCalculator.ReportType.valueOf(
                            reportType);

            aqiResult = AqiCalculator.calculate(
                    new AqiCalculator.Input(
                            calculatorType,
                            true,
                            null,
                            request.so2(),
                            request.no2(),
                            request.co(),
                            request.o3(),
                            request.pm10(),
                            request.pm25()));
        } else {
            aqiResult = unavailableResult(
                    unavailableReason(
                            reportType,
                            request,
                            hasMissingValue));
        }

        Integer versionNo = Math.toIntExact(
                lambdaQuery()
                        .eq(Measurement::getTaskId, taskId)
                        .count() + 1);

        Measurement measurement = new Measurement();
        measurement.setTaskId(task.getId());
        measurement.setFeedbackId(feedback.getId());
        measurement.setSubmitterId(worker.getId());
        measurement.setVersionNo(versionNo);
        measurement.setMeasuredAt(request.measuredAt());
        measurement.setLocation(request.location().trim());
        measurement.setReportType(reportType);
        measurement.setDataSource(
                request.dataSource().trim());

        measurement.setSo2(request.so2());
        measurement.setNo2(request.no2());
        measurement.setCo(request.co());
        measurement.seto3(request.o3());
        measurement.setPm10(request.pm10());
        measurement.setPm25(request.pm25());

        measurement.setMissingReason(
                trimToNull(request.missingReason()));

        boolean statisticallyValid =
                request.statisticallyValid()
                        && !"INSTANT".equals(reportType)
                        && !hasMissingValue;

        measurement.setStatisticallyValid(
                flag(statisticallyValid));

        measurement.setInvalidReason(
                statisticallyValid
                        ? null
                        : unavailableReason(
                        reportType,
                        request,
                        hasMissingValue));

        measurement.setQualityFlag(
                statisticallyValid
                        ? "VALID"
                        : "INVALID");

        measurement.setSo2Iaqi(
                iaqi(aqiResult, AqiCalculator.Pollutant.SO2));

        measurement.setNo2Iaqi(
                iaqi(aqiResult, AqiCalculator.Pollutant.NO2));

        measurement.setCoIaqi(
                iaqi(aqiResult, AqiCalculator.Pollutant.CO));

        measurement.seto3Iaqi(
                iaqi(aqiResult, AqiCalculator.Pollutant.O3));

        measurement.setPm10Iaqi(
                iaqi(aqiResult, AqiCalculator.Pollutant.PM10));

        measurement.setPm25Iaqi(
                iaqi(aqiResult, AqiCalculator.Pollutant.PM25));

        measurement.setAqiCalculable(
                flag(aqiResult.calculable()));

        measurement.setAqi(aqiResult.aqi());

        measurement.setAqiLevel(
                aqiResult.level() == null
                        ? null
                        : aqiResult.level().byteValue());

        measurement.setAqiCategory(
                aqiResult.category());

        measurement.setPrimaryPollutants(
                primaryPollutants(aqiResult));

        measurement.setCalculationReason(
                aqiResult.reason());

        measurement.setStandardVersion(
                "HJ 633-2026");

        measurement.setSiteNote(
                trimToNull(request.siteNote()));

        measurement.setReviewStatus("PENDING");

        if (!save(measurement)) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "检测记录保存失败");
        }

        anomalyRuleService.evaluate(
                measurement,
                feedback);

        task.setStatus("PENDING_REVIEW");

        if (inspectionTaskMapper.updateById(task) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "任务状态更新失败");
        }

        feedback.setStatus("PENDING_REVIEW");

        if (feedbackMapper.updateById(feedback) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "反馈状态更新失败");
        }

        createPendingAnalysis(measurement);
        saveOperationLog(feedback, worker, measurement);

        return toResponse(
                baseMapper.selectById(measurement.getId()));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public List<MeasurementResponse> listForAdmin(
            String reviewStatus) {

        User admin = currentAdmin();

        String normalizedStatus =
                reviewStatus == null
                        || reviewStatus.isBlank()
                        ? null
                        : reviewStatus.trim()
                        .toUpperCase(Locale.ROOT);

        if (normalizedStatus != null
                && !REVIEW_STATUSES.contains(
                normalizedStatus)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "检测复核状态不正确");
        }

        return lambdaQuery()
                .eq(
                        normalizedStatus != null,
                        Measurement::getReviewStatus,
                        normalizedStatus)
                .orderByDesc(Measurement::getSubmittedAt)
                .orderByDesc(Measurement::getId)
                .list()
                .stream()
                .filter(measurement ->
                        isAccessible(admin, measurement))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public MeasurementResponse getForAdmin(
            Long measurementId) {

        if (measurementId == null
                || measurementId <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "检测记录ID必须是正整数");
        }

        User admin = currentAdmin();

        Measurement measurement =
                getById(measurementId);

        if (measurement == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "检测记录不存在");
        }

        Feedback feedback =
                requireFeedback(measurement);

        requireAdminAccess(admin, feedback);

        return toResponse(measurement);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MeasurementReviewResponse review(
            Long measurementId,
            ReviewMeasurementRequest request) {

        if (measurementId == null
                || measurementId <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "检测记录ID必须是正整数");
        }

        User admin = currentAdmin();

        /*
         * 先普通读取一次，取得taskId。
         * 随后按任务→反馈→检测记录的固定顺序加锁，
         * 与网格员提交检测时的加锁顺序保持一致。
         */
        Measurement existing =
                baseMapper.selectById(measurementId);

        if (existing == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "检测记录不存在");
        }

        InspectionTask task =
                inspectionTaskMapper.selectOne(
                        Wrappers
                                .<InspectionTask>lambdaQuery()
                                .eq(
                                        InspectionTask::getId,
                                        existing.getTaskId())
                                .last("FOR UPDATE"));

        if (task == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "检测记录关联的任务不存在");
        }

        Feedback feedback =
                feedbackMapper.selectOne(
                        Wrappers
                                .<Feedback>lambdaQuery()
                                .eq(
                                        Feedback::getId,
                                        task.getFeedbackId())
                                .last("FOR UPDATE"));

        if (feedback == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "检测记录关联的反馈不存在");
        }

        Measurement measurement =
                baseMapper.selectOne(
                        Wrappers
                                .<Measurement>lambdaQuery()
                                .eq(
                                        Measurement::getId,
                                        measurementId)
                                .last("FOR UPDATE"));

        if (measurement == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "检测记录不存在");
        }

        requireAdminAccess(admin, feedback);

        if (!Objects.equals(
                measurement.getTaskId(),
                task.getId())
                || !Objects.equals(
                measurement.getFeedbackId(),
                feedback.getId())) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "检测记录关联关系不正确");
        }

        Measurement latest =
                lambdaQuery()
                        .eq(
                                Measurement::getTaskId,
                                task.getId())
                        .orderByDesc(
                                Measurement::getVersionNo)
                        .last("LIMIT 1")
                        .one();

        if (latest == null
                || !Objects.equals(
                latest.getId(),
                measurement.getId())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "只能复核任务的最新检测记录");
        }

        if (!"PENDING".equals(
                measurement.getReviewStatus())
                || !"PENDING_REVIEW".equals(
                task.getStatus())
                || !"PENDING_REVIEW".equals(
                feedback.getStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前状态不允许复核");
        }

        String decision = request.decision()
                .trim()
                .toUpperCase(Locale.ROOT);

        String opinion = request.opinion().trim();

        String publicReply =
                trimToNull(request.publicReply());

        if ("COMPLETE".equals(decision)) {
            if (publicReply == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "普通结案时必须填写公众办理说明");
            }

            Long activeAnomalyCount =
                    anomalyEventMapper.selectCount(
                            Wrappers
                                    .<AnomalyEvent>lambdaQuery()
                                    .eq(
                                            AnomalyEvent::getFeedbackId,
                                            feedback.getId())
                                    .in(
                                            AnomalyEvent::getStatus,
                                            "PENDING_REVIEW",
                                            "PROCESSING"));

            if (activeAnomalyCount != null
                    && activeAnomalyCount > 0) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "反馈存在待处理异常事件，不能普通结案");
            }
        }

        String taskStatus;
        String feedbackStatus;
        String measurementStatus;
        String action;

        if ("RETURN".equals(decision)) {
            measurementStatus = "RETURNED";
            taskStatus = "PENDING";
            feedbackStatus = "CHECKING";
            action = "RETURN_MEASUREMENT";

            excludePendingAnomalyForReturn(
                    measurement,
                    admin,
                    opinion);
        } else {
            measurementStatus = "APPROVED";
            taskStatus = "COMPLETED";
            feedbackStatus = "COMPLETED";
            action = "COMPLETE_FEEDBACK";
        }

        measurement.setReviewStatus(
                measurementStatus);

        if (baseMapper.updateById(measurement) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "检测复核状态更新失败");
        }

        task.setStatus(taskStatus);

        if (inspectionTaskMapper.updateById(task) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "核查任务状态更新失败");
        }

        feedback.setStatus(feedbackStatus);

        if ("COMPLETE".equals(decision)) {
            feedback.setPublicReply(publicReply);
        }

        if (feedbackMapper.updateById(feedback) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "反馈状态更新失败");
        }

        LocalDateTime reviewedAt =
                LocalDateTime.now();

        MeasurementReview review =
                new MeasurementReview();

        review.setMeasurementId(
                measurement.getId());

        review.setReviewerId(admin.getId());
        review.setDecision(decision);
        review.setOpinion(opinion);
        review.setPublicReply(publicReply);
        review.setReviewedAt(reviewedAt);

        if (measurementReviewMapper.insert(review) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "复核记录保存失败");
        }

        saveReviewOperationLog(
                feedback,
                task,
                admin,
                action,
                feedbackStatus,
                opinion);

        return new MeasurementReviewResponse(
                review.getId(),
                measurement.getId(),
                admin.getId(),
                decision,
                opinion,
                publicReply,
                reviewedAt,
                measurementStatus,
                taskStatus,
                feedbackStatus);
    }

    private void excludePendingAnomalyForReturn(
            Measurement measurement,
            User admin,
            String opinion) {

        AnomalyEvent event =
                anomalyEventMapper.selectOne(
                        Wrappers
                                .<AnomalyEvent>lambdaQuery()
                                .eq(
                                        AnomalyEvent::getMeasurementId,
                                        measurement.getId())
                                .eq(
                                        AnomalyEvent::getStatus,
                                        "PENDING_REVIEW")
                                .last("FOR UPDATE"));

        if (event == null) {
            return;
        }

        event.setStatus("EXCLUDED");
        event.setConfirmedPriority(null);
        event.setReviewedBy(admin.getId());
        event.setReviewedAt(
                LocalDateTime.now());
        event.setReviewReason(
                "检测记录被退回补充："
                        + opinion);

        if (anomalyEventMapper.updateById(event) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "退回检测时排除异常事件失败");
        }
    }

    private User currentWorker() {
        String phone = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getPhone, phone)
                        .eq(User::getRole, "GRID")
                        .eq(User::getEnabled, 1));

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "当前网格员账号不可用");
        }

        return user;
    }

    private void createPendingAnalysis(
            Measurement measurement) {

        AiAnalysis analysis = new AiAnalysis();
        analysis.setTargetType("MEASUREMENT");
        analysis.setTargetId(measurement.getId());
        analysis.setStatus("PENDING");

        if (aiAnalysisMapper.insert(analysis) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "待分析记录创建失败");
        }
    }

    private void saveOperationLog(
            Feedback feedback,
            User worker,
            Measurement measurement) {

        OperationLog log = new OperationLog();
        log.setBusinessType("FEEDBACK");
        log.setBusinessId(feedback.getId());
        log.setAction("SUBMIT_MEASUREMENT");
        log.setOperatorId(worker.getId());
        log.setFromStatus("CHECKING");
        log.setToStatus("PENDING_REVIEW");
        log.setFromAssigneeId(worker.getId());
        log.setToAssigneeId(worker.getId());
        log.setRemark(
                "提交检测记录，记录ID="
                        + measurement.getId()
                        + "，版本="
                        + measurement.getVersionNo());

        if (operationLogMapper.insert(log) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "操作记录保存失败");
        }
    }

    private String unavailableReason(
            String reportType,
            SubmitMeasurementRequest request,
            boolean hasMissingValue) {

        if ("INSTANT".equals(reportType)) {
            return "现场瞬时读数不能作为完整AQI统计数据";
        }

        if (!request.statisticallyValid()) {
            return request.invalidReason().trim();
        }

        if (hasMissingValue) {
            return "六项污染物数据不完整："
                    + request.missingReason().trim();
        }

        return "当前数据不能计算AQI";
    }

    private AqiCalculator.Result unavailableResult(
            String reason) {

        return new AqiCalculator.Result(
                false,
                java.util.Map.of(),
                null,
                null,
                null,
                List.of(),
                reason);
    }

    private Integer iaqi(
            AqiCalculator.Result result,
            AqiCalculator.Pollutant pollutant) {

        return result.iaqiValues().get(pollutant);
    }

    private String primaryPollutants(
            AqiCalculator.Result result) {

        if (result.primaryPollutants().isEmpty()) {
            return null;
        }

        return result.primaryPollutants()
                .stream()
                .map(Enum::name)
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        return isBlank(value)
                ? null
                : value.trim();
    }

    private Byte flag(boolean value) {
        return (byte) (value ? 1 : 0);
    }

    private static final Set<String> REVIEW_STATUSES =
            Set.of(
                    "PENDING",
                    "APPROVED",
                    "RETURNED");

    private User currentAdmin() {
        String phone = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getPhone, phone)
                        .eq(User::getRole, "ADMIN")
                        .eq(User::getEnabled, 1));

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "当前管理员账号不可用");
        }

        return user;
    }

    private Feedback requireFeedback(
            Measurement measurement) {

        Feedback feedback =
                feedbackMapper.selectById(
                        measurement.getFeedbackId());

        if (feedback == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "检测记录关联的反馈不存在");
        }

        return feedback;
    }

    private boolean isAccessible(
            User admin,
            Measurement measurement) {

        Feedback feedback =
                requireFeedback(measurement);

        Grid grid =
                gridMapper.selectById(
                        feedback.getGridId());

        if (grid == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "反馈关联的网格不存在");
        }

        return userRegionMapper.countAccessibleRegion(
                admin.getId(),
                grid.getRegionId()) > 0;
    }

    private void requireAdminAccess(
            User admin,
            Feedback feedback) {

        Grid grid =
                gridMapper.selectById(
                        feedback.getGridId());

        if (grid == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "反馈关联的网格不存在");
        }

        if (userRegionMapper.countAccessibleRegion(
                admin.getId(),
                grid.getRegionId()) == 0) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "检测记录不在当前管理员授权区域内");
        }
    }

    private void saveReviewOperationLog(
            Feedback feedback,
            InspectionTask task,
            User admin,
            String action,
            String toStatus,
            String opinion) {

        OperationLog log = new OperationLog();
        log.setBusinessType("FEEDBACK");
        log.setBusinessId(feedback.getId());
        log.setAction(action);
        log.setOperatorId(admin.getId());
        log.setFromStatus("PENDING_REVIEW");
        log.setToStatus(toStatus);
        log.setFromAssigneeId(task.getAssigneeId());
        log.setToAssigneeId(task.getAssigneeId());
        log.setRemark(opinion);

        if (operationLogMapper.insert(log) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "复核操作记录保存失败");
        }
    }

    private MeasurementResponse toResponse(
            Measurement measurement) {

        return new MeasurementResponse(
                measurement.getId(),
                measurement.getTaskId(),
                measurement.getFeedbackId(),
                measurement.getSubmitterId(),
                measurement.getVersionNo(),
                measurement.getMeasuredAt(),
                measurement.getLocation(),
                measurement.getReportType(),
                measurement.getDataSource(),
                measurement.getSo2(),
                measurement.getNo2(),
                measurement.getCo(),
                measurement.geto3(),
                measurement.getPm10(),
                measurement.getPm25(),
                measurement.getMissingReason(),
                measurement.getStatisticallyValid(),
                measurement.getInvalidReason(),
                measurement.getQualityFlag(),
                measurement.getSo2Iaqi(),
                measurement.getNo2Iaqi(),
                measurement.getCoIaqi(),
                measurement.geto3Iaqi(),
                measurement.getPm10Iaqi(),
                measurement.getPm25Iaqi(),
                measurement.getAqiCalculable(),
                measurement.getAqi(),
                measurement.getAqiLevel(),
                measurement.getAqiCategory(),
                measurement.getPrimaryPollutants(),
                measurement.getCalculationReason(),
                measurement.getStandardVersion(),
                measurement.getRuleStatus(),
                measurement.getRuleReason(),
                measurement.getSuggestedPriority(),
                measurement.getRuleVersion(),
                measurement.getRuleEvaluatedAt(),
                measurement.getSiteNote(),
                measurement.getReviewStatus(),
                measurement.getSubmittedAt());
    }
}
