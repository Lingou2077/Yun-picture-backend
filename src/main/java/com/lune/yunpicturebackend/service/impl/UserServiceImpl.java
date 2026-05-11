package com.lune.yunpicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lune.yunpicturebackend.constant.UserConstant;
import com.lune.yunpicturebackend.exception.BusinessException;
import com.lune.yunpicturebackend.exception.ErrorCode;
import com.lune.yunpicturebackend.exception.ThrowUtils;
import com.lune.yunpicturebackend.manager.auth.StpKit;
import com.lune.yunpicturebackend.model.dto.user.UserLoginRequest;
import com.lune.yunpicturebackend.model.dto.user.UserQueryRequest;
import com.lune.yunpicturebackend.model.dto.user.UserRegisterRequest;
import com.lune.yunpicturebackend.model.entity.User;
import com.lune.yunpicturebackend.model.enums.UserRoleEnum;
import com.lune.yunpicturebackend.model.vo.LoginUserVO;
import com.lune.yunpicturebackend.model.vo.UserVO;
import com.lune.yunpicturebackend.service.UserService;
import com.lune.yunpicturebackend.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.lune.yunpicturebackend.constant.UserConstant.USER_LOGIN_STATE;

/**
 * @author ljx
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2026-04-10 21:21:06
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    /**
     * 用户注册
     *
     * @param userRegisterRequest (账号、密码、确认密码)
     * @return
     */
    @Override
    public long userRegister(UserRegisterRequest userRegisterRequest) {
        // 1. 校验参数
        if (StrUtil.hasBlank(userRegisterRequest.getUserAccount(), userRegisterRequest.getUserPassword(), userRegisterRequest.getCheckPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userRegisterRequest.getUserAccount().length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userRegisterRequest.getUserPassword().length() < 8 || userRegisterRequest.getCheckPassword().length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        if (!userRegisterRequest.getUserPassword().equals(userRegisterRequest.getCheckPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 2. 检查用户账号是否与数据库已有的重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userRegisterRequest.getUserAccount()); // 构建查询条件
        Long cont = this.baseMapper.selectCount(queryWrapper); // 获取已存在的数量
        if (cont > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号已存在");
        }
        // 3. 密码加密（必须加密）
        String encryptPassword = getEncryptPassword(userRegisterRequest.getUserPassword());
        // 4. 插入数据到数据库中
        User user = new User();
        user.setUserAccount(userRegisterRequest.getUserAccount());
        user.setUserPassword(encryptPassword);
        user.setUserName("无名"); // 默认名称
        user.setUserRole(UserRoleEnum.USER.getValue()); // 默认普通用户
        boolean result = this.save(user); // 保存用户信息(mybatis实现)
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    @Override
    public LoginUserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request) {
        // 1.校验
        ThrowUtils.throwIf(
                StrUtil.hasBlank(userLoginRequest.getUserAccount(), userLoginRequest.getUserPassword()),
                ErrorCode.PARAMS_ERROR, "参数为空");
        ThrowUtils.throwIf(userLoginRequest.getUserAccount().length() < 4,
                ErrorCode.PARAMS_ERROR, "用户账号错误");
        ThrowUtils.throwIf(userLoginRequest.getUserPassword().length() < 8,
                ErrorCode.PARAMS_ERROR, "用户密码错误");
        // 2.对用户传递的密码进行加密
        String encryptPassword = getEncryptPassword(userLoginRequest.getUserPassword());
        // 3.查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userLoginRequest.getUserAccount());
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper); // 数据表已对userAccount约束（唯一），所以查询出来只会是1条，而不是返回一个列表
        // 不存在，抛异常
        if (user == null) {
            log.error("user login failed,userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 4. 保存用户登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        // 5. 记录用户登录态到 Sa-token，便于空间鉴权时使用，注意保证该用户信息与 SpringSession 中的信息过期时间一致
        StpKit.SPACE.login(user.getId());
        StpKit.SPACE.getSession().set(USER_LOGIN_STATE, user);

        return this.getLoginUserVo(user);
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
        final String SALT = "Lune";
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }

    /**
     * 获取当前登录用户信息
     *
     * @param request 请求
     * @return 当前登录用户信息
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 判断是否登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        ThrowUtils.throwIf(currentUser == null || currentUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        // 从数据库中再次查询数据，保证数据一致性
        currentUser = this.getById(currentUser.getId());
        ThrowUtils.throwIf(currentUser == null, ErrorCode.NOT_LOGIN_ERROR);
        return currentUser;
    }

    /**
     * 获取脱敏后的登录用户信息
     *
     * @param user 用户
     * @return 转换脱敏后的用户信息
     */
    @Override
    public LoginUserVO getLoginUserVo(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVo = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVo); // 将老对象值赋值给新对象
        return loginUserVo;
    }

    @Override
    public UserVO getUserVo(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVo = new UserVO();
        BeanUtil.copyProperties(user, userVo); // 将老对象值赋值给新对象
        return userVo;
    }

    @Override
    public List<UserVO> getUserVoList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream()
                .map(this::getUserVo)
                .collect(Collectors.toList());
    }

    @Override
    public boolean userLogOut(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        ThrowUtils.throwIf(currentUser == null || currentUser.getId() == null, ErrorCode.OPERATION_ERROR, "未登录");
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR, "请求参数为空");
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole); // 等于
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount); // 模糊匹配
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField); // 排序
        return queryWrapper;
    }

    /**
     * 判断用户是否为管理员
     *
     * @param user 用户
     * @return 是否为管理员
     */
    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }
}




