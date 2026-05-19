package com.lune.yunpicture.domain.space.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lune.yunpicture.domain.space.entity.Space;
import com.lune.yunpicture.domain.user.entity.User;
import com.lune.yunpicture.interfaces.dto.space.SpaceAddRequest;
import com.lune.yunpicture.interfaces.dto.space.SpaceQueryRequest;
import com.lune.yunpicture.interfaces.vo.space.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
 * @author ljx
 * @description 针对表【space(空间)】的数据库操作Service
 * @createDate 2026-04-19 16:23:32
 */
public interface SpaceDomainService {

    /**
     * 空间查询条件拼接
     *
     * @param spaceQueryRequest 空间查询请求体
     * @return 查询条件
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 根据空间等级填充空间对象
     *
     * @param space 空间对象
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 校验空间权限
     *
     * @param loginUser
     * @param space
     */
    void checkSpaceAuth(User loginUser, Space space);
}
