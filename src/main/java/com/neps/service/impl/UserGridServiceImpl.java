package com.neps.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neps.entity.Grid;
import com.neps.entity.User;
import com.neps.entity.UserGrid;
import com.neps.mapper.GridMapper;
import com.neps.mapper.UserGridMapper;
import com.neps.mapper.UserMapper;
import com.neps.mapper.UserRegionMapper;
import com.neps.service.UserGridService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UserGridServiceImpl
        extends ServiceImpl<UserGridMapper, UserGrid>
        implements UserGridService {

    private final UserMapper userMapper;
    private final GridMapper gridMapper;
    private final UserRegionMapper userRegionMapper;

    public UserGridServiceImpl(
            UserMapper userMapper,
            GridMapper gridMapper,
            UserRegionMapper userRegionMapper) {
        this.userMapper = userMapper;
        this.gridMapper = gridMapper;
        this.userRegionMapper = userRegionMapper;
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void assignGrid(Long userId, Long gridId) {
        User target = lockWorker(userId);
        Grid grid = lockAuthorizedGrid(gridId);

        if (!Byte.valueOf((byte) 1).equals(target.getEnabled())
                || !Byte.valueOf((byte) 1).equals(grid.getEnabled())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "网格员和网格必须处于启用状态");
        }

        boolean exists = lambdaQuery()
                .eq(UserGrid::getUserId, userId)
                .eq(UserGrid::getGridId, gridId)
                .count() > 0;

        if (exists) {
            return;
        }

        UserGrid relation = new UserGrid();
        relation.setUserId(userId);
        relation.setGridId(gridId);

        if (!save(relation)) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "网格分配失败");
        }
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void removeGrid(Long userId, Long gridId) {
        lockWorker(userId);
        lockAuthorizedGrid(gridId);

        lambdaUpdate()
                .eq(UserGrid::getUserId, userId)
                .eq(UserGrid::getGridId, gridId)
                .remove();
    }

    @Override
    @PreAuthorize("hasRole('GRID')")
    public List<Grid> listMyGrids() {
        User worker = currentUser("GRID");
        return baseMapper.selectMyGrids(worker.getId());
    }

    private User currentUser(String role) {
        String phone = SecurityContextHolder.getContext()
                .getAuthentication().getName();

        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getPhone, phone)
                        .eq(User::getRole, role)
                        .eq(User::getEnabled, 1));

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "当前账号不可用");
        }

        return user;
    }

    private User lockWorker(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "用户 ID 必须是正整数");
        }

        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getId, userId)
                        .last("FOR UPDATE"));

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "目标用户不存在");
        }

        if (!"GRID".equals(user.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "目标用户必须是网格员");
        }

        return user;
    }

    private Grid lockAuthorizedGrid(Long gridId) {
        if (gridId == null || gridId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "网格 ID 必须是正整数");
        }

        User admin = currentUser("ADMIN");

        Grid grid = gridMapper.selectOne(
                Wrappers.<Grid>lambdaQuery()
                        .eq(Grid::getId, gridId)
                        .last("FOR UPDATE"));

        if (grid == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "网格不存在");
        }

        if (userRegionMapper.countAccessibleRegion(
                admin.getId(), grid.getRegionId()) == 0) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "网格不在你的授权范围内，或所属区域链存在停用区域");
        }

        return grid;
    }
}