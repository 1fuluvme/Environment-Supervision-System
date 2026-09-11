package com.neps.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neps.ai.QwenAiClient;
import com.neps.dto.*;
import com.neps.entity.AnomalyEvent;
import com.neps.entity.AqiPrediction;
import com.neps.entity.Grid;
import com.neps.mapper.AnomalyEventMapper;
import com.neps.mapper.AqiPredictionMapper;
import com.neps.mapper.GridMapper;
import com.neps.mapper.RegionMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiQuestionService {

    private static final Set<String> SUPPORTED_WORDS =
            Set.of(
                    "空气", "AQI", "aqi", "检测",
                    "统计", "趋势", "污染", "优良",
                    "覆盖率", "记录", "异常", "事件",
                    "预测", "未来", "溯源", "来源",
                    "企业", "依据");

    private static final Set<String> EVENT_WORDS =
            Set.of(
                    "这个事件", "该事件",
                    "事件详情", "事件依据");

    private static final Set<String> WRITE_WORDS =
            Set.of(
                    "派发", "指派", "删除", "修改",
                    "更新状态", "关闭工单", "创建工单",
                    "确认工单", "导入");

    private final StatisticsService statisticsService;
    private final AqiPredictionMapper predictionMapper;
    private final PollutionTraceService traceService;
    private final AnomalyEventMapper eventMapper;
    private final GridMapper gridMapper;
    private final RegionMapper regionMapper;
    private final QwenAiClient qwenAiClient;
    private final ObjectMapper objectMapper;
    private final String mode;

    public AiQuestionService(
            StatisticsService statisticsService,
            AqiPredictionMapper predictionMapper,
            PollutionTraceService traceService,
            AnomalyEventMapper eventMapper,
            GridMapper gridMapper,
            RegionMapper regionMapper,
            QwenAiClient qwenAiClient,
            ObjectMapper objectMapper,
            @Value("${app.ai.mode:mock}")
            String mode) {

        this.statisticsService = statisticsService;
        this.predictionMapper = predictionMapper;
        this.traceService = traceService;
        this.eventMapper = eventMapper;
        this.gridMapper = gridMapper;
        this.regionMapper = regionMapper;
        this.qwenAiClient = qwenAiClient;
        this.objectMapper = objectMapper;
        this.mode = mode;
    }

    @PreAuthorize("hasAnyRole('ADMIN','DECISION')")
    public AiQuestionResponse answer(
            AiQuestionRequest request) {

        String question = request.question().trim();

        validateQuestion(question, request.anomalyEventId());

        /*
         * 统计服务统一完成角色、日期和区域授权校验。
         * 后续查询只能使用这个已授权区域的子树。
         */
        StatisticsResponse statistics =
                statisticsService.query(
                        request.regionId(),
                        request.startDate(),
                        request.endDate(),
                        request.reportType());

        List<Long> regionIds =
                regionMapper.selectEnabledIdsInTree(
                        request.regionId());

        List<Long> gridIds = gridMapper.selectList(
                        Wrappers.<Grid>lambdaQuery()
                                .in(
                                        !regionIds.isEmpty(),
                                        Grid::getRegionId,
                                        regionIds)
                                .eq(Grid::getEnabled, 1))
                .stream()
                .map(Grid::getId)
                .toList();

        List<AnomalyEvent> events =
                findEvents(
                        gridIds,
                        request.startDate(),
                        request.endDate());

        List<AqiPrediction> predictions =
                findPredictions(
                        regionIds,
                        request.startDate(),
                        request.endDate());

        List<PollutionTraceResponse> traces =
                findTraces(
                        regionIds,
                        request.startDate(),
                        request.endDate());

        AnomalyEvent selectedEvent =
                findSelectedEvent(
                        request.anomalyEventId(),
                        regionIds,
                        request.startDate(),
                        request.endDate());

        boolean noData =
                statistics.measurementCount() == 0
                        && events.isEmpty()
                        && predictions.isEmpty()
                        && traces.isEmpty()
                        && selectedEvent == null;

        if (noData) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "选定区域和时间范围内没有可供问答的数据");
        }

        Context context = buildContext(
                question,
                statistics,
                events,
                predictions,
                traces,
                selectedEvent);

        Answer answer = "mock".equalsIgnoreCase(mode)
                ? mockAnswer(
                statistics,
                events,
                predictions,
                traces,
                selectedEvent,
                context.sources())
                : qwenAnswer(context.snapshot());

        return new AiQuestionResponse(
                question,
                statistics.regionId(),
                statistics.regionName(),
                statistics.startDate(),
                statistics.endDate(),
                statistics.reportType(),
                answer.answer(),
                answer.monitoringFacts(),
                answer.predictions(),
                answer.suggestions(),
                context.sources(),
                "mock".equalsIgnoreCase(mode)
                        ? "MOCK"
                        : "QWEN",
                "mock".equalsIgnoreCase(mode)
                        ? "LOCAL-DEMO"
                        : qwenAiClient.modelName(),
                "mock".equalsIgnoreCase(mode),
                LocalDateTime.now());
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
                                AnomalyEvent::getCreatedAt));
    }

    private List<AqiPrediction> findPredictions(
            List<Long> regionIds,
            LocalDate startDate,
            LocalDate endDate) {

        if (regionIds.isEmpty()) {
            return List.of();
        }

        // ponytail: 问答最多提供50条预测；需要长期分析时再做分页摘要。
        return predictionMapper.selectList(
                Wrappers.<AqiPrediction>lambdaQuery()
                        .in(
                                AqiPrediction::getRegionId,
                                regionIds)
                        .between(
                                AqiPrediction::getTargetDate,
                                startDate,
                                endDate)
                        .orderByDesc(
                                AqiPrediction::getTargetDate)
                        .last("LIMIT 50"));
    }

    private List<PollutionTraceResponse> findTraces(
            List<Long> regionIds,
            LocalDate startDate,
            LocalDate endDate) {

        return traceService.list((Long) null)
                .stream()
                .filter(trace ->
                        regionIds.contains(
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
                .limit(50)
                .toList();
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

    private Context buildContext(
            String question,
            StatisticsResponse statistics,
            List<AnomalyEvent> events,
            List<AqiPrediction> predictions,
            List<PollutionTraceResponse> traces,
            AnomalyEvent selectedEvent) {

        ObjectNode context =
                objectMapper.createObjectNode();

        List<AiQuestionResponse.Source> sources =
                new ArrayList<>();

        context.put("question", question);
        context.put(
                "dataScope",
                isAdmin()
                        ? "ADMIN_AUTHORIZED_DETAIL"
                        : "DECISION_AUTHORIZED_SUMMARY");

        if (statistics.measurementCount() > 0) {
            String id = sourceId(
                    "STATISTICS",
                    statistics.regionId(),
                    statistics.startDate(),
                    statistics.endDate());

            ObjectNode statisticsNode =
                    context.putObject("statistics");

            statisticsNode.put("sourceId", id);
            statisticsNode.set(
                    "data",
                    objectMapper.valueToTree(
                            statistics));

            sources.add(source(
                    id,
                    "AIR_QUALITY_STATISTICS",
                    "授权区域空气质量统计"));
        }

        if (!events.isEmpty()) {
            String id = sourceId(
                    "ANOMALY_SUMMARY",
                    statistics.regionId(),
                    statistics.startDate(),
                    statistics.endDate());

            Map<String, Long> statusCounts =
                    events.stream()
                            .collect(
                                    Collectors.groupingBy(
                                            AnomalyEvent::getStatus,
                                            TreeMap::new,
                                            Collectors.counting()));

            Map<String, Long> priorityCounts =
                    events.stream()
                            .collect(
                                    Collectors.groupingBy(
                                            this::priority,
                                            TreeMap::new,
                                            Collectors.counting()));

            ObjectNode anomalyNode =
                    context.putObject(
                            "anomalySummary");

            anomalyNode.put("sourceId", id);
            anomalyNode.put(
                    "totalCount",
                    events.size());
            anomalyNode.set(
                    "statusCounts",
                    objectMapper.valueToTree(
                            statusCounts));
            anomalyNode.set(
                    "priorityCounts",
                    objectMapper.valueToTree(
                            priorityCounts));

            sources.add(source(
                    id,
                    "ANOMALY_SUMMARY",
                    "授权区域异常事件汇总"));
        }

        if (!predictions.isEmpty()) {
            String id = sourceId(
                    "AQI_PREDICTIONS",
                    statistics.regionId(),
                    statistics.startDate(),
                    statistics.endDate());

            ObjectNode predictionNode =
                    context.putObject("aqiPredictions");

            predictionNode.put("sourceId", id);

            ArrayNode items =
                    predictionNode.putArray("items");

            predictions.forEach(prediction -> {
                ObjectNode item = items.addObject();

                item.put("id", prediction.getId());
                item.put(
                        "regionId",
                        prediction.getRegionId());
                item.put(
                        "targetDate",
                        String.valueOf(
                                prediction.getTargetDate()));
                item.put(
                        "predictedAqi",
                        prediction.getPredictedAqi());
                item.put(
                        "method",
                        prediction.getMethod());
                item.put(
                        "sourceName",
                        prediction.getSourceName());

                if (prediction.getActualAqi() != null) {
                    item.put(
                            "actualAqi",
                            prediction.getActualAqi());
                }
            });

            sources.add(source(
                    id,
                    "AQI_PREDICTION",
                    "授权区域已有AQI预测结果"));
        }

        if (!traces.isEmpty()) {
            String id = sourceId(
                    "POLLUTION_TRACES",
                    statistics.regionId(),
                    statistics.startDate(),
                    statistics.endDate());

            ObjectNode traceNode =
                    context.putObject(
                            "pollutionTraces");

            traceNode.put("sourceId", id);
            traceNode.set(
                    "items",
                    objectMapper.valueToTree(
                            traces));

            sources.add(source(
                    id,
                    "POLLUTION_TRACE",
                    "授权区域已有疑似溯源结果"));
        }

        if (selectedEvent != null) {
            String id =
                    "ANOMALY_EVENT:"
                            + selectedEvent.getId();

            ObjectNode eventNode =
                    context.putObject("selectedEvent");

            eventNode.put("sourceId", id);
            eventNode.put(
                    "id",
                    selectedEvent.getId());
            eventNode.put(
                    "status",
                    selectedEvent.getStatus());
            eventNode.put(
                    "priority",
                    priority(selectedEvent));
            eventNode.put(
                    "createdAt",
                    String.valueOf(
                            selectedEvent.getCreatedAt()));

            /*
             * 决策者只获得事件汇总。
             * 管理员才可获得业务触发及人工复核详情。
             */
            if (isAdmin()) {
                eventNode.put(
                        "gridId",
                        selectedEvent.getGridId());
                eventNode.put(
                        "feedbackId",
                        selectedEvent.getFeedbackId());
                eventNode.put(
                        "measurementId",
                        selectedEvent.getMeasurementId());
                eventNode.put(
                        "triggerReason",
                        selectedEvent.getTriggerReason());
                eventNode.put(
                        "reviewReason",
                        selectedEvent.getReviewReason());
            }

            sources.add(source(
                    id,
                    "ANOMALY_EVENT",
                    isAdmin()
                            ? "授权异常事件业务依据"
                            : "授权异常事件状态摘要"));
        }

        context.set(
                "sources",
                objectMapper.valueToTree(sources));

        try {
            return new Context(
                    objectMapper.writeValueAsString(
                            context),
                    List.copyOf(sources));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "AI问答上下文生成失败",
                    exception);
        }
    }

    private Answer mockAnswer(
            StatisticsResponse statistics,
            List<AnomalyEvent> events,
            List<AqiPrediction> predictions,
            List<PollutionTraceResponse> traces,
            AnomalyEvent selectedEvent,
            List<AiQuestionResponse.Source> sources) {

        List<String> facts =
                new ArrayList<>();

        List<String> predictionTexts =
                new ArrayList<>();

        String statisticsSource =
                findSource(
                        sources,
                        "AIR_QUALITY_STATISTICS");

        if (statisticsSource != null) {
            facts.add(
                    "[" + statisticsSource
                            + "] 有效AQI记录"
                            + statistics.validAqiCount()
                            + "条，其中污染记录"
                            + statistics.pollutedCount()
                            + "条、优良记录"
                            + statistics.goodCount()
                            + "条");
        }

        String anomalySource =
                findSource(
                        sources,
                        "ANOMALY_SUMMARY");

        if (anomalySource != null) {
            facts.add(
                    "[" + anomalySource
                            + "] 异常事件共"
                            + events.size()
                            + "个");
        }

        String predictionSource =
                findSource(
                        sources,
                        "AQI_PREDICTION");

        for (AqiPrediction prediction : predictions) {
            predictionTexts.add(
                    "[" + predictionSource
                            + "] "
                            + prediction.getTargetDate()
                            + "预测AQI为"
                            + prediction.getPredictedAqi()
                            + "，方法为"
                            + prediction.getMethod());
        }

        String traceSource =
                findSource(
                        sources,
                        "POLLUTION_TRACE");

        if (traceSource != null) {
            facts.add(
                    "[" + traceSource
                            + "] 已有"
                            + traces.size()
                            + "条疑似溯源分析结果，"
                            + "不能直接视为责任认定");
        }

        String answer;

        if (selectedEvent != null) {
            String eventSource =
                    "ANOMALY_EVENT:"
                            + selectedEvent.getId();

            answer = "异常事件"
                    + selectedEvent.getId()
                    + "当前状态为"
                    + selectedEvent.getStatus()
                    + "，优先级为"
                    + priority(selectedEvent)
                    + "。";

            facts.add(
                    "[" + eventSource
                            + "] "
                            + (isAdmin()
                            ? "触发依据："
                            + selectedEvent.getTriggerReason()
                            : "决策者仅展示事件状态摘要"));
        } else {
            answer = statistics.regionName()
                    + "在所选时间内有"
                    + statistics.pollutedCount()
                    + "条污染记录、"
                    + events.size()
                    + "个异常事件、"
                    + predictions.size()
                    + "条预测和"
                    + traces.size()
                    + "条疑似溯源结果。";
        }

        List<String> suggestions =
                statistics.pollutedCount() > 0
                        || !events.isEmpty()
                        ? List.of(
                        "结合现场检测和人工复核决定后续处置")
                        : List.of(
                        "继续保持常规监测");

        return new Answer(
                answer,
                List.copyOf(facts),
                List.copyOf(predictionTexts),
                suggestions);
    }

    private Answer qwenAnswer(
            String snapshot) {

        try {
            JsonNode result =
                    objectMapper.readTree(
                            qwenAiClient.answerQuestion(
                                    snapshot));

            return new Answer(
                    result.path("answer").asText(),
                    strings(
                            result.path(
                                    "monitoringFacts")),
                    strings(
                            result.path(
                                    "predictions")),
                    strings(
                            result.path(
                                    "suggestions")));
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI问答服务暂时不可用，请稍后重试");
        }
    }

    private void validateQuestion(
            String question,
            Long eventId) {

        if (containsAny(question, WRITE_WORDS)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "AI问答仅提供只读分析，不能执行或代替业务操作");
        }

        if (!containsAny(
                question,
                SUPPORTED_WORDS)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "当前仅支持空气质量统计、异常事件、预测和疑似溯源问题");
        }

        if (containsAny(question, EVENT_WORDS)
                && eventId == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "查询具体事件依据时必须提供anomalyEventId");
        }
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

    private String findSource(
            List<AiQuestionResponse.Source> sources,
            String type) {

        return sources.stream()
                .filter(source ->
                        type.equals(source.type()))
                .map(AiQuestionResponse.Source::id)
                .findFirst()
                .orElse(null);
    }

    private String priority(
            AnomalyEvent event) {

        return event.getConfirmedPriority() != null
                ? event.getConfirmedPriority()
                : event.getSuggestedPriority();
    }

    private boolean isAdmin() {
        return SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(
                                authority.getAuthority()));
    }

    private List<String> strings(
            JsonNode array) {

        List<String> values =
                new ArrayList<>();

        array.forEach(item -> {
            if (item.isTextual()
                    && !item.asText().isBlank()) {
                values.add(item.asText());
            }
        });

        return List.copyOf(values);
    }

    private boolean containsAny(
            String question,
            Set<String> words) {

        return words.stream()
                .anyMatch(question::contains);
    }

    private record Context(
            String snapshot,
            List<AiQuestionResponse.Source> sources) {
    }

    private record Answer(
            String answer,
            List<String> monitoringFacts,
            List<String> predictions,
            List<String> suggestions) {
    }
}
