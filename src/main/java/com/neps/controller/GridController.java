package com.neps.controller;

import com.neps.entity.Grid;
import com.neps.service.GridService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Tag(name= "网格查询")
@RestController
@RequestMapping("api/grids")
public class GridController {
    private final GridService gridService;

    public GridController(GridService gridService) {
        this.gridService = gridService;
    }

    @Operation(summary = "查询启用的网格列表")
    @GetMapping
    public List<Grid> getGrids() {
        return gridService.lambdaQuery()
                .eq(Grid::getEnabled,1)
                .orderByAsc(Grid::getId)
                .list();
    }

    @Operation(summary = "按id查询启用的网格")
    @GetMapping("/{id}")
    public Grid getByid(@PathVariable("id") Long id) {
        if(id<0){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "网格ID必须是整数");
        }

        Grid grid = gridService.lambdaQuery()
                .eq(Grid::getId, id)
                .eq(Grid::getEnabled, 1)
                .one();

        if(grid==null){
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "网格不存在或已停用");
        }

        return grid;
    }
}
