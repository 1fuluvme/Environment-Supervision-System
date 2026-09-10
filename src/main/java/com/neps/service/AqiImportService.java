package com.neps.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.neps.aqi.AqiCalculator;
import com.neps.dto.AqiImportResponse;
import com.neps.dto.AqiImportResponse.RowError;
import com.neps.entity.ExternalAqi;
import com.neps.entity.ImportBatch;
import com.neps.entity.Region;
import com.neps.entity.User;
import com.neps.mapper.ExternalAqiMapper;
import com.neps.mapper.ImportBatchMapper;
import com.neps.mapper.RegionMapper;
import com.neps.mapper.UserMapper;
import com.neps.mapper.UserRegionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AqiImportService {

    private static final long MAX_FILE_SIZE =
            1024L * 1024L;

    private static final int MAX_RECORDS = 1000;

    private static final String HEADER =
            "record_code,region_code,observed_at,"
                    + "report_type,aqi,quality_flag,"
                    + "source_name,is_demo";

    private static final Set<String> REPORT_TYPES =
            Set.of("DAILY", "REALTIME");

    private static final Set<String> QUALITY_FLAGS =
            Set.of("VALID", "INVALID");

    private final ImportBatchMapper importBatchMapper;
    private final ExternalAqiMapper externalAqiMapper;
    private final RegionMapper regionMapper;
    private final UserMapper userMapper;
    private final UserRegionMapper userRegionMapper;

    public AqiImportService(
            ImportBatchMapper importBatchMapper,
            ExternalAqiMapper externalAqiMapper,
            RegionMapper regionMapper,
            UserMapper userMapper,
            UserRegionMapper userRegionMapper) {

        this.importBatchMapper = importBatchMapper;
        this.externalAqiMapper = externalAqiMapper;
        this.regionMapper = regionMapper;
        this.userMapper = userMapper;
        this.userRegionMapper = userRegionMapper;
    }

    public String template() {
        return "\uFEFF" + HEADER + "\n"
                + "AQI-DEMO-001,请替换区域编码,"
                + "2026-08-01T00:00:00,"
                + "DAILY,75,VALID,AUTHORIZED_DEMO,1\n";
    }

    @Transactional
    public AqiImportResponse importCsv(
            MultipartFile file) {

        User admin = currentAdmin();
        byte[] bytes = readFile(file);
        String fileHash = sha256(bytes);

        if (importBatchMapper.selectCount(
                Wrappers.<ImportBatch>lambdaQuery()
                        .eq(ImportBatch::getDataType, "AQI")
                        .eq(ImportBatch::getFileHash, fileHash)) > 0) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "相同内容的AQI文件已经导入");
        }

        String text = decodeUtf8(bytes);

        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }

        String[] lines = text.split("\\R", -1);
        List<RowError> errors = new ArrayList<>();
        List<ParsedRow> rows = new ArrayList<>();
        Set<String> recordKeys = new HashSet<>();

        if (lines.length == 0
                || !HEADER.equals(lines[0].trim())) {

            errors.add(new RowError(
                    1,
                    "表头不正确，应为：" + HEADER));

            return invalid(errors);
        }

        String batchSource = null;
        Boolean batchDemo = null;

        for (int index = 1;
             index < lines.length;
             index++) {

            String line = lines[index];
            int rowNumber = index + 1;

            if (line.isBlank()) {
                continue;
            }

            if (rows.size() >= MAX_RECORDS) {
                errors.add(new RowError(
                        rowNumber,
                        "单个文件不能超过"
                                + MAX_RECORDS
                                + "条数据"));
                break;
            }

            try {
                ParsedRow row = parseRow(
                        line,
                        rowNumber,
                        admin,
                        recordKeys);

                if (batchSource == null) {
                    batchSource = row.sourceName();
                    batchDemo = row.demo();
                } else if (!batchSource.equals(
                        row.sourceName())) {

                    throw new IllegalArgumentException(
                            "同一批文件的source_name必须相同");
                } else if (!batchDemo.equals(row.demo())) {
                    throw new IllegalArgumentException(
                            "同一批文件的is_demo必须相同");
                }

                rows.add(row);
            } catch (IllegalArgumentException exception) {
                errors.add(new RowError(
                        rowNumber,
                        exception.getMessage()));
            }
        }

        if (rows.isEmpty() && errors.isEmpty()) {
            errors.add(new RowError(
                    2,
                    "文件没有数据行"));
        }

        if (!errors.isEmpty()) {
            return invalid(errors);
        }

        String batchNo = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        ImportBatch batch = new ImportBatch();
        batch.setBatchNo(batchNo);
        batch.setDataType("AQI");
        batch.setOriginalName(cleanFilename(
                file.getOriginalFilename()));
        batch.setFileHash(fileHash);
        batch.setRecordCount(rows.size());
        batch.setSourceName(batchSource);
        batch.setIsDemo(flag(batchDemo));
        batch.setImportedBy(admin.getId());
        batch.setImportedAt(now);

        try {
            importBatchMapper.insert(batch);

            for (ParsedRow row : rows) {
                ExternalAqi externalAqi =
                        new ExternalAqi();

                externalAqi.setImportBatchId(
                        batch.getId());
                externalAqi.setRecordCode(
                        row.recordCode());
                externalAqi.setRegionId(
                        row.regionId());
                externalAqi.setObservedAt(
                        row.observedAt());
                externalAqi.setReportType(
                        row.reportType());
                externalAqi.setAqi((short) row.aqi());
                externalAqi.setAqiLevel(
                        (byte) AqiCalculator.level(
                                row.aqi()));
                externalAqi.setAqiCategory(
                        AqiCalculator.category(
                                row.aqi()));
                externalAqi.setQualityFlag(
                        row.qualityFlag());
                externalAqi.setSourceName(
                        row.sourceName());
                externalAqi.setIsDemo(
                        flag(row.demo()));
                externalAqi.setCreatedAt(now);

                externalAqiMapper.insert(externalAqi);
            }
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "导入数据发生重复，请刷新后重试");
        }

        return new AqiImportResponse(
                true,
                batch.getId(),
                batchNo,
                rows.size(),
                List.of());
    }

    private ParsedRow parseRow(
            String line,
            int rowNumber,
            User admin,
            Set<String> recordKeys) {

        if (line.contains("\"")) {
            throw new IllegalArgumentException(
                    "本模板不允许使用引号");
        }

        String[] values = line.split(",", -1);

        if (values.length != 8) {
            throw new IllegalArgumentException(
                    "应当包含8列，实际为"
                            + values.length + "列");
        }

        for (int i = 0; i < values.length; i++) {
            values[i] = values[i].trim();

            if (values[i].isBlank()) {
                throw new IllegalArgumentException(
                        "第" + (i + 1) + "列不能为空");
            }
        }

        String recordCode = values[0];

        if (!recordCode.matches(
                "[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) {

            throw new IllegalArgumentException(
                    "record_code格式不正确");
        }

        Region region = regionMapper.selectOne(
                Wrappers.<Region>lambdaQuery()
                        .eq(Region::getCode, values[1])
                        .eq(Region::getEnabled, 1));

        if (region == null) {
            throw new IllegalArgumentException(
                    "区域编码不存在或已停用");
        }

        if (userRegionMapper.countAccessibleRegion(
                admin.getId(),
                region.getId()) == 0) {

            throw new IllegalArgumentException(
                    "区域不在当前管理员授权范围内");
        }

        LocalDateTime observedAt;

        try {
            observedAt =
                    LocalDateTime.parse(values[2]);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "observed_at必须使用"
                            + "yyyy-MM-ddTHH:mm:ss格式");
        }

        if (observedAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "历史监测时间不能晚于当前时间");
        }

        String reportType =
                values[3].toUpperCase(Locale.ROOT);

        if (!REPORT_TYPES.contains(reportType)) {
            throw new IllegalArgumentException(
                    "report_type只能是DAILY或REALTIME");
        }

        if ("DAILY".equals(reportType)
                && !LocalTime.MIDNIGHT.equals(
                observedAt.toLocalTime())) {

            throw new IllegalArgumentException(
                    "DAILY数据的时间必须为00:00:00");
        }

        int aqi;

        try {
            aqi = Integer.parseInt(values[4]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "aqi必须是整数");
        }

        if (aqi < 0 || aqi > 500) {
            throw new IllegalArgumentException(
                    "aqi必须在0至500之间");
        }

        String qualityFlag =
                values[5].toUpperCase(Locale.ROOT);

        if (!QUALITY_FLAGS.contains(qualityFlag)) {
            throw new IllegalArgumentException(
                    "quality_flag只能是VALID或INVALID");
        }

        String sourceName = values[6];

        if (sourceName.length() > 100
                || sourceName.chars()
                .anyMatch(Character::isISOControl)) {

            throw new IllegalArgumentException(
                    "source_name格式不正确");
        }

        boolean demo;

        if ("1".equals(values[7])) {
            demo = true;
        } else if ("0".equals(values[7])) {
            demo = false;
        } else {
            throw new IllegalArgumentException(
                    "is_demo只能是0或1");
        }

        String recordKey =
                sourceName + "\0" + recordCode;

        if (!recordKeys.add(recordKey)) {
            throw new IllegalArgumentException(
                    "文件中存在重复record_code");
        }

        if (externalAqiMapper.selectCount(
                Wrappers.<ExternalAqi>lambdaQuery()
                        .eq(
                                ExternalAqi::getSourceName,
                                sourceName)
                        .eq(
                                ExternalAqi::getRecordCode,
                                recordCode)) > 0) {

            throw new IllegalArgumentException(
                    "该来源的record_code已经导入");
        }

        return new ParsedRow(
                recordCode,
                region.getId(),
                observedAt,
                reportType,
                aqi,
                qualityFlag,
                sourceName,
                demo);
    }

    private byte[] readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "请选择CSV文件");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "CSV文件不能超过1MB");
        }

        String filename =
                cleanFilename(file.getOriginalFilename());

        if (!filename.toLowerCase(Locale.ROOT)
                .endsWith(".csv")) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "只允许上传CSV文件");
        }

        try {
            return file.getBytes();
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "CSV文件读取失败");
        }
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(
                            CodingErrorAction.REPORT)
                    .onUnmappableCharacter(
                            CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "CSV文件必须使用UTF-8编码");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance(
                                    "SHA-256")
                            .digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "无法计算文件哈希", exception);
        }
    }

    private User currentAdmin() {
        String phone = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User admin = userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getPhone, phone)
                        .eq(User::getRole, "ADMIN")
                        .eq(User::getEnabled, 1));

        if (admin == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "当前管理员账号不可用");
        }

        return admin;
    }

    private String cleanFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unknown.csv";
        }

        String normalized =
                filename.replace('\\', '/');

        return normalized.substring(
                normalized.lastIndexOf('/') + 1);
    }

    private byte flag(boolean value) {
        return value ? (byte) 1 : (byte) 0;
    }

    private AqiImportResponse invalid(
            List<RowError> errors) {

        return new AqiImportResponse(
                false,
                null,
                null,
                0,
                List.copyOf(errors));
    }

    private record ParsedRow(
            String recordCode,
            Long regionId,
            LocalDateTime observedAt,
            String reportType,
            int aqi,
            String qualityFlag,
            String sourceName,
            boolean demo) {
    }
}
