package com.xuecheng.content.feignclient;

import com.alibaba.nacos.common.http.param.MediaType;
import com.xuecheng.content.config.MultipartSupportConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
* @Author: ldjc
* @Description: 内容服务远程调用媒资服务接口
* @DateTime: 14:52 2024/1/18
* @Params:
* @Return
*/
//fallback为熔断降级,即如果以下方法请求无法执行,则降级到指定的fallback属性类里执行操作)
    //熔断降级的两种方式
    //1 使用fallback进行熔断降级,是无法拿到熔断异常
//@FeignClient(value = "media-api",configuration={MultipartSupportConfig.class},fallback = MediaServiceClientFallback.class)
    //2 使用FallbackFactory进行熔断降级,就可以拿到熔断异常信息
@FeignClient(value = "media-api",configuration={MultipartSupportConfig.class},fallbackFactory = MediaServiceClientFallbackFactory.class)
public interface MediaServiceClient {

    @RequestMapping(value = "/media/upload/coursefile",consumes = MediaType.MULTIPART_FORM_DATA)
    public String upload(@RequestPart("filedata") MultipartFile filedata, @RequestParam(value= "objectName",required=false) String objectName) throws Exception ;
}
