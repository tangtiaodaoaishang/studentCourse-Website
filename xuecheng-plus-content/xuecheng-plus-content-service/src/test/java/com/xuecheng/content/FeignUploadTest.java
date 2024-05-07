package com.xuecheng.content;

import com.xuecheng.content.config.MultipartSupportConfig;
import com.xuecheng.content.feignclient.MediaServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

/**
 * @program: xuecheng-plus-project
 * @description: 测试远程调用媒资服务
 * @author: ldjc
 * @create: 2024-01-18 14:57
 **/
@SpringBootTest
public class FeignUploadTest {
    @Autowired
    MediaServiceClient mediaServiceClient;
    @Test
    public void test(){
        //将file类型转为MultipartFile类型
        File file = new File("E:\\120.html");
        MultipartFile multipartFile = MultipartSupportConfig.getMultipartFile(file);
        try {
            String upload = mediaServiceClient.upload(multipartFile, "course/120.html");
            if(upload==null){
                System.out.println("走了降级的逻辑");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
