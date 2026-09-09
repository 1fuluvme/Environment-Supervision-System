package com.neps.controller;

import com.neps.entity.Grid;
import com.neps.service.UserGridService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "网格员负责网格")
@RestController
@RequestMapping("/api/grid/grids")
public class WorkerGridController {

    private final UserGridService userGridService;

    public WorkerGridController(UserGridService userGridService) {
        this.userGridService = userGridService;
    }

    @Operation(summary = "查询本人负责的启用网格")
    @GetMapping
    public List<Grid> mine() {
        return userGridService.listMyGrids();
    }
}
