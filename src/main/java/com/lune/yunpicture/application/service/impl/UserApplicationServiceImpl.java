package com.lune.yunpicture.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lune.yunpicture.domain.user.service.UserDomainService;
import com.lune.yunpicture.infrastructure.common.DeleteRequest;
import com.lune.yunpicture.infrastructure.exception.BusinessException;
import com.lune.yunpicture.infrastructure.exception.ErrorCode;
import com.lune.yunpicture.infrastructure.exception.ThrowUtils;
import com.lune.yunpicture.interfaces.dto.user.UserLoginRequest;
import com.lune.yunpicture.interfaces.dto.user.UserQueryRequest;
import com.lune.yunpicture.interfaces.dto.user.UserRegisterRequest;
import com.lune.yunpicture.domain.user.entity.User;
import com.lune.yunpicture.interfaces.vo.user.LoginUserVO;
import com.lune.yunpicture.interfaces.vo.user.UserVO;
import com.lune.yunpicture.application.service.UserApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

/**
 * @author ljx
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2026-04-10 21:21:06
 */
@Service
@Slf4j
public class UserApplicationServiceImpl implements UserApplicationService {

    // 上层调用下层（应用服务层调用领域服务层）
    @Resource
    private UserDomainService userDomainService;

    /**
     * 用户注册
     *
     * @param userRegisterRequest (账号、密码、确认密码)
     * @return
     */
    @Override
    public long userRegister(UserRegisterRequest userRegisterRequest) {
        // 1. 校验参数
        User.validUserRegister(userRegisterRequest);
        // 2. 执行注册
        return userDomainService.userRegister(userRegisterRequest);
    }

    @Override
    public LoginUserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request) {
        // 1. 校验参数
        User.validUserLogin(userLoginRequest);
        // 2. 执行登录
        return userDomainService.userLogin(userLoginRequest, request);
    }

    /**
     * 获取加密后的密码
     *
     * @param userPassword 密码
     * @return 加密后的密码
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        // 加盐，混淆密码(防止反向破解密码)
        return userDomainService.getEncryptPassword(userPassword);
    }

    /**
     * 获取当前登录用户信息
     *
     * @param request 请求
     * @return 当前登录用户信息
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        return userDomainService.getLoginUser(request);
    }

    /**
     * 获取脱敏后的登录用户信息
     *
     * @param user 用户
     * @return 转换脱敏后的用户信息
     */
    @Override
    public LoginUserVO getLoginUserVo(User user) {
        return userDomainService.getLoginUserVo(user);
    }

    @Override
    public UserVO getUserVo(User user) {
        return userDomainService.getUserVo(user);
    }

    @Override
    public List<UserVO> getUserVoList(List<User> userList) {
        return userDomainService.getUserVoList(userList);
    }

    @Override
    public boolean userLogOut(HttpServletRequest request) {
        return userDomainService.userLogOut(request);
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        return userDomainService.getQueryWrapper(userQueryRequest);
    }

    @Override
    public User getUserById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userDomainService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return user;
    }

    @Override
    public UserVO getUserVOById(long id) {
        return userDomainService.getUserVo(getUserById(id));
    }

    @Override
    public boolean deleteUser(DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return userDomainService.removeById(deleteRequest.getId());
    }

    @Override
    public void updateUser(User user) {
        boolean result = userDomainService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    @Override
    public Page<UserVO> listUserVOByPage(UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = userQueryRequest.getCurrent();
        long size = userQueryRequest.getPageSize();
        Page<User> userPage = userDomainService.page(new Page<>(current, size),
                userDomainService.getQueryWrapper(userQueryRequest));
        Page<UserVO> userVOPage = new Page<>(current, size, userPage.getTotal());
        List<UserVO> userVO = userDomainService.getUserVoList(userPage.getRecords());
        userVOPage.setRecords(userVO);
        return userVOPage;
    }

    @Override
    public List<User> listByIds(Set<Long> userIdSet) {
        return userDomainService.listByIds(userIdSet);
    }

    @Override
    public long saveUser(User userEntity) {
        // 设置默认密码
        final String DEFAULT_USER_PASSWORD = "12345678";
        userEntity.setUserPassword(userDomainService.getEncryptPassword(DEFAULT_USER_PASSWORD)); // 加密后的默认密码

        // 插入数据
        boolean result = userDomainService.saveUser(userEntity); // mybatis-plus自带方法
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return userEntity.getId();
    }
}




