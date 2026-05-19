package com.lune.yunpicture.domain.user.entity;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.util.Date;

import com.lune.yunpicture.domain.user.valueobject.UserRoleEnum;
import com.lune.yunpicture.infrastructure.exception.BusinessException;
import com.lune.yunpicture.infrastructure.exception.ErrorCode;
import com.lune.yunpicture.infrastructure.exception.ThrowUtils;
import com.lune.yunpicture.interfaces.dto.user.UserLoginRequest;
import com.lune.yunpicture.interfaces.dto.user.UserRegisterRequest;
import lombok.Data;

import javax.servlet.http.HttpServletRequest;

/**
 * 用户
 *
 * @TableName user
 */
@TableName(value = "user")
@Data
public class User implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID) // 长整型id
    private Long id;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    private String userRole;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic // 标记为逻辑删除字段
    private Integer isDelete;

    @TableField(exist = false) // 不映射到数据库字段,序列化标识
    private static final long serialVersionUID = 1L;

    public static void validUserRegister(UserRegisterRequest userRegisterRequest) {
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
    }

    public static void validUserLogin(UserLoginRequest userLoginRequest) {
        // 1.校验
        ThrowUtils.throwIf(
                StrUtil.hasBlank(userLoginRequest.getUserAccount(), userLoginRequest.getUserPassword()),
                ErrorCode.PARAMS_ERROR, "参数为空");
        ThrowUtils.throwIf(userLoginRequest.getUserAccount().length() < 4,
                ErrorCode.PARAMS_ERROR, "用户账号错误");
        ThrowUtils.throwIf(userLoginRequest.getUserPassword().length() < 8,
                ErrorCode.PARAMS_ERROR, "用户密码错误");
    }

    /**
     * 判断用户是否为管理员
     * @return 是否为管理员
     */
    public boolean isAdmin() {
        return UserRoleEnum.ADMIN.getValue().equals(this.getUserRole());
    }
}