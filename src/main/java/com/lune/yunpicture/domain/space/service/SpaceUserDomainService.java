package com.lune.yunpicture.domain.space.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lune.yunpicture.domain.space.entity.SpaceUser;
import com.lune.yunpicture.interfaces.dto.spaceuser.SpaceUserAddRequest;
import com.lune.yunpicture.interfaces.dto.spaceuser.SpaceUserQueryRequest;
import com.lune.yunpicture.interfaces.vo.space.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author ljx
 * @description 针对表【space_user(空间用户关联)】的数据库操作Service
 * @createDate 2026-05-09 09:39:36
 */
public interface SpaceUserDomainService {

    /**
     * 空间成员查询条件拼接
     *
     * @param spaceUserQueryRequest 空间查询请求体
     * @return 查询条件
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);
}
