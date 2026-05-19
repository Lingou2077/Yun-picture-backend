package com.lune.yunpicture.application.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lune.yunpicture.domain.space.service.SpaceDomainService;
import com.lune.yunpicture.infrastructure.exception.BusinessException;
import com.lune.yunpicture.infrastructure.exception.ErrorCode;
import com.lune.yunpicture.infrastructure.exception.ThrowUtils;
import com.lune.yunpicture.interfaces.dto.space.SpaceAddRequest;
import com.lune.yunpicture.interfaces.dto.space.SpaceQueryRequest;
import com.lune.yunpicture.domain.space.entity.Space;
import com.lune.yunpicture.domain.space.entity.SpaceUser;
import com.lune.yunpicture.domain.user.entity.User;
import com.lune.yunpicture.domain.space.valueobject.SpaceLevelEnum;
import com.lune.yunpicture.domain.space.valueobject.SpaceRoleEnum;
import com.lune.yunpicture.domain.space.valueobject.SpaceTypeEnum;
import com.lune.yunpicture.interfaces.dto.space.SpaceUpdateRequest;
import com.lune.yunpicture.interfaces.vo.space.SpaceVO;
import com.lune.yunpicture.interfaces.vo.user.UserVO;
import com.lune.yunpicture.application.service.SpaceApplicationService;
import com.lune.yunpicture.infrastructure.mapper.SpaceMapper;
import com.lune.yunpicture.application.service.SpaceUserApplicationService;
import com.lune.yunpicture.application.service.UserApplicationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author ljx
 * @description 针对表【space(空间)】的数据库操作Service实现
 * @createDate 2026-04-19 16:23:32
 */
@Service
public class SpaceApplicationServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceApplicationService {
    @Resource
    private SpaceDomainService spaceDomainService;

    @Resource
    private UserApplicationService userApplicationService;

    @Resource
    private SpaceUserApplicationService spaceUserApplicationService;

    @Resource
    private TransactionTemplate transactionTemplate; // 编程式事务工具

    // 不使用分表，方便部署
//    @Resource
//    @Lazy
//    private DynamicShardingManager dynamicShardingManager;

    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        // 1. 填充参数默认值
        // 转换实体类和DTO
        Space space = new Space();
        BeanUtil.copyProperties(spaceAddRequest, space);
        if (StrUtil.isBlank(space.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (space.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        if (space.getSpaceType() == null) {
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        // 填充容量和图片创建数量大小
        this.fillSpaceBySpaceLevel(space);
        // 2. 参数校验
        space.validSpace(true);
        // 3. 校验权限，非管理员只能创建普通级别空间
        Long userId = loginUser.getId();
        space.setUserId(userId);
        if (SpaceLevelEnum.COMMON.getValue() != space.getSpaceLevel() && !loginUser.isAdmin()) {
            // 如果用户创建的空间级别不等于普通级别并且该用户不是管理员
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的空间");
        }
        // 4. 控制同一用户只能创建一个私有空间以及一个团队空间，根据id加锁
        String lock = String.valueOf(userId).intern(); // 构建锁对象，每个用户都有一把锁，同一用户得到的是同一把锁（intern()）
        synchronized (lock) {
            // 添加事务
            Long newSpaceId = transactionTemplate.execute(status -> {
                // 判断是否已有空间
                boolean exists = this.lambdaQuery()
                        .eq(Space::getUserId, userId)
                        .eq(Space::getSpaceType, space.getSpaceType())
                        .exists();
                // 已有空间，就不能再创建
                ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户每类空间仅能创建一个");
                // 创建
                boolean result = this.save(space); // 保存空间数据录入数据库
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "保存空间到数据库失败");
                // 创建成功后，如果是团队空间，关联新增成员记录
                if (SpaceTypeEnum.TEAM.getValue() == space.getSpaceType()) {
                    SpaceUser spaceUser = new SpaceUser();
                    spaceUser.setSpaceId(space.getId());
                    spaceUser.setUserId(userId);
                    spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                    result = spaceUserApplicationService.save(spaceUser);
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建团队空间成员失败");
                }
                // 创建分表（仅对团队空间有效），方便部署，不使用分表
//                dynamicShardingManager.createSpacePictureTable(space);
                return space.getId(); // 返回新写入数据的id
            });
            return Optional.ofNullable(newSpaceId).orElse(-1L);
        }
    }

    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 对象转封装类
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 关联用户信息
        Long userId = spaceVO.getUserId();
        User user = userApplicationService.getUserById(userId);
        UserVO userVO = userApplicationService.getUserVo(user);
        spaceVO.setUser(userVO);
        return spaceVO;
    }

    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        // 对象列表 => 封装对象列表
        List<SpaceVO> spaceVOList = spaceList.stream().map(SpaceVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userApplicationService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceVO.setUser(userApplicationService.getUserVo(user));
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        return spaceDomainService.getQueryWrapper(spaceQueryRequest);
    }

    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        spaceDomainService.fillSpaceBySpaceLevel(space);
    }

    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
        // 仅本人或管理员可删除
        spaceDomainService.checkSpaceAuth(loginUser, space);
    }

    @Override
    public void updateSpace(SpaceUpdateRequest spaceUpdateRequest, Space space) {
        // 自动填充数据
        this.fillSpaceBySpaceLevel(space);
        // 数据校验
        space.validSpace(false);
        // 判断是否存在
        long id = spaceUpdateRequest.getId();
        Space oldSpace = this.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = this.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }
}




