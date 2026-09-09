package com.neps.controller;

import com.neps.entity.Region;
import com.neps.service.UserRegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "决策者授权区域")
@RestController
@RequestMapping("/api/decision/regions")
public class DecisionRegionController {

    private final UserRegionService userRegionService;

    public DecisionRegionController(UserRegionService userRegionService) {
        this.userRegionService = userRegionService;
    }

    @Operation(summary = "查询本人有效授权区域及下级区域")
    @GetMapping
    public List<Region> mine() {
        return userRegionService.listMyRegions();
    }
}