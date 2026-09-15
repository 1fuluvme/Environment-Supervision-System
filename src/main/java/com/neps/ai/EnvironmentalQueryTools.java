package com.neps.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.neps.dto.AiQuestionRequest;
import com.neps.dto.AiQuestionResponse;
import com.neps.dto.PollutionTraceResponse;
import com.neps.dto.StatisticsResponse;
import com.neps.entity.AnomalyEvent;
import com.neps.entity.AqiPrediction;
import com.neps.entity.Grid;
import com.neps.mapper.AnomalyEventMapper;
import com.neps.mapper.AqiPredictionMapper;
import com.neps.mapper.GridMapper;
import com.neps.mapper.RegionMapper;
import com.neps.service.PollutionTraceService;
import com.neps.service.StatisticsService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class EnvironmentalQueryTools {

    public static final String SCOPE_KEY =
            EnvironmentalQueryTools.class.getName() + ".scope";

    public static final String USAGE_KEY =
            EnvironmentalQueryTools.class.getName() + ".usage";

    private final StatisticsService statisticsService;
    private final AqiPredictionMapper predictionMapper;
    private final PollutionTraceService traceService;
    private final AnomalyEventMapper eventMapper;
    private final GridMapper gridMapper;
    private final RegionMapper regionMapper;

    public EnvironmentalQueryTools(
            StatisticsService statisticsService,
            AqiPredictionMapper predictionMapper,
            PollutionTraceService traceService,
            AnomalyEventMapper eventMapper,
            GridMapper gridMapper,
            RegionMapper regionMapper) {

        this.statisticsService = statisticsService;
        this.predictionMapper = predictionMapper;
        this.traceService = traceService;
        this.eventMapper = eventMapper;
        this.gridMapper = gridMapper;
        this.regionMapper = regionMapper;
    }

    public QueryScope createScope(
            AiQuestionRequest request) {

        StatisticsResponse statistics =
                statisticsService.query(
                        request.regionId(),
                        request.startDate(),
                        request.endDate(),
                        request.reportType());

        List<Long> regionIds =
                regionMapper.selectEnabledIdsInTree(
                        request.regionId());

        AnomalyEvent selectedEvent =
                findSelectedEvent(
                        request.anomalyEventId(),
                        regionIds,
                        request.startDate(),
                        request.endDate());

        return new QueryScope(
                request.regionId(),
                request.startDate(),
                request.endDate(),
                request.reportType(),
                request.anomalyEventId(),
                statistics,
                List.copyOf(regionIds),
                selectedEvent);
    }

    @Tool(
            name = "queryAirQualityStatistics",
            description = """
                    查询授权区域和时间范围内的空气质量统计、
                    AQI等级分布、污染数量、优良数量、覆盖率和趋势。
                    回答空气质量、AQI、统计、趋势、污染或覆盖率问题时使用。
                    """)
    public ToolResult queryAirQualityStatistics(
            @ToolParam(description = "区域ID")
            Long regionId,

            @ToolParam(description = "开始日期，ISO格式，例如2026-08-01")
            LocalDate startDate,

            @ToolParam(description = "结束日期，ISO格式，例如2026-08-10")
            LocalDate endDate,

            @ToolParam(description = "统计口径，例如REALTIME或DAILY")
            String reportType,

            ToolContext context) {

        QueryScope scope = scope(
                regionId,
                startDate,
                endDate,
                reportType,
                context);

        List<AiQuestionResponse.Source> sources =
                scope.statistics().measurementCount() == 0
                        ? List.of()
                        : List.of(source(
                        sourceId(
                                "STATISTICS",
                                regionId,
                                startDate,
                                endDate),
                        "AIR_QUALITY_STATISTICS",
                        "授权区域空气质量统计"));

        recordUsage(context, sources);

        return new ToolResult(
                sources,
                scope.statistics());
    }

    @Tool(
            name = "queryAnomalyEvidence",
            description = """
                    查询授权区域中的异常事件数量、状态、优先级，
                    或查询请求中指定异常事件的只读依据。
                    回答异常、事件、事件详情或事件依据问题时使用。
                    """)
    public ToolResult queryAnomalyEvidence(
            @ToolParam(description = "区域ID")
            Long regionId,

            @ToolParam(description = "开始日期，ISO格式")
            LocalDate startDate,

            @ToolParam(description = "结束日期，ISO格式")
            LocalDate endDate,

            @ToolParam(description = "统计口径，例如REALTIME或DAILY")
            String reportType,

            @Nullable
            @ToolParam(
                    description = "指定异常事件ID，没有指定事件时省略",
                    required = false)
            Long anomalyEventId,

            ToolContext context) {

        QueryScope scope = scope(
                regionId,
                startDate,
                endDate,
                reportType,
                anomalyEventId,
                context);

        List<Long> gridIds =
                scope.regionIds().isEmpty()
                        ? List.of()
                        : gridMapper.selectList(
                                Wrappers.<Grid>lambdaQuery()
                                        .in(
                                                Grid::getRegionId,
                                                scope.regionIds())
                                        .eq(Grid::getEnabled, 1))
                        .stream()
                        .map(Grid::getId)
                        .toList();

        List<AnomalyEvent> events =
                findEvents(
                        gridIds,
                        startDate,
                        endDate);

        Map<String, Long> statusCounts =
                events.stream()
                        .collect(Collectors.groupingBy(
                                AnomalyEvent::getStatus,
                                TreeMap::new,
                                Collectors.counting()));

        Map<String, Long> priorityCounts =
                events.stream()
                        .collect(Collectors.groupingBy(
                                this::priority,
                                TreeMap::new,
                                Collectors.counting()));

        EventData selected =
                eventData(scope.selectedEvent());

        List<AiQuestionResponse.Source> sources =
                new ArrayList<>();

        if (!events.isEmpty()) {
            sources.add(source(
                    sourceId(
                            "ANOMALY_SUMMARY",
                            regionId,
                            startDate,
                            endDate),
                    "ANOMALY_SUMMARY",
                    "授权区域异常事件汇总"));
        }

        if (scope.selectedEvent() != null) {
            sources.add(source(
                    "ANOMALY_EVENT:"
                            + scope.selectedEvent().getId(),
                    "ANOMALY_EVENT",
                    isAdmin()
                            ? "授权异常事件业务依据"
                            : "授权异常事件状态摘要"));
        }

        recordUsage(context, sources);

        return new ToolResult(
                List.copyOf(sources),
                new AnomalyData(
                        events.size(),
                        statusCounts,
                        priorityCounts,
                        selected));
    }

    @Tool(
            name = "queryAqiPredictions",
            description = """
                    查询授权区域已有的AQI预测结果和实际值。
                    回答预测、未来空气质量或预测误差问题时使用。
                    预测结果不是实时监测事实。
                    """)
    public ToolResult queryAqiPredictions(
            @ToolParam(description = "区域ID")
            Long regionId,

            @ToolParam(description = "开始日期，ISO格式")
            LocalDate startDate,

            @ToolParam(description = "结束日期，ISO格式")
            LocalDate endDate,

            @ToolParam(description = "统计口径，例如REALTIME或DAILY")
            String reportType,

            ToolContext context) {

        QueryScope scope = scope(
                regionId,
                startDate,
                endDate,
                reportType,
                context);

        List<PredictionData> predictions =
                scope.regionIds().isEmpty()
                        ? List.of()
                        : predictionMapper.selectList(
                                Wrappers.<AqiPrediction>lambdaQuery()
                                        .in(
                                                AqiPrediction::getRegionId,
                                                scope.regionIds())
                                        .between(
                                                AqiPrediction::getTargetDate,
                                                startDate,
                                                endDate)
                                        .orderByDesc(
                                                AqiPrediction::getTargetDate)
                                        .last("LIMIT 50"))
                        .stream()
                        .map(prediction ->
                                new PredictionData(
                                        prediction.getId(),
                                        prediction.getRegionId(),
                                        prediction.getTargetDate(),
                                        prediction.getPredictedAqi(),
                                        prediction.getActualAqi(),
                                        prediction.getAbsoluteError(),
                                        prediction.getMethod(),
                                        prediction.getSourceName()))
                        .toList();

        List<AiQuestionResponse.Source> sources =
                predictions.isEmpty()
                        ? List.of()
                        : List.of(source(
                        sourceId(
                                "AQI_PREDICTIONS",
                                regionId,
                                startDate,
                                endDate),
                        "AQI_PREDICTION",
                        "授权区域已有AQI预测结果"));

        recordUsage(context, sources);

        return new ToolResult(
                sources,
                predictions);
    }

    @Tool(
            name = "queryPollutionTraces",
            description = """
                    查询授权区域已有的疑似污染溯源分析及候选企业。
                    回答污染来源、企业或溯源问题时使用。
                    结果只是辅助线索，不能作为责任认定。
                    """)
    public ToolResult queryPollutionTraces(
            @ToolParam(description = "区域ID")
            Long regionId,

            @ToolParam(description = "开始日期，ISO格式")
            LocalDate startDate,

            @ToolParam(description = "结束日期，ISO格式")
            LocalDate endDate,

            @ToolParam(description = "统计口径，例如REALTIME或DAILY")
            String reportType,

            ToolContext context) {

        QueryScope scope = scope(
                regionId,
                startDate,
                endDate,
                reportType,
                context);

        List<TraceData> traces =
                traceService.list((Long) null)
                        .stream()
                        .filter(trace ->
                                scope.regionIds().contains(
                                        trace.regionId()))
                        .filter(trace ->
                                trace.eventObservedAt() != null)
                        .filter(trace -> {
                            LocalDate date =
                                    trace.eventObservedAt()
                                            .toLocalDate();

                            return !date.isBefore(startDate)
                                    && !date.isAfter(endDate);
                        })
                        .limit(20)
                        .map(trace ->
                                new TraceData(
                                        trace.id(),
                                        trace.anomalyEventId(),
                                        trace.regionId(),
                                        trace.status(),
                                        trace.eventObservedAt(),
                                        trace.candidates(),
                                        trace.methodVersion(),
                                        trace.demo()))
                        .toList();

        List<AiQuestionResponse.Source> sources =
                traces.isEmpty()
                        ? List.of()
                        : List.of(source(
                        sourceId(
                                "POLLUTION_TRACES",
                                regionId,
                                startDate,
                                endDate),
                        "POLLUTION_TRACE",
                        "授权区域已有疑似溯源结果"));

        recordUsage(context, sources);

        return new ToolResult(
                sources,
                traces);
    }

    private QueryScope scope(
            Long regionId,
            LocalDate startDate,
            LocalDate endDate,
            String reportType,
            ToolContext context) {

        Object value =
                context.getContext().get(SCOPE_KEY);

        if (value instanceof QueryScope fixed) {
            validateFixedScope(
                    fixed,
                    regionId,
                    startDate,
                    endDate,
                    reportType);

            return fixed;
        }

        StatisticsResponse statistics =
                statisticsService.query(
                        regionId,
                        startDate,
                        endDate,
                        reportType);

        return new QueryScope(
                regionId,
                startDate,
                endDate,
                reportType,
                null,
                statistics,
                List.copyOf(
                        regionMapper.selectEnabledIdsInTree(
                                regionId)),
                null);
    }

    private QueryScope scope(
            Long regionId,
            LocalDate startDate,
            LocalDate endDate,
            String reportType,
            Long anomalyEventId,
            ToolContext context) {

        QueryScope scope = scope(
                regionId,
                startDate,
                endDate,
                reportType,
                context);

        if (context.getContext().get(SCOPE_KEY)
                instanceof QueryScope fixed) {

            if (!Objects.equals(
                    fixed.anomalyEventId(),
                    anomalyEventId)) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "工具请求的异常事件超出本次问答范围");
            }

            return fixed;
        }

        return new QueryScope(
                scope.regionId(),
                scope.startDate(),
                scope.endDate(),
                scope.reportType(),
                anomalyEventId,
                scope.statistics(),
                scope.regionIds(),
                findSelectedEvent(
                        anomalyEventId,
                        scope.regionIds(),
                        startDate,
                        endDate));
    }

    private void validateFixedScope(
            QueryScope fixed,
            Long regionId,
            LocalDate startDate,
            LocalDate endDate,
            String reportType) {

        boolean invalid =
                !Objects.equals(
                        fixed.regionId(),
                        regionId)
                        || !Objects.equals(
                        fixed.startDate(),
                        startDate)
                        || !Objects.equals(
                        fixed.endDate(),
                        endDate)
                        || !fixed.reportType()
                        .equalsIgnoreCase(reportType);

        if (invalid) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "工具请求超出本次问答的数据范围");
        }
    }

    private List<AnomalyEvent> findEvents(
            List<Long> gridIds,
            LocalDate startDate,
            LocalDate endDate) {

        if (gridIds.isEmpty()) {
            return List.of();
        }

        return eventMapper.selectList(
                Wrappers.<AnomalyEvent>lambdaQuery()
                        .in(
                                AnomalyEvent::getGridId,
                                gridIds)
                        .ge(
                                AnomalyEvent::getCreatedAt,
                                startDate.atStartOfDay())
                        .lt(
                                AnomalyEvent::getCreatedAt,
                                endDate.plusDays(1)
                                        .atStartOfDay())
                        .orderByDesc(
                                AnomalyEvent::getCreatedAt)
                        .last("LIMIT 50"));
    }

    private AnomalyEvent findSelectedEvent(
            Long eventId,
            List<Long> regionIds,
            LocalDate startDate,
            LocalDate endDate) {

        if (eventId == null) {
            return null;
        }

        AnomalyEvent event =
                eventMapper.selectById(eventId);

        if (event == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "异常事件不存在");
        }

        Grid grid =
                gridMapper.selectById(
                        event.getGridId());

        if (grid == null
                || !regionIds.contains(
                grid.getRegionId())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "异常事件不在本次授权查询范围内");
        }

        LocalDate eventDate =
                event.getCreatedAt().toLocalDate();

        if (eventDate.isBefore(startDate)
                || eventDate.isAfter(endDate)) {

            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "异常事件不在选定时间范围内");
        }

        return event;
    }

    private EventData eventData(
            AnomalyEvent event) {

        if (event == null) {
            return null;
        }

        boolean admin = isAdmin();

        return new EventData(
                event.getId(),
                event.getStatus(),
                priority(event),
                event.getCreatedAt(),
                admin ? event.getGridId() : null,
                admin ? event.getFeedbackId() : null,
                admin ? event.getMeasurementId() : null,
                admin ? event.getTriggerReason() : null,
                admin ? event.getReviewReason() : null);
    }

    private boolean isAdmin() {
        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        return authentication != null
                && authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(
                                authority.getAuthority()));
    }

    private String priority(
            AnomalyEvent event) {

        return event.getConfirmedPriority() != null
                ? event.getConfirmedPriority()
                : event.getSuggestedPriority();
    }

    private String sourceId(
            String type,
            Long regionId,
            LocalDate startDate,
            LocalDate endDate) {

        return type
                + ":"
                + regionId
                + ":"
                + startDate
                + ":"
                + endDate;
    }

    private AiQuestionResponse.Source source(
            String id,
            String type,
            String description) {

        return new AiQuestionResponse.Source(
                id,
                type,
                description);
    }

    private void recordUsage(
            ToolContext context,
            List<AiQuestionResponse.Source> sources) {

        Object value =
                context.getContext().get(USAGE_KEY);

        if (value instanceof Usage usage) {
            usage.addAll(sources);
        }
    }

    public record QueryScope(
            Long regionId,
            LocalDate startDate,
            LocalDate endDate,
            String reportType,
            Long anomalyEventId,
            StatisticsResponse statistics,
            List<Long> regionIds,
            AnomalyEvent selectedEvent) {
    }

    public record ToolResult(
            List<AiQuestionResponse.Source> sources,
            Object data) {
    }

    public record AnomalyData(
            int totalCount,
            Map<String, Long> statusCounts,
            Map<String, Long> priorityCounts,
            EventData selectedEvent) {
    }

    public record EventData(
            Long id,
            String status,
            String priority,
            LocalDateTime createdAt,
            Long gridId,
            Long feedbackId,
            Long measurementId,
            String triggerReason,
            String reviewReason) {
    }

    public record PredictionData(
            Long id,
            Long regionId,
            LocalDate targetDate,
            Short predictedAqi,
            Short actualAqi,
            Object absoluteError,
            String method,
            String sourceName) {
    }

    public record TraceData(
            Long id,
            Long anomalyEventId,
            Long regionId,
            String status,
            LocalDateTime eventObservedAt,
            List<PollutionTraceResponse.Candidate> candidates,
            String methodVersion,
            boolean demo) {
    }

    public static final class Usage {

        private final Map<String, AiQuestionResponse.Source>
                sources = new LinkedHashMap<>();

        public void addAll(
                List<AiQuestionResponse.Source> values) {

            values.forEach(source ->
                    sources.put(source.id(), source));
        }

        public List<AiQuestionResponse.Source> sources() {
            return List.copyOf(sources.values());
        }
    }
}
