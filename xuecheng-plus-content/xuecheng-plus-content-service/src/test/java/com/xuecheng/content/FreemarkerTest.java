package com.xuecheng.content;

import com.xuecheng.content.model.dto.CoursePreviewDto;
import com.xuecheng.content.service.CoursePublishService;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import sun.nio.ch.IOUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

/**
 * @program: xuecheng-plus-project
 * @description: 页面静态化测试
 * @author: ldjc
 * @create: 2024-01-18 13:30
 **/
@SpringBootTest
public class FreemarkerTest {
    @Autowired
    CoursePublishService coursePublishService;
    //测试页面静态化
    @Test
    public void testGenerateHtmlByTemplate() throws IOException, TemplateException {
        Configuration configuration = new Configuration(Configuration.getVersion());
        //获取classpath路径
        String path = this.getClass().getResource("/").getPath();
        //指定模板的目录
        configuration.setDirectoryForTemplateLoading(new File(path+"/templates/"));
        //指定编码
        configuration.setDefaultEncoding("utf-8");
        //获取前端模板
        Template template = configuration.getTemplate("course_template.ftl");
        //获取后台数据库数据
        CoursePreviewDto coursePreviewInfo = coursePublishService.getCoursePreviewInfo( 22L);
        HashMap<Object, Object> map = new HashMap<>();
        map.put("model",coursePreviewInfo);
        //获取前端静态页面的代码数据
        //参数:Template template模板, Object model数据
        String html = FreeMarkerTemplateUtils.processTemplateIntoString(template, map);
        //将html输入IO流
        InputStream inputStream = IOUtils.toInputStream(html, "utf-8");
        //输出流
        FileOutputStream outputStream = new FileOutputStream("E:\\120.html");
        //使用流将html内容写入文件
        IOUtils.copy(inputStream,outputStream);
    }
}
