package com.lune.yunpicture.domain.picture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lune.yunpicture.domain.picture.entity.Picture;
import com.lune.yunpicture.domain.picture.repository.PictureRepository;
import com.lune.yunpicture.domain.picture.service.PictureDomainService;
import com.lune.yunpicture.domain.picture.valueobject.PictureReviewStatusEnum;
import com.lune.yunpicture.domain.user.entity.User;
import com.lune.yunpicture.infrastructure.api.CosManager;
import com.lune.yunpicture.infrastructure.api.aliyunai.AliYunAiApi;
import com.lune.yunpicture.infrastructure.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.lune.yunpicture.infrastructure.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.lune.yunpicture.infrastructure.exception.BusinessException;
import com.lune.yunpicture.infrastructure.exception.ErrorCode;
import com.lune.yunpicture.infrastructure.exception.ThrowUtils;
import com.lune.yunpicture.infrastructure.utils.ColorSimilarUtils;
import com.lune.yunpicture.infrastructure.utils.ColorTransformUtils;
import com.lune.yunpicture.interfaces.dto.picture.*;
import com.lune.yunpicture.interfaces.vo.picture.PictureVO;
import com.lune.yunpicture.infrastructure.manager.upload.FilePictureUpload;
import com.lune.yunpicture.infrastructure.manager.upload.PictureUploadTemplate;
import com.lune.yunpicture.infrastructure.manager.upload.UrlPictureUpload;
import com.lune.yunpicture.infrastructure.manager.upload.model.dto.file.UploadPictureResult;
import com.lune.yunpicture.domain.space.entity.Space;
import com.lune.yunpicture.application.service.SpaceApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author ljx
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2026-04-12 22:07:54
 */
@Service
@Slf4j
public class PictureDomainServiceImpl implements PictureDomainService {

    @Resource
    private PictureRepository pictureRepository;

    @Resource
    private CosManager cosManager;

    @Resource
    private SpaceApplicationService spaceApplicationService;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AliYunAiApi aliYunAiApi;


