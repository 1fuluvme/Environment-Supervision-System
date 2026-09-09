package com.neps.service.impl;

import com.neps.entity.OperationLog;
import com.neps.mapper.OperationLogMapper;
import com.neps.service.OperationLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 业务操作记录 服务实现类
 * </p>
 *
 * @author neps
 * @since 2026-09-07
 */
@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {

}
