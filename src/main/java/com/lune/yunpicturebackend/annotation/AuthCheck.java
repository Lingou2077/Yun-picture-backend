package com.lune.yunpicturebackend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD) // 方法级别注解
@Retention(RetentionPolicy.RUNTIME) // 运行时还有效
public @interface AuthCheck {
    /**
     * 必须有该角色
     */
    String mustRole() default "";
}
