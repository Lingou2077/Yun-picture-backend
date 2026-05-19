package com.lune.yunpicture.domain.user.valueobject;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户角色枚举
 */
@Getter
public enum UserRoleEnum {

    USER("用户", "user"),
    ADMIN("管理员", "admin");

    private final String text;

    private final String value;

    private static final Map<String, UserRoleEnum> VALUE_MAP = new HashMap<>();

    static {
        for (UserRoleEnum roleEnum : UserRoleEnum.values()) {
            VALUE_MAP.put(roleEnum.value, roleEnum);
        }
    } // 在类加载时就将枚举值与枚举实例建立映射关系存入map

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static UserRoleEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        return VALUE_MAP.get(value); // 通过value获取枚举实例
    }
}

