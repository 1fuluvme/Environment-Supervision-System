package com.neps.controller;

import com.neps.entity.Region;
import com.neps.service.UserRegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/regions")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理员授权区域")
public class AdminRegionController {

    private final UserRegionService userRegionService;

    public AdminRegionController(
            UserRegionService userRegionService) {

        this.userRegionService =
                userRegionService;
    }

    @GetMapping
    @Operation(summary = "查询管理员本人授权区域及下级区域")
    public List<Region> mine() {
        return userRegionService.listMyRegions();
    }
}
