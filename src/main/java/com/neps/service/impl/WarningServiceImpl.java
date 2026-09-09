package com.neps.service.impl;

import com.neps.entity.Warning;
import com.neps.mapper.WarningMapper;
import com.neps.service.WarningService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 内部污染预警 服务实现类
 * </p>
 *
 * @author neps
 * @since 2026-09-09
 */
@Service
public class WarningServiceImpl extends ServiceImpl<WarningMapper, Warning> implements WarningService {

}
