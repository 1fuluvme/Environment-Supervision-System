package com.neps.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neps.entity.Region;
import com.neps.entity.User;
import com.neps.entity.UserRegion;
import com.neps.mapper.RegionMapper;
import com.neps.mapper.UserMapper;
import com.neps.mapper.UserRegionMapper;
import com.neps.service.UserRegionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UserRegionServiceImpl
        extends ServiceImpl<UserRegionMapper, UserRegion>
        implements UserRegionService {

    private final UserMapper userMapper;
    private final RegionMapper regionMapper;

    public UserRegionServiceImpl(
            UserMapper userMapper, RegionMapper regionMapper) {
        this.userMapper = userMapper;
        this.regionMapper = regionMapper;
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void grantRegion(Long userId, Long regionId) {
        if (userId == null || userId <= 0
                || regionId == null || regionId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "用户和区域 ID 必须是正整数");
        }

        User admin = currentUser("ADMIN");

        User target = userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getId, userId)
                        .last("FOR UPDATE"));

        if (target == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "目标用户不存在");
        }

        if (!"DECISION".equals(target.getRole())
                || !Byte.valueOf((byte) 1).equals(target.getEnabled())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "只能为启用的决策者授权区域");
        }

        Region region = regionMapper.selectOne(
                Wrappers.<Region>lambdaQuery()
                        .eq(Region::getId, regionId)
                        .last("FOR UPDATE"));

        if (region == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "区域不存在");
        }

        if (!Byte.valueOf((byte) 1).equals(region.getEnabled())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "区域已停用");
        }

        if (baseMapper.countAccessibleRegion(
                admin.getId(), regionId) == 0) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "不能授予自己授权范围之外或区域链已停用的区域");
        }

        boolean exists = lambdaQuery()
                .eq(UserRegion::getUserId, userId)
                .eq(UserRegion::getRegionId, regionId)
                .count() > 0;

        if (exists) {
            return;
        }

        UserRegion relation = new UserRegion();
        relation.setUserId(userId);
        relation.setRegionId(regionId);

        if (!save(relation)) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "区域授权失败");
        }
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void removeRegion(Long userId, Long regionId) {
        if (userId == null || userId <= 0
                || regionId == null || regionId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "用户和区域 ID 必须是正整数");
        }

        User admin = currentUser("ADMIN");

        User target = userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getId, userId)
                        .last("FOR UPDATE"));

        if (target == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "目标用户不存在");
        }

        if (!"DECISION".equals(target.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "只能撤销决策者的区域授权");
        }

        Region region = regionMapper.selectOne(
                Wrappers.<Region>lambdaQuery()
                        .eq(Region::getId, regionId)
                        .last("FOR UPDATE"));

        if (region == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "区域不存在");
        }

        if (baseMapper.countAccessibleRegion(
                admin.getId(), regionId) == 0) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "不能撤销自己授权范围之外或区域链已停用的区域");
        }

        remove(Wrappers.<UserRegion>lambdaQuery()
                .eq(UserRegion::getUserId, userId)
                .eq(UserRegion::getRegionId, regionId));
    }

    @Override
    @PreAuthorize("hasRole('DECISION')")
    public List<Region> listMyRegions() {
        User user = currentUser("DECISION");
        return baseMapper.selectMyRegions(user.getId());
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
}
