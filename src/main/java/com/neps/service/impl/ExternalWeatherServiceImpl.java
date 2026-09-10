package com.neps.service.impl;

import com.neps.entity.ExternalWeather;
import com.neps.mapper.ExternalWeatherMapper;
import com.neps.service.ExternalWeatherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 导入的气象观测数据 服务实现类
 * </p>
 *
 * @author neps
 * @since 2026-09-10
 */
@Service
public class ExternalWeatherServiceImpl extends ServiceImpl<ExternalWeatherMapper, ExternalWeather> implements ExternalWeatherService {

}
