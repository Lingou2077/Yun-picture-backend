package com.lune.yunpicture.infrastructure.repository;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lune.yunpicture.domain.space.entity.SpaceUser;
import com.lune.yunpicture.domain.space.repository.SpaceUserRepository;
import com.lune.yunpicture.infrastructure.mapper.SpaceUserMapper;
import org.springframework.stereotype.Service;

/**
 * 空间成员仓储实现类
 */
@Service
public class SpaceUserRepositoryImpl extends ServiceImpl<SpaceUserMapper, SpaceUser> implements SpaceUserRepository {
}
