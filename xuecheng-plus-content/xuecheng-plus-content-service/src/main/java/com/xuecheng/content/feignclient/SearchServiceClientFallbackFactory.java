package com.xuecheng.content.feignclient;

import feign.hystrix.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @program: xuecheng-plus-project
 * @description: 熔断降级操作类
 * @author: ldjc
 * @create: 2024-02-13 19:45
 **/
@Slf4j
@Component
public class SearchServiceClientFallbackFactory implements FallbackFactory<SearchServiceClient> {

    @Override
    public SearchServiceClient create(Throwable throwable) {
        return new SearchServiceClient() {
            @Override
            public Boolean add(CourseIndex courseIndex) {
                log.error("添加课程索引发生熔断操作,索引信息:{},熔断异常信息:{}",courseIndex,throwable.toString(),throwable);
                //如果走降级逻辑,返回false值
                return false;
            }
        };

    }
}
