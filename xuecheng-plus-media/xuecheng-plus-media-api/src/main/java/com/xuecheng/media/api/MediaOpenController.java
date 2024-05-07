package com.xuecheng.media.api;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.base.model.RestResponse;
import com.xuecheng.media.model.po.MediaFiles;
import com.xuecheng.media.service.MediaFileService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @program: xuecheng-plus-project
 * @description: 媒资文件公开管理接口
 * @author: ldjc
 * @create: 2024-01-16 22:04
 **/
@Api(value = "媒资文件管理接口",tags = "媒资文件管理接口")
@RestController
@RequestMapping("/open")
public class MediaOpenController {
    @Autowired
    MediaFileService mediaFileService;

    @ApiOperation("预览文件")
    @GetMapping("/preview/{mediaId}")
    public RestResponse<String> getPlayUrlByMediaId(@PathVariable String mediaId){

        MediaFiles mediaFiles = mediaFileService.getFileById(mediaId);
        if(mediaFiles == null) {
            //向前端返回错误信息
            return RestResponse.validfail("找不到视频");
        }
        //取出视频播放地址
        String url = mediaFiles.getUrl();
        if (StringUtils.isEmpty(url)){
            //向前端返回错误信息
            return RestResponse.validfail("该视频正在处理中");
        }
        //向前端返回正确的视频地址信息
        return RestResponse.success(mediaFiles.getUrl());

    }
}
