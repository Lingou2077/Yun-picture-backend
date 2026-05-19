package com.lune.yunpicture.interfaces.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户登录请求封装类
 */
@Data
public class UserLoginRequest implements Serializable {
    private static final long serialVersionUID = -3326269947148309486L;
    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;
}
