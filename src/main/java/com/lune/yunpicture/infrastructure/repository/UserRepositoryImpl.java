package com.lune.yunpicture.infrastructure.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lune.yunpicture.domain.user.entity.User;
import com.lune.yunpicture.domain.user.repository.UserRepository;
import com.lune.yunpicture.infrastructure.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * 用户仓库实现类
 */
@Service
public class UserRepositoryImpl extends ServiceImpl<UserMapper, User> implements UserRepository {
}
