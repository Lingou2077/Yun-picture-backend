package com.lune.yunpicture.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lune.yunpicture.infrastructure.common.DeleteRequest;
import com.lune.yunpicture.interfaces.dto.user.UserLoginRequest;
import com.lune.yunpicture.interfaces.dto.user.UserQueryRequest;
import com.lune.yunpicture.interfaces.dto.user.UserRegisterRequest;
import com.lune.yunpicture.domain.user.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lune.yunpicture.interfaces.vo.user.LoginUserVO;
import com.lune.yunpicture.interfaces.vo.user.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

/**
 * @author ljx
 * @description 针对表【user(用户)】的数据库操作Service
 * @createDate 2026-04-10 21:21:06
 */
public interface UserApplicationService {


    /**
     * 用户注册
     *
     * @param userRegisterRequest (账号、密码、确认密码)
     * @return
     */
    long userRegister(UserRegisterRequest userRegisterRequest);

    /**
     * 用户登录
     *
     * @param userLoginRequest (账号、密码)
     * @param request          请求session
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request);

    /**
     * 获取加密后的密码
     *
     * @param userPassword
     * @return
     */
    String getEncryptPassword(String userPassword);

    /**
     * 获取当前登录用户(系统内部传递信息使用)
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 获取脱敏后的登录用户信息
     *
     * @param user
     * @return
     */
    LoginUserVO getLoginUserVo(User user);

    /**
     * 获取脱敏后的用户信息
     *
     * @param user
     * @return
     */
    UserVO getUserVo(User user);

    /**
     * 获取脱敏后的用户信息列表
     *
     * @param userList
     * @return
     */
    List<UserVO> getUserVoList(List<User> userList);

    /**
     * 用户登录态注销
     *
     * @param request
     * @return
     */
    boolean userLogOut(HttpServletRequest request);

    /**
     * 获取查询条件
     *
     * @param userQueryRequest
     * @return
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);


    User getUserById(long id);

    UserVO getUserVOById(long id);

    boolean deleteUser(DeleteRequest deleteRequest);

    void updateUser(User user);

    Page<UserVO> listUserVOByPage(UserQueryRequest userQueryRequest);

    List<User> listByIds(Set<Long> userIdSet);

    long saveUser(User userEntity);
}
