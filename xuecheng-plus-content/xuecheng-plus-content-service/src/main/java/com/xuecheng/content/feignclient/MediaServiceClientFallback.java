package com.xuecheng.content.feignclient;

import org.springframework.web.multipart.MultipartFile;

/**
 * @program: xuecheng-plus-project
 * @description: 熔断降级第一种方式操作类
 * @author: ldjc
 * @create: 2024-01-18 16:37
 **/
public class MediaServiceClientFallback implements MediaServiceClient{
    @Override
    public String upload(MultipartFile filedata, String objectName) throws Exception {
        return null;
    }
}
