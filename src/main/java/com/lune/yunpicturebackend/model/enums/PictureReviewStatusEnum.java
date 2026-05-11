package com.lune.yunpicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 图片审核状态枚举
 */
@Getter
public enum PictureReviewStatusEnum {

    REVIEWING("待审核", 0),
    PASS("通过", 1),
    REJECT("拒绝", 2);

    private final String text;

    private final int value;

    private static final Map<Integer, PictureReviewStatusEnum> VALUE_MAP = new HashMap<>();

    static {
        for (PictureReviewStatusEnum roleEnum : PictureReviewStatusEnum.values()) {
            VALUE_MAP.put(roleEnum.value, roleEnum);
        }
    } // 在类加载时就将枚举值与枚举实例建立映射关系存入map

    PictureReviewStatusEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static PictureReviewStatusEnum getEnumByValue(int value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        return VALUE_MAP.get(value); // 通过value获取枚举实例
    }
}
