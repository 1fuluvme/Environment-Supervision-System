package com.neps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neps.entity.Grid;
import com.neps.entity.UserGrid;

import java.util.List;

public interface UserGridService extends IService<UserGrid> {

    void assignGrid(Long userId, Long gridId);

    void removeGrid(Long userId, Long gridId);

    List<Grid> listMyGrids();
}
