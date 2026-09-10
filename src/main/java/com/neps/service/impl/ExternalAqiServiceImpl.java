package com.neps.service.impl;

import com.neps.entity.ExternalAqi;
import com.neps.mapper.ExternalAqiMapper;
import com.neps.service.ExternalAqiService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 导入的历史AQI数据 服务实现类
 * </p>
 *
 * @author neps
 * @since 2026-09-10
 */
@Service
public class ExternalAqiServiceImpl extends ServiceImpl<ExternalAqiMapper, ExternalAqi> implements ExternalAqiService {

}
