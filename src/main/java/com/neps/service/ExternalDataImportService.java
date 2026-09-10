package com.neps.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.neps.dto.AqiImportResponse;
import com.neps.dto.AqiImportResponse.RowError;
import com.neps.entity.*;
import com.neps.mapper.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ExternalDataImportService {

    private static final long MAX_FILE_SIZE = 1024L * 1024L;
    private static final int MAX_RECORDS = 1000;
    private static final Set<String> TYPES = Set.of("WEATHER", "EMISSION", "TRAFFIC");
    private static final Set<String> QUALITY_FLAGS = Set.of("VALID", "INVALID");
    private static final Set<String> POLLUTANTS =
            Set.of("SO2", "NO2", "CO", "O3", "PM10", "PM25", "VOC", "OTHER");
    private static final Set<String> CONGESTION_LEVELS = Set.of("LOW", "MEDIUM", "HIGH");

    private static final String WEATHER_HEADER =
            "record_code,region_code,observed_at,wind_direction_deg,wind_speed_mps,temperature_c,humidity_pct,quality_flag,source_name,is_demo";
    private static final String EMISSION_HEADER =
            "record_code,region_code,enterprise_code,enterprise_name,longitude,latitude,observed_at,pollutant_code,emission_value,emission_unit,quality_flag,source_name,is_demo";
    private static final String TRAFFIC_HEADER =
            "record_code,region_code,road_code,road_name,longitude,latitude,observed_at,traffic_flow_per_hour,congestion_level,quality_flag,source_name,is_demo";

    private final ImportBatchMapper importBatchMapper;
    private final ExternalWeatherMapper weatherMapper;
    private final ExternalEmissionMapper emissionMapper;
    private final ExternalTrafficMapper trafficMapper;
    private final RegionMapper regionMapper;
    private final UserMapper userMapper;
    private final UserRegionMapper userRegionMapper;

    public ExternalDataImportService(
            ImportBatchMapper importBatchMapper,
            ExternalWeatherMapper weatherMapper,
            ExternalEmissionMapper emissionMapper,
            ExternalTrafficMapper trafficMapper,
            RegionMapper regionMapper,
            UserMapper userMapper,
            UserRegionMapper userRegionMapper) {
        this.importBatchMapper = importBatchMapper;
        this.weatherMapper = weatherMapper;
        this.emissionMapper = emissionMapper;
        this.trafficMapper = trafficMapper;
        this.regionMapper = regionMapper;
        this.userMapper = userMapper;
        this.userRegionMapper = userRegionMapper;
    }

    public String template(String type) {
        return switch (checkedType(type)) {
            case "WEATHER" -> "\uFEFF" + WEATHER_HEADER + "\n"
                    + "WEATHER-DEMO-001,请替换区域编码,2026-08-01T12:00:00,90,3.5,28.5,65,VALID,AUTHORIZED_DEMO,1\n";
            case "EMISSION" -> "\uFEFF" + EMISSION_HEADER + "\n"
                    + "EMISSION-DEMO-001,请替换区域编码,ENT-001,演示企业,120.100000,30.200000,2026-08-01T12:00:00,SO2,12.5,kg/h,VALID,AUTHORIZED_DEMO,1\n";
            case "TRAFFIC" -> "\uFEFF" + TRAFFIC_HEADER + "\n"
                    + "TRAFFIC-DEMO-001,请替换区域编码,ROAD-001,演示道路,120.100000,30.200000,2026-08-01T12:00:00,800,MEDIUM,VALID,AUTHORIZED_DEMO,1\n";
            default -> throw new IllegalStateException();
        };
    }

    @Transactional
    public AqiImportResponse importCsv(String type, MultipartFile file) {
        type = checkedType(type);
        User admin = currentAdmin();
        byte[] bytes = readFile(file);
        String fileHash = sha256(bytes);

        if (importBatchMapper.selectCount(Wrappers.<ImportBatch>lambdaQuery()
                .eq(ImportBatch::getDataType, type)
                .eq(ImportBatch::getFileHash, fileHash)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "相同内容的" + type + "文件已经导入");
        }

        String text = decodeUtf8(bytes);
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        String[] lines = text.split("\\R", -1);
        List<RowError> errors = new ArrayList<>();
        List<Object> rows = new ArrayList<>();
        Set<String> recordKeys = new HashSet<>();

        if (lines.length == 0 || !header(type).equals(lines[0].trim())) {
            return invalid(List.of(new RowError(1, "表头不正确，应为：" + header(type))));
        }

        String batchSource = null;
        Byte batchDemo = null;
        int dataRows = 0;
        for (int index = 1; index < lines.length; index++) {
            if (lines[index].isBlank()) continue;
            dataRows++;
            int rowNumber = index + 1;
            if (dataRows > MAX_RECORDS) {
                errors.add(new RowError(rowNumber, "单个文件不能超过" + MAX_RECORDS + "条数据"));
                break;
            }
            try {
                Object row = parse(type, lines[index], admin, recordKeys);
                String source = sourceName(row);
                byte demo = demo(row);
                if (batchSource == null) {
                    batchSource = source;
                    batchDemo = demo;
                } else if (!batchSource.equals(source)) {
                    throw new IllegalArgumentException("同一批文件的source_name必须相同");
                } else if (!batchDemo.equals(demo)) {
                    throw new IllegalArgumentException("同一批文件的is_demo必须相同");
                }
                rows.add(row);
            } catch (IllegalArgumentException exception) {
                errors.add(new RowError(rowNumber, exception.getMessage()));
            }
        }

        if (dataRows == 0) errors.add(new RowError(2, "文件没有数据行"));
        if (!errors.isEmpty()) return invalid(errors);

        String batchNo = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        ImportBatch batch = new ImportBatch();
        batch.setBatchNo(batchNo);
        batch.setDataType(type);
        batch.setOriginalName(cleanFilename(file.getOriginalFilename()));
        batch.setFileHash(fileHash);
        batch.setRecordCount(rows.size());
        batch.setSourceName(batchSource);
        batch.setIsDemo(batchDemo);
        batch.setImportedBy(admin.getId());
        batch.setImportedAt(now);

        try {
            importBatchMapper.insert(batch);
            for (Object row : rows) insert(row, batch.getId(), now);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "导入数据发生重复，请刷新后重试");
        }

        return new AqiImportResponse(true, batch.getId(), batchNo, rows.size(), List.of());
    }

    private Object parse(String type, String line, User admin, Set<String> recordKeys) {
        if (line.contains("\"")) throw new IllegalArgumentException("本模板不允许使用引号");
        String[] values = line.split(",", -1);
        int expected = switch (type) {
            case "WEATHER" -> 10;
            case "EMISSION" -> 13;
            case "TRAFFIC" -> 12;
            default -> throw new IllegalStateException();
        };
        if (values.length != expected) {
            throw new IllegalArgumentException("应当包含" + expected + "列，实际为" + values.length + "列");
        }
        for (int i = 0; i < values.length; i++) {
            values[i] = values[i].trim();
            if (values[i].isBlank()) throw new IllegalArgumentException("第" + (i + 1) + "列不能为空");
        }

        return switch (type) {
            case "WEATHER" -> parseWeather(values, admin, recordKeys);
            case "EMISSION" -> parseEmission(values, admin, recordKeys);
            case "TRAFFIC" -> parseTraffic(values, admin, recordKeys);
            default -> throw new IllegalStateException();
        };
    }

    private ExternalWeather parseWeather(String[] v, User admin, Set<String> keys) {
        Common common = common(v, 2, 7, 8, 9, admin, keys, "WEATHER");
        BigDecimal direction = decimal(v[3], "wind_direction_deg");
        BigDecimal speed = decimal(v[4], "wind_speed_mps");
        BigDecimal temperature = decimal(v[5], "temperature_c");
        BigDecimal humidity = decimal(v[6], "humidity_pct");
        range(direction, BigDecimal.ZERO, new BigDecimal("360"), false, "wind_direction_deg必须在0至360之间且不含360");
        nonNegative(speed, "wind_speed_mps不能小于0");
        range(temperature, new BigDecimal("-80"), new BigDecimal("80"), true, "temperature_c必须在-80至80之间");
        range(humidity, BigDecimal.ZERO, new BigDecimal("100"), true, "humidity_pct必须在0至100之间");

        ExternalWeather row = new ExternalWeather();
        fillWeatherCommon(row, common);
        row.setWindDirection(direction);
        row.setWindSpeed(speed);
        row.setTemperature(temperature);
        row.setHumidity(humidity);
        return row;
    }

    private ExternalEmission parseEmission(String[] v, User admin, Set<String> keys) {
        Common common = common(v, 6, 10, 11, 12, admin, keys, "EMISSION");
        simpleCode(v[2], "enterprise_code");
        text(v[3], 100, "enterprise_name");
        BigDecimal longitude = decimal(v[4], "longitude");
        BigDecimal latitude = decimal(v[5], "latitude");
        range(longitude, new BigDecimal("-180"), new BigDecimal("180"), true, "longitude必须在-180至180之间");
        range(latitude, new BigDecimal("-90"), new BigDecimal("90"), true, "latitude必须在-90至90之间");
        String pollutant = v[7].toUpperCase(Locale.ROOT);
        if (!POLLUTANTS.contains(pollutant)) throw new IllegalArgumentException("pollutant_code不受支持");
        BigDecimal value = decimal(v[8], "emission_value");
        nonNegative(value, "emission_value不能小于0");
        if (!"kg/h".equals(v[9])) throw new IllegalArgumentException("emission_unit目前只支持kg/h");

        ExternalEmission row = new ExternalEmission();
        fillEmissionCommon(row, common);
        row.setEnterpriseCode(v[2]);
        row.setEnterpriseName(v[3]);
        row.setLongitude(longitude);
        row.setLatitude(latitude);
        row.setPollutantCode(pollutant);
        row.setEmissionValue(value);
        row.setEmissionUnit(v[9]);
        return row;
    }

    private ExternalTraffic parseTraffic(String[] v, User admin, Set<String> keys) {
        Common common = common(v, 6, 9, 10, 11, admin, keys, "TRAFFIC");
        simpleCode(v[2], "road_code");
        text(v[3], 100, "road_name");
        BigDecimal longitude = decimal(v[4], "longitude");
        BigDecimal latitude = decimal(v[5], "latitude");
        range(longitude, new BigDecimal("-180"), new BigDecimal("180"), true, "longitude必须在-180至180之间");
        range(latitude, new BigDecimal("-90"), new BigDecimal("90"), true, "latitude必须在-90至90之间");
        int flow;
        try {
            flow = Integer.parseInt(v[7]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("traffic_flow_per_hour必须是整数");
        }
        if (flow < 0) throw new IllegalArgumentException("traffic_flow_per_hour不能小于0");
        String congestion = v[8].toUpperCase(Locale.ROOT);
        if (!CONGESTION_LEVELS.contains(congestion)) {
            throw new IllegalArgumentException("congestion_level只能是LOW、MEDIUM或HIGH");
        }

        ExternalTraffic row = new ExternalTraffic();
        fillTrafficCommon(row, common);
        row.setRoadCode(v[2]);
        row.setRoadName(v[3]);
        row.setLongitude(longitude);
        row.setLatitude(latitude);
        row.setTrafficFlow(flow);
        row.setCongestionLevel(congestion);
        return row;
    }

    private Common common(String[] v, int timeIndex, int qualityIndex, int sourceIndex,
                          int demoIndex, User admin, Set<String> keys, String type) {
        simpleCode(v[0], "record_code");
        Region region = regionMapper.selectOne(Wrappers.<Region>lambdaQuery()
                .eq(Region::getCode, v[1]).eq(Region::getEnabled, 1));
        if (region == null) throw new IllegalArgumentException("区域编码不存在或已停用");
        if (userRegionMapper.countAccessibleRegion(admin.getId(), region.getId()) == 0) {
            throw new IllegalArgumentException("区域不在当前管理员授权范围内");
        }

        LocalDateTime observedAt;
        try {
            observedAt = LocalDateTime.parse(v[timeIndex]);
        } catch (Exception exception) {
            throw new IllegalArgumentException("observed_at必须使用yyyy-MM-ddTHH:mm:ss格式");
        }
        if (observedAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("历史数据时间不能晚于当前时间");
        }
        String quality = v[qualityIndex].toUpperCase(Locale.ROOT);
        if (!QUALITY_FLAGS.contains(quality)) {
            throw new IllegalArgumentException("quality_flag只能是VALID或INVALID");
        }
        text(v[sourceIndex], 100, "source_name");
        byte demo;
        if ("1".equals(v[demoIndex])) demo = 1;
        else if ("0".equals(v[demoIndex])) demo = 0;
        else throw new IllegalArgumentException("is_demo只能是0或1");

        String key = v[sourceIndex] + "\0" + v[0];
        if (!keys.add(key)) throw new IllegalArgumentException("文件中存在重复record_code");
        long existing = switch (type) {
            case "WEATHER" -> weatherMapper.selectCount(Wrappers.<ExternalWeather>lambdaQuery()
                    .eq(ExternalWeather::getSourceName, v[sourceIndex]).eq(ExternalWeather::getRecordCode, v[0]));
            case "EMISSION" -> emissionMapper.selectCount(Wrappers.<ExternalEmission>lambdaQuery()
                    .eq(ExternalEmission::getSourceName, v[sourceIndex]).eq(ExternalEmission::getRecordCode, v[0]));
            case "TRAFFIC" -> trafficMapper.selectCount(Wrappers.<ExternalTraffic>lambdaQuery()
                    .eq(ExternalTraffic::getSourceName, v[sourceIndex]).eq(ExternalTraffic::getRecordCode, v[0]));
            default -> throw new IllegalStateException();
        };
        if (existing > 0) throw new IllegalArgumentException("该来源的record_code已经导入");
        return new Common(v[0], region.getId(), observedAt, quality, v[sourceIndex], demo);
    }

    private void fillWeatherCommon(ExternalWeather row, Common c) {
        row.setRecordCode(c.recordCode()); row.setRegionId(c.regionId()); row.setObservedAt(c.observedAt());
        row.setQualityFlag(c.quality()); row.setSourceName(c.source()); row.setIsDemo(c.demo());
    }

    private void fillEmissionCommon(ExternalEmission row, Common c) {
        row.setRecordCode(c.recordCode()); row.setRegionId(c.regionId()); row.setObservedAt(c.observedAt());
        row.setQualityFlag(c.quality()); row.setSourceName(c.source()); row.setIsDemo(c.demo());
    }

    private void fillTrafficCommon(ExternalTraffic row, Common c) {
        row.setRecordCode(c.recordCode()); row.setRegionId(c.regionId()); row.setObservedAt(c.observedAt());
        row.setQualityFlag(c.quality()); row.setSourceName(c.source()); row.setIsDemo(c.demo());
    }

    private void insert(Object row, Long batchId, LocalDateTime now) {
        if (row instanceof ExternalWeather value) {
            value.setImportBatchId(batchId); value.setCreatedAt(now); weatherMapper.insert(value);
        } else if (row instanceof ExternalEmission value) {
            value.setImportBatchId(batchId); value.setCreatedAt(now); emissionMapper.insert(value);
        } else if (row instanceof ExternalTraffic value) {
            value.setImportBatchId(batchId); value.setCreatedAt(now); trafficMapper.insert(value);
        } else {
            throw new IllegalStateException("未知导入数据类型");
        }
    }

    private String sourceName(Object row) {
        if (row instanceof ExternalWeather value) return value.getSourceName();
        if (row instanceof ExternalEmission value) return value.getSourceName();
        if (row instanceof ExternalTraffic value) return value.getSourceName();
        throw new IllegalStateException("未知导入数据类型");
    }

    private byte demo(Object row) {
        if (row instanceof ExternalWeather value) return value.getIsDemo();
        if (row instanceof ExternalEmission value) return value.getIsDemo();
        if (row instanceof ExternalTraffic value) return value.getIsDemo();
        throw new IllegalStateException("未知导入数据类型");
    }

    private String checkedType(String type) {
        String value = type == null ? "" : type.toUpperCase(Locale.ROOT);
        if (!TYPES.contains(value)) throw new IllegalArgumentException("不支持的数据类型");
        return value;
    }

    private String header(String type) {
        return switch (type) {
            case "WEATHER" -> WEATHER_HEADER;
            case "EMISSION" -> EMISSION_HEADER;
            case "TRAFFIC" -> TRAFFIC_HEADER;
            default -> throw new IllegalStateException();
        };
    }

    private void simpleCode(String value, String name) {
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) {
            throw new IllegalArgumentException(name + "格式不正确");
        }
    }

    private void text(String value, int maxLength, String name) {
        if (value.length() > maxLength || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + "格式不正确");
        }
    }

    private BigDecimal decimal(String value, String name) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + "必须是数字");
        }
    }

    private void nonNegative(BigDecimal value, String message) {
        if (value.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException(message);
    }

    private void range(BigDecimal value, BigDecimal min, BigDecimal max, boolean includeMax, String message) {
        if (value.compareTo(min) < 0 || (includeMax ? value.compareTo(max) > 0 : value.compareTo(max) >= 0)) {
            throw new IllegalArgumentException(message);
        }
    }

    private byte[] readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择CSV文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "CSV文件不能超过1MB");
        }
        if (!cleanFilename(file.getOriginalFilename()).toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只允许上传CSV文件");
        }
        try {
            return file.getBytes();
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV文件读取失败");
        }
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV文件必须使用UTF-8编码");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算文件哈希", exception);
        }
    }

    private User currentAdmin() {
        String phone = SecurityContextHolder.getContext().getAuthentication().getName();
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getPhone, phone).eq(User::getRole, "ADMIN").eq(User::getEnabled, 1));
        if (admin == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前管理员账号不可用");
        }
        return admin;
    }

    private String cleanFilename(String filename) {
        if (filename == null || filename.isBlank()) return "unknown.csv";
        String normalized = filename.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private AqiImportResponse invalid(List<RowError> errors) {
        return new AqiImportResponse(false, null, null, 0, List.copyOf(errors));
    }

    private record Common(String recordCode, Long regionId, LocalDateTime observedAt,
                          String quality, String source, byte demo) {}
}
