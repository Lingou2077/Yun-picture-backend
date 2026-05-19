package com.lune.yunpicture.shared.websocket;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.lune.yunpicture.shared.auth.SpaceUserAuthManager;
import com.lune.yunpicture.shared.auth.model.SpaceUserPermissionConstant;
import com.lune.yunpicture.domain.picture.entity.Picture;
import com.lune.yunpicture.domain.space.entity.Space;
import com.lune.yunpicture.domain.user.entity.User;
import com.lune.yunpicture.domain.space.valueobject.SpaceTypeEnum;
import com.lune.yunpicture.application.service.PictureApplicationService;
import com.lune.yunpicture.application.service.SpaceApplicationService;
import com.lune.yunpicture.application.service.UserApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 握手拦截器,建立链接前校验用户权限
 */
@Slf4j
@Component
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    @Resource
    private UserApplicationService userApplicationService;

    @Resource
    private PictureApplicationService pictureApplicationService;

    @Resource
    private SpaceApplicationService spaceApplicationService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 建立连接前先校验
     *
     * @param request
     * @param response
     * @param wsHandler
     * @param attributes
     * @return
     * @throws Exception
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // 获取当前登录用户
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest httpServletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            // 从请求中获取参数
            String pictureId = httpServletRequest.getParameter("pictureId");
            if (StrUtil.isBlank(pictureId)) {
                log.error("缺少图片参数，拒绝握手");
                return false;
            }
            // 获取当前登录用户
            User loginUser = userApplicationService.getLoginUser(httpServletRequest);
            if (ObjUtil.isEmpty(loginUser)) {
                log.error("用户未登录，拒绝握手");
                return false;
            }
            // 校验用户是否有编辑当前图片的权限
            Picture picture = pictureApplicationService.getById(pictureId);
            if (ObjUtil.isEmpty(picture)) {
                log.error("图片不存在，拒绝握手");
                return false;
            }
            Long spaceId = picture.getSpaceId();
            Space space = null; // 默认为空
            // 空间id不为空时，校验是否为团队空间
            if (spaceId != null) {
                space = spaceApplicationService.getById(spaceId);
                if (ObjUtil.isEmpty(space)) {
                    log.error("空间不存在，拒绝握手");
                    return false;
                }
                if (space.getSpaceType() != SpaceTypeEnum.TEAM.getValue()) {
                    log.error("非团队空间，拒绝握手");
                    return false;
                }
                // 如果是团队空间，并且有编辑者权限，才能建立连接
            }
            // 当空间id为空时，为公共图库，做扩展：以后有多个系统管理员时协同编辑公共图库图片
            // 校验是否为管理员权限
            List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser); // 获取用户权限列表
            if (!permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT)) {
                log.error("用户没有编辑权限，拒绝握手");
                return false;
            }
            // 设置用户登录信息等属性到WebSocket会话（Session）中
            attributes.put("user", loginUser);
            attributes.put("userId", loginUser.getId());
            attributes.put("pictureId", Long.valueOf(pictureId)); // 转换为long类型
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, @Nullable Exception exception) {
    }
}
