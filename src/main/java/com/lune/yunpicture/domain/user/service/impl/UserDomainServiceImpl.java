package com.lune.yunpicture.domain.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lune.yunpicture.domain.user.entity.User;
import com.lune.yunpicture.domain.user.repository.UserRepository;
import com.lune.yunpicture.domain.user.service.UserDomainService;
import com.lune.yunpicture.domain.user.valueobject.UserRoleEnum;
import com.lune.yunpicture.infrastructure.exception.BusinessException;
import com.lune.yunpicture.infrastructure.exception.ErrorCode;
import com.lune.yunpicture.infrastructure.exception.ThrowUtils;
import com.lune.yunpicture.interfaces.dto.user.UserLoginRequest;
import com.lune.yunpicture.interfaces.dto.user.UserQueryRequest;
import com.lune.yunpicture.interfaces.dto.user.UserRegisterRequest;
import com.lune.yunpicture.interfaces.vo.user.LoginUserVO;
import com.lune.yunpicture.interfaces.vo.user.UserVO;
import com.lune.yunpicture.shared.auth.StpKit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.lune.yunpicture.domain.user.constant.UserConstant.USER_LOGIN_STATE;

/**
 * @author ljx
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2026-04-10 21:21:06
 */
@Service
@Slf4j
public class UserDomainServiceImpl implements UserDomainService {
    // 操作数据库接口（用户仓储）
    @Resource
    private UserRepository userRepository;

    /**
     * 用户注册
     *
     * @param userRegisterRequest (账号、密码、确认密码)
     * @return
     */
    @Override
    public long userRegister(UserRegisterRequest userRegisterRequest) {
        // 2. 检查用户账号是否与数据库已有的重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userRegisterRequest.getUserAccount()); // 构建查询条件
        Long cont = userRepository.getBaseMapper().selectCount(queryWrapper); // 获取已存在的数量
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
        boolean result = userRepository.save(user); // 保存用户信息(mybatis实现)
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    @Override
    public LoginUserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request) {
        // 2.对用户传递的密码进行加密
        String encryptPassword = getEncryptPassword(userLoginRequest.getUserPassword());
        // 3.查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userLoginRequest.getUserAccount());
        queryWrapper.eq("userPassword", encryptPassword);
        User user = userRepository.getBaseMapper().selectOne(queryWrapper); // 数据表已对userAccount约束（唯一），所以查询出来只会是1条，而不是返回一个列表
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
        currentUser = userRepository.getById(currentUser.getId());
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

    @Override
    public Boolean removeById(Long id) {
        return userRepository.removeById(id);
    }

    @Override
    public boolean updateById(User user) {
        return userRepository.updateById(user);
    }

    @Override
    public User getById(long id) {
        return userRepository.getById(id);
    }

    @Override
    public Page<User> page(Page<User> userPage, QueryWrapper<User> queryWrapper) {
        return userRepository.page(userPage, queryWrapper);
    }

    @Override
    public List<User> listByIds(Set<Long> userIdSet) {
        return userRepository.listByIds(userIdSet);
    }

    @Override
    public boolean saveUser(User userEntity) {
        return userRepository.save(userEntity);
    }
}




