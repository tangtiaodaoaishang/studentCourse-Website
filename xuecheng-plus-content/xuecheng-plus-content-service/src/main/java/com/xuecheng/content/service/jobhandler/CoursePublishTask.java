package com.xuecheng.content.service.jobhandler;

import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.content.feignclient.CourseIndex;
import com.xuecheng.content.feignclient.SearchServiceClient;
import com.xuecheng.content.mapper.CoursePublishMapper;
import com.xuecheng.content.model.dto.CoursePreviewDto;
import com.xuecheng.content.model.po.CoursePublish;
import com.xuecheng.content.service.CoursePublishService;
import com.xuecheng.messagesdk.model.po.MqMessage;
import com.xuecheng.messagesdk.service.MessageProcessAbstract;
import com.xuecheng.messagesdk.service.MqMessageService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * @program: xuecheng-plus-project
 * @description: 课程发布任务调度类
 * @author: ldjc
 * @create: 2024-01-17 19:11
 **/
//注意xxl-job任务执行前提是spring管理,即必须在xxl-job任务方法的所属类上加上@Component注解管理
@Slf4j
@Component
public class CoursePublishTask extends MessageProcessAbstract {
    @Autowired
    CoursePublishService coursePublishService;
    //远程feign调用search模块接口
    @Autowired
    SearchServiceClient searchServiceClient;
    //课程发布表实体类(course_publish表)
    @Autowired
    CoursePublishMapper coursePublishMapper;

    //执行课程发布任务调度逻辑
    //任务调度入口
    @XxlJob("CoursePublishJobHandler")
    public void coursePublishJobHandler() throws Exception {
        // 分片参数
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();
        //调用抽象类的方法执行任务
        process(shardIndex,shardTotal,"course_publish",30,60);
    }
    //如果以下方法抛出异常则说明任务执行失败
    @Override
    public boolean execute(MqMessage mqMessage) {
        //从mqMessage表中拿到课程id
        Long courseId =Long.valueOf(mqMessage.getBusinessKey1());
        //1 课程发布静态页面资源上传到minio
        generateCourseHtml(mqMessage,courseId);
        //2 向elasticsearch写索引数据
        saveCourseIndex(mqMessage,courseId);
        //3 向redis写缓存
        saveCourseCache(mqMessage,courseId);
        //4 返回true,表示任务完成
        return true;
    }

    //课程发布静态页面资源上传到minio文件系统(第一个阶段任务)
    public void generateCourseHtml(MqMessage mqMessage,long courseId){
        //消息id
        Long id = mqMessage.getId();
        MqMessageService mqMessageService = this.getMqMessageService();
        //任务幂等性处理
        //查询数据库取出该阶段的执行状态码
        int stageOne = mqMessageService.getStageOne(id);
        if(stageOne>0){
            log.debug("课程静态化任务完成,无需处理.....");
            return;
        }
        //1 开始进行课程信息静态化,生成html页面
        File file = coursePublishService.generateCourseHtml(courseId);
        if(file==null){
            XueChengPlusException.cast("生成的静态页面为空");
        }
        //2 将html页面上传到minio
        coursePublishService.uploadCourseHtml(courseId,file);
        //任务处理完成,将执行状态码修改为完成状态码(1)
        //完成本阶段任务
        mqMessageService.completedStageOne(id);
    }
    //将课程信息缓存至redis
    public void saveCourseCache(MqMessage mqMessage,long courseId){

    }
    //保存课程索引信息(向elasticsearch写索引(数据库中的课程信息)数据 第二个阶段任务)
    public void saveCourseIndex(MqMessage mqMessage,long courseId){
        //消息id
        Long id = mqMessage.getId();
        MqMessageService mqMessageService = this.getMqMessageService();
        int stageTwo = mqMessageService.completedStageTwo(id);
        //任务幂等性处理
/*        if(stageTwo>0){
            log.debug("课程静态化任务完成,无需处理.....");
            return;
        }*/
        //1 从课程的发布表(course_publish表)中查询课程信息
        CoursePublish coursePublish = coursePublishMapper.selectById(courseId);
        CourseIndex courseIndex=new CourseIndex();
        BeanUtils.copyProperties(coursePublish,courseIndex);
        //2 调用搜索服务添加索引
        Boolean add = searchServiceClient.add(courseIndex);
        if(!add){
            XueChengPlusException.cast("远程调用搜索服务添加课程索引失败");
        }
        //完成本阶段任务
        mqMessageService.completedStageTwo(id);

    }
}
