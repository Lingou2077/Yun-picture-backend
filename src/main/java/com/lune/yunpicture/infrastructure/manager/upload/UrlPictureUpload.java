package com.lune.yunpicture.infrastructure.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.lune.yunpicture.infrastructure.exception.BusinessException;
import com.lune.yunpicture.infrastructure.exception.ErrorCode;
import com.lune.yunpicture.infrastructure.exception.ThrowUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * url图片上传
 */
@Service
public class UrlPictureUpload extends PictureUploadTemplate {
    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        // 校验非空
        ThrowUtils.throwIf(StringUtils.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件不能为空");
        // 校验url格式
        try {
            new URL(fileUrl); // 使用 java 自带的url校验
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式错误");
        }
        // 校验url协议（http 和 https）
        ThrowUtils.throwIf(!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://"),
                ErrorCode.PARAMS_ERROR, "仅支持 HTTP 或 HTTPS 协议的文件"
        );
        // 发送 HEAD 请求校验文件是否存在
        HttpResponse httpResponse = null;
        try {
            httpResponse = HttpUtil.createRequest(Method.HEAD, fileUrl)
                    .execute();
            // 未正常访问，不执行后续逻辑
            if (httpResponse.getStatus() != HttpStatus.HTTP_OK) {
                return;
            }
            // 文件存在，则校验文件类型
            String header = httpResponse.header("Content-Type");
            // 不为空才校验是否合法，这样校验规则较为宽松
            if (StringUtils.isNotBlank(header)) {
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/png", "image/webp", "image/jpg");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(header), ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
            // 文件存在，则校验文件大小
            String contentLengthStr = httpResponse.header("Content-Length");
            if (StringUtils.isNotBlank(contentLengthStr)) {
                try {
                    long contentLength = Long.parseLong(contentLengthStr);
                    final long ONE_M = 1024 * 1024; // 1mb
                    ThrowUtils.throwIf(contentLength > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过2MB");
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小格式异常");
                }

            }
        } finally {
            if (httpResponse != null) {
                // 释放资源
                httpResponse.close();
            }
        }
    }

    @Override
    protected String getOriginFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        return FileUtil.mainName(fileUrl);
    }

    @Override
    protected void processFile(Object inputSource, File file) throws IOException {
        String fileUrl = (String) inputSource;
        HttpUtil.downloadFile(fileUrl, file); // 下载远程图片到临时目录
    }
}
