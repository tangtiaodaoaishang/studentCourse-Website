package com.xuecheng.content.feignclient;

import feign.hystrix.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * @program: xuecheng-plus-project
 * @description: 熔断降级第二种方式操作类
 * @author: ldjc
 * @create: 2024-01-18 16:41
 **/
@Slf4j
@Component
public class MediaServiceClientFallbackFactory implements FallbackFactory<MediaServiceClient> {
    //拿到熔断异常throwable
    @Override
    public MediaServiceClient create(Throwable throwable) {
        return new MediaServiceClient() {
            //发生熔断,上游服务调用此方法执行降级逻辑
            @Override
            public String upload(MultipartFile filedata, String objectName) throws Exception {
                log.debug("调用媒资管理服务上传文件时发生熔断，异常信息:{}",throwable.toString(),throwable);
                return null;
            }
        };
    }
}
