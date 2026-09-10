package com.neps.controller;

import com.neps.dto.AqiImportResponse;
import com.neps.service.AqiImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/admin/imports")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理员外部数据导入")
public class AdminAqiImportController {

    private final AqiImportService aqiImportService;

    public AdminAqiImportController(
            AqiImportService aqiImportService) {

        this.aqiImportService = aqiImportService;
    }

    @Operation(summary = "下载历史AQI CSV模板")
    @GetMapping(
            value = "/aqi/template",
            produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> template() {

        ContentDisposition disposition =
                ContentDisposition.attachment()
                        .filename(
                                "historical-aqi-template.csv",
                                StandardCharsets.UTF_8)
                        .build();

        return ResponseEntity.ok()
                .contentType(new MediaType(
                        "text",
                        "csv",
                        StandardCharsets.UTF_8))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString())
                .body(aqiImportService.template());
    }

    @Operation(summary = "导入历史AQI CSV文件")
    @PostMapping(
            value = "/aqi",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AqiImportResponse> importAqi(
            @RequestPart("file")
            MultipartFile file) {

        AqiImportResponse result =
                aqiImportService.importCsv(file);

        return ResponseEntity
                .status(
                        result.success()
                                ? HttpStatus.CREATED
                                : HttpStatus.BAD_REQUEST)
                .body(result);
    }
}
