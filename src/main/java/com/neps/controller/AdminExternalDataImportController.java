package com.neps.controller;

import com.neps.dto.AqiImportResponse;
import com.neps.service.ExternalDataImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/admin/imports")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理员外部数据导入")
public class AdminExternalDataImportController {

    private final ExternalDataImportService importService;

    public AdminExternalDataImportController(ExternalDataImportService importService) {
        this.importService = importService;
    }

    @Operation(summary = "下载气象数据CSV模板")
    @GetMapping(value = "/weather/template", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> weatherTemplate() {
        return template("WEATHER", "weather-template.csv");
    }

    @Operation(summary = "导入气象数据CSV文件")
    @PostMapping(value = "/weather", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AqiImportResponse> importWeather(@RequestPart("file") MultipartFile file) {
        return upload("WEATHER", file);
    }

    @Operation(summary = "下载企业排污数据CSV模板")
    @GetMapping(value = "/emission/template", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> emissionTemplate() {
        return template("EMISSION", "emission-template.csv");
    }

    @Operation(summary = "导入企业排污数据CSV文件")
    @PostMapping(value = "/emission", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AqiImportResponse> importEmission(@RequestPart("file") MultipartFile file) {
        return upload("EMISSION", file);
    }

    @Operation(summary = "下载交通流量数据CSV模板")
    @GetMapping(value = "/traffic/template", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> trafficTemplate() {
        return template("TRAFFIC", "traffic-template.csv");
    }

    @Operation(summary = "导入交通流量数据CSV文件")
    @PostMapping(value = "/traffic", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AqiImportResponse> importTraffic(@RequestPart("file") MultipartFile file) {
        return upload("TRAFFIC", file);
    }

    private ResponseEntity<String> template(String type, String filename) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(importService.template(type));
    }

    private ResponseEntity<AqiImportResponse> upload(String type, MultipartFile file) {
        AqiImportResponse result = importService.importCsv(type, file);
        return ResponseEntity.status(result.success() ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST)
                .body(result);
    }
}
