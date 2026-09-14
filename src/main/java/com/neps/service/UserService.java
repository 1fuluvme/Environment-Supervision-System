package com.neps.service;

import com.neps.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author neps
 * @since 2026-09-03
 */
public interface UserService extends IService<User> {
    User register(String phone,String displayName,String password);

    User createManagedUser(
            String phone, String displayName, String password, String role);

    User findByPhoneForAdmin(String phone);

    User changeEnabled(Long id, boolean enabled);

    List<User> listForAdmin(String role);
}
