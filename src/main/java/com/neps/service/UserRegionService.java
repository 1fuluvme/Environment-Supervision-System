package com.neps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neps.entity.Region;
import com.neps.entity.UserRegion;

import java.util.List;

public interface UserRegionService extends IService<UserRegion> {

    void grantRegion(Long userId, Long regionId);

    void removeRegion(Long userId, Long regionId);

    List<Region> listMyRegions();
}