    /**
     * 图片参数校验（用于增删改查）
     *
     * @param picture 图片对象
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        // 有url才验证
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    /**
     * 上传图片
     *
     * @param inputSource          文件输入源
     * @param pictureUploadRequest 图片上传请求对象
     * @param loginUser            登录用户
     * @return 图片信息（脱敏）
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 校验空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            // 校验是否有空间权限，仅空间管理员才能上传
            Space space = spaceApplicationService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 已改为统一的权限校验
            // 仅本人或管理员可上传
//            if (!loginUser.getId().equals(space.getUserId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
//            }
            // 校验额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }

        // 判断是否为新增还是删除
        Long pictureId = null;
        // 如果请求对象为空，说明没有图片，就是新增
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新，判断图片是否存在
        if (pictureId != null) {
            Picture oldPicture = pictureRepository.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 已改为统一的权限校验
            // 仅本人或管理员可更新
//            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
            // 校验空间是否一致
            // 没传spaceId，则复用原有图片的spaceId（兼容了公共图库）
            if (spaceId == null) {
                if (oldPicture.getId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                // 传了spaceId，则必须与原图空间id一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间id不一致");
                }
            }
        }
        // 上传图片（无论是更新还是新增都是要上传图片的），得到图片信息
        // 按照用户id创建文件夹 => 按照空间id创建文件夹
        String uploadPathPrefix;
        if (spaceId == null) {
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {
            uploadPathPrefix = String.format("space/%s", spaceId);
        }
        // 根据inputSource类型区分上传方法
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload; // 默认使用文件上传方式
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        // 构建图片对象
        Picture picture = new Picture();
        picture.setSpaceId(spaceId); // 指定空间id
        picture.setUrl(uploadPictureResult.getUrl()); // 添加原图url
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl()); // 添加缩略图url
        String picName = uploadPictureResult.getPicName(); // 默认是对象存储解析后的名称
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        } // 如果请求对象有picName，则使用picName作为图片名称，支持外层传递图片名称
        picture.setName(picName);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
//        picture.setPicColor(uploadPictureResult.getPicColor());
        // 转换为6位标准颜色
        picture.setPicColor(ColorTransformUtils.getStandardColor(uploadPictureResult.getPicColor()));
        picture.setUserId(loginUser.getId());
        // 补充审核参数
        fillReviewParams(picture, loginUser);
        // 操作数据库 （需要判断为新增还是更新）
        // 如果pictureId不为空，说明是更新,否则为新增
        if (pictureId != null) {
            // 更新需要补充id和更新时间
            picture.setId(pictureId);
            picture.setUpdateTime(new Date());
        }
        // 开启事务
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            // 插入数据
            boolean result = pictureRepository.saveOrUpdate(picture); // Mybatis会根据有无id字段来判断是保存还是更新
            ThrowUtils.throwIf(!result, ErrorCode.SYSTEM_ERROR, "图片上传失败，数据库操作失败");
            if (finalSpaceId != null) {
                // 更新空间使用额度
                boolean update = spaceApplicationService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.SYSTEM_ERROR, "空间使用额度更新失败");
            }
            return picture;
        });
        // todo: 清理图片文件
        return PictureVO.objToVo(picture); // 封装响应数据
    }

    /**
     * 获取查询条件
     *
     * @param pictureQueryRequest 图片请求体
     * @return 查询条件
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }

        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        // >= startEditTime
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        // < endEditTime
        queryWrapper.le(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    /**
     * 图片审核
     *
     * @param pictureReviewRequest 图片审核请求体
     * @param loginUser            登录用户
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum enumByValue = PictureReviewStatusEnum.getEnumByValue(reviewStatus); // 获取枚举
        String reviewMessage = pictureReviewRequest.getReviewMessage();
        if (id == null || enumByValue == null || PictureReviewStatusEnum.REVIEWING.equals(enumByValue)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2. 判断图片是否存在
        Picture oldPicture = pictureRepository.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 3. 判断审核状态是否重复,已是该状态
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        // 4. 数据库操作
        Picture updatePicture = new Picture();
        BeanUtil.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean b = pictureRepository.updateById(updatePicture);
        ThrowUtils.throwIf(!b, ErrorCode.OPERATION_ERROR);
    }

    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (loginUser.isAdmin()) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员用户，无论是上传还是编辑，默认为待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        // 校验参数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多30条");
        // 名称前缀默认为搜索关键词
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            // 为空设置默认值为搜索关键词
            namePrefix = searchText;
        }
        // 抓取内容
        // 拼接搜索地址
        String searchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(searchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        // 解析内容
        Element div = document.getElementsByClass("dgControl").first();// 获取html页面中对应类名（这里是图片最外层div的类名）
        if (ObjUtil.isEmpty(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        Elements imgElementList = div.select("img.mimg"); // 根据css 选择器获取图片元素：必须包含mimg类名的img元素
        // 遍历列表，依次上传图片
        int countUpload = 0;
        for (Element imgElement : imgElementList) {
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                // 单张失败不应该打断
                log.info("当前链接为空，已跳过：{}", fileUrl);
                continue;
            }
            // 处理图片地址，将?后的全部去掉防止转义或与对象存储冲突
            int questionMarkIndex = fileUrl.indexOf("?"); // 获取?下标位置
            if (questionMarkIndex > -1) {
                // > -1 表示有?
                fileUrl = fileUrl.substring(0, questionMarkIndex); // 将？之前的内容截取出来
            }
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(fileUrl);
            pictureUploadRequest.setPicName(namePrefix + (countUpload + 1));
            // 上传图片
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("上传成功：id:{}", pictureVO.getId());
                countUpload++; // 上传成功数量加1
            } catch (Exception e) {
                log.error("上传图片失败：{}", e.getMessage());
                continue;
            }
            if (countUpload >= count) {
                break;
            }
        }
        return countUpload;
    }

    @Async // 异步方法，当前端点击删除时，另外开启线程执行该操作
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判断该图片是否被多条记录使用
        String pictureUrl = oldPicture.getUrl();
        Long count = pictureRepository.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count(); // 获取图片使用次数
        // 有不止一条记录用到该图片，不清理
        if (count > 1) {
            return;
        }
        // 删除图片
        cosManager.deleteObject(pictureUrl);
        // 删除缩略图
        String thumbnailUrl = oldPicture.getThumbnailUrl();
        if (StrUtil.isNotBlank(thumbnailUrl)) {
            cosManager.deleteObject(thumbnailUrl);
        }
    }

    /**
     * 删除图片
     *
     * @param pictureId
     * @param loginUser
     */
    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = pictureRepository.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限，已改为通过注解鉴权
//        checkPictureAuth(loginUser, oldPicture);
        // 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = pictureRepository.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 更新空间使用额度，释放额度
            boolean update = spaceApplicationService.lambdaUpdate()
                    .eq(Space::getId, oldPicture.getSpaceId())
                    .setSql("totalSize = totalSize - " + oldPicture.getPicSize() + ", totalCount = totalCount - 1")
                    .update();
            ThrowUtils.throwIf(!update, ErrorCode.SYSTEM_ERROR, "空间使用额度更新失败");
            return true;
        });

        // 异步清理文件
        this.clearPictureFile(oldPicture);
    }

    @Override
    public void editPicture(Picture picture, User loginUser) {

        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        picture.validPicture();
        // 判断是否存在
        long id = picture.getId();
        Picture oldPicture = pictureRepository.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑，已改为通过注解鉴权
//        checkPictureAuth(loginUser, oldPicture);
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = pictureRepository.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        Long loginUserId = loginUser.getId();
        if (spaceId == null) {
            // 公共空间，仅用户本人和系统管理员可操作
            if (!picture.getUserId().equals(loginUserId) && !loginUser.isAdmin()) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!picture.getUserId().equals(loginUserId)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceApplicationService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问");
        }
        // 3. 查询该空间下所有图片（必须要有主色调）
        List<Picture> pictureList = pictureRepository.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 若无图片，则返回空列表
        if (pictureList.isEmpty()) {
            return new ArrayList<>();
        }
        // 将颜色字符串转换为主色调
        Color targetColor = Color.decode(picColor); // 转换，节省调用解析方法时的转换
        // 4. 计算相似度并排序
        List<Picture> sortedPictureList = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    String hexColor = picture.getPicColor();
                    // 无主色调图片会默认排在最后
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    // 计算相似度
                    // 越大越相似 （默认从小到大排序，所以为了相似度高的排在前面，使用 - 取反）
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                .limit(12) // 获取相似度最高的12个图片
                .collect(Collectors.toList());
        // 5. 返回结果 转换为封装类VO
        return sortedPictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }

    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        // 1. 获取和校验参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        ThrowUtils.throwIf(CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR);
        // 2. 校验空间权限
        Space space = spaceApplicationService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问");
        }
        // 3. 查询指定图片（仅选择需要的字段）
        List<Picture> pictureList = pictureRepository.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        if (pictureList.isEmpty()) {
            return;
        }
        // 4. 更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        // 更新命名规则
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList, nameRule);
        // 5. 操作数据库批量更新
        boolean result = pictureRepository.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "批量编辑操作失败");
    }

    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        // 判断图片是否存在，另一种写法
        Picture picture = Optional.ofNullable(pictureRepository.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"));
        // 校验权限，已改为通过注解鉴权
//        checkPictureAuth(loginUser, picture);
        // 创建扩图任务
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        createOutPaintingTaskRequest.setInput(input);
        createOutPaintingTaskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());
        // 创建任务
        return aliYunAiApi.createOutPaintingTask(createOutPaintingTaskRequest);
    }

    /**
     * nameRule 格式：图片{序号}
     *
     * @param pictureList
     * @param nameRule
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (StrUtil.isBlank(nameRule) || CollUtil.isEmpty(pictureList)) {
            return;
        }
        long count = 1; // 序号
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }
}



