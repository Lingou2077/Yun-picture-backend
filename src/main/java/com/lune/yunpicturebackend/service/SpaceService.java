package com.lune.yunpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lune.yunpicturebackend.model.dto.space.SpaceAddRequest;
import com.lune.yunpicturebackend.model.dto.space.SpaceQueryRequest;
import com.lune.yunpicturebackend.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lune.yunpicturebackend.model.entity.User;
import com.lune.yunpicturebackend.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
 * @author ljx
 * @description 针对表【space(空间)】的数据库操作Service
 * @createDate 2026-04-19 16:23:32
 */
public interface SpaceService extends IService<Space> {

    /**
     *
     * 创建空间
     *
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    /**
     * 空间参数校验
     *
     * @param space 空间对象
     * @param add   是否为创建
     */
    void validSpace(Space space, boolean add);

    /**
     * 获取脱敏后的空间信息（单条）
     *
     * @param space   空间对象
     * @param request 请求对象
     * @return 脱敏后的空间信息
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);


    /**
     * 获取脱敏后空间列表（分页）
     *
     * @param spacePage 空间分页对象
     * @param request   请求对象
     * @return 脱敏后的空间列表
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

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
