package com.lune.yunpicture.infrastructure.api.imagesearch;

import com.lune.yunpicture.infrastructure.api.imagesearch.model.ImageSearchResult;
import com.lune.yunpicture.infrastructure.api.imagesearch.sub.GetImageFirstUrlApi;
import com.lune.yunpicture.infrastructure.api.imagesearch.sub.GetImageListApi;
import com.lune.yunpicture.infrastructure.api.imagesearch.sub.GetImagePageUrlApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 图片搜索接口 （使用一种设计模式：门面模式）
 */
@Slf4j
public class ImageSearchApiFacade {

    /**
     * 搜索图片
     *
     * @param imageUrl
     * @return
     */
    public static List<ImageSearchResult> searchImage(String imageUrl) {
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        return GetImageListApi.getImageList(imageFirstUrl);
    }

    public static void main(String[] args) {
        String imageUrl = "https://yunpic-1348558641.cos.ap-guangzhou.myqcloud.com//public/2042615128095055874/2026-04-16_mPftWfWjfKLTrSmL.";
        List<ImageSearchResult> imageList = searchImage(imageUrl);
        System.out.println("搜索成功，返回的图片列表：" + imageList);
    }
}
