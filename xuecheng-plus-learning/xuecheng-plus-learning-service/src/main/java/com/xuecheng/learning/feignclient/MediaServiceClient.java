package com.xuecheng.learning.feignclient;

import com.xuecheng.base.model.RestResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @description TODO
 * @author Mr.M
 * @date 2022/10/27 9:04
 * @version 1.0
 */
 @FeignClient(value = "media-api",fallbackFactory = MediaServiceClientFallbackFactory.class)
 @RequestMapping("/media")
 public interface MediaServiceClient {

  /**
  * @Author: ldjc
  * @Description: 远程获取媒资服务的视频资源
  * @DateTime: 23:17 2024/2/15
  * @Params: [mediaId]
  * @Return com.xuecheng.base.model.RestResponse<java.lang.String>
  */
  @GetMapping("/open/preview/{mediaId}")
  public RestResponse<String> getPlayUrlByMediaId(@PathVariable("mediaId") String mediaId);

 }
