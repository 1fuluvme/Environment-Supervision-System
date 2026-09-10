package com.neps.service.impl;

import com.neps.entity.ExternalTraffic;
import com.neps.mapper.ExternalTrafficMapper;
import com.neps.service.ExternalTrafficService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 导入的交通流量数据 服务实现类
 * </p>
 *
 * @author neps
 * @since 2026-09-10
 */
@Service
public class ExternalTrafficServiceImpl extends ServiceImpl<ExternalTrafficMapper, ExternalTraffic> implements ExternalTrafficService {

}
