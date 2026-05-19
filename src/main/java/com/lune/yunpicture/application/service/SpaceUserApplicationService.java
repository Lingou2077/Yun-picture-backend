package com.lune.yunpicture.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lune.yunpicture.interfaces.dto.spaceuser.SpaceUserAddRequest;
import com.lune.yunpicture.interfaces.dto.spaceuser.SpaceUserQueryRequest;
import com.lune.yunpicture.domain.space.entity.SpaceUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lune.yunpicture.interfaces.vo.space.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author ljx
 * @description 针对表【space_user(空间用户关联)】的数据库操作Service
 * @createDate 2026-05-09 09:39:36
 */
public interface SpaceUserApplicationService extends IService<SpaceUser> {

    /**
     *
     * 创建空间成员
     *
     * @param spaceUserAddRequest
     * @return
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    /**
     * 空间成员参数校验
     *
     * @param spaceUser 空间成员对象
     * @param add       是否为创建
     */
    void validSpaceUser(SpaceUser spaceUser, boolean add);

    /**
     * 获取脱敏后空间成员列表
     *
     * @param spaceUserList 空间成员列表
     * @return 脱敏后的空间列表
     */
    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);

    /**
     * 空间成员查询条件拼接
     *
     * @param spaceUserQueryRequest 空间查询请求体
     * @return 查询条件
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);
}
