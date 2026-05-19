package com.lune.yunpicture.infrastructure.utils;

import java.awt.*;

/**
 * 工具类：颜色转换
 */
public class ColorTransformUtils {

    private ColorTransformUtils() {
        // 工具类不需要实例化
    }

    /**
     * 获取标准颜色 （将数据万象的五位色值转换为六位）
     *
     * @param color
     * @return
     */
    public static String getStandardColor(String color) {
        // 数据万象解析时，会把中间的多个0合并成一个0
        // 需要自己转换
        // 示例：0x080e0 => 0x0800e0
        if (color.length() == 7) {
            color = color.substring(0, 4) + "0" + color.substring(4, 7);
        }
        return color;
    }
}
