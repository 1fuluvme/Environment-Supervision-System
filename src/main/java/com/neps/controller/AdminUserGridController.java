package com.neps.controller;

import com.neps.service.UserGridService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理员网格分配")
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserGridController {

    private final UserGridService userGridService;

    public AdminUserGridController(UserGridService userGridService) {
        this.userGridService = userGridService;
    }

    @Operation(summary = "为网格员分配负责网格")
    @PostMapping("/{userId}/grids/{gridId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(
            @PathVariable("userId") Long userId,
            @PathVariable("gridId") Long gridId) {

        userGridService.assignGrid(userId, gridId);
    }

    @Operation(summary = "撤销网格员的负责网格")
    @DeleteMapping("/{userId}/grids/{gridId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @PathVariable("userId") Long userId,
            @PathVariable("gridId") Long gridId) {

        userGridService.removeGrid(userId, gridId);
    }
}
