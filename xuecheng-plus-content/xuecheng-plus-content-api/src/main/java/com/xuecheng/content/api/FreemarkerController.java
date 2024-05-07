package com.xuecheng.content.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

/**
 * @program: xuecheng-plus-project
 * @description: Freemarker入门程序
 * @author: ldjc
 * @create: 2024-01-12 16:48
 **/
@RestController
public class FreemarkerController {
    @GetMapping("/testfreemarker")
    public ModelAndView test(){
        ModelAndView modelAndView = new ModelAndView();
        //根据试图名字找到对应的.ftl页面模板
        modelAndView.setViewName("test");
        //添加页面模板中的变量的值(指定模型)
        modelAndView.addObject("name","小明");
        return modelAndView;
    }
}
