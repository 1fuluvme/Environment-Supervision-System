package com.neps.controller;

import com.neps.service.UserRegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理员区域授权")
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserRegionController {

    private final UserRegionService userRegionService;

    public AdminUserRegionController(UserRegionService userRegionService) {
        this.userRegionService = userRegionService;
    }

    @Operation(summary = "为决策者授权区域")
    @PostMapping("/{userId}/regions/{regionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grant(
            @PathVariable("userId") Long userId,
            @PathVariable("regionId") Long regionId) {

        userRegionService.grantRegion(userId, regionId);
    }

    @Operation(summary = "撤销决策者的区域授权")
    @DeleteMapping("/{userId}/regions/{regionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @PathVariable("userId") Long userId,
            @PathVariable("regionId") Long regionId) {

        userRegionService.removeRegion(userId, regionId);
    }
}
