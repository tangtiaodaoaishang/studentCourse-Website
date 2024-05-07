package com.xuecheng.content.service.impl;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.alibaba.fastjson.JSON;
import com.xuecheng.base.exception.CommonError;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.content.config.MultipartSupportConfig;
import com.xuecheng.content.feignclient.MediaServiceClient;
import com.xuecheng.content.mapper.CourseBaseMapper;
import com.xuecheng.content.mapper.CourseMarketMapper;
import com.xuecheng.content.mapper.CoursePublishMapper;
import com.xuecheng.content.mapper.CoursePublishPreMapper;
import com.xuecheng.content.model.dto.CourseBaseInfoDto;
import com.xuecheng.content.model.dto.CoursePreviewDto;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.model.po.CourseBase;
import com.xuecheng.content.model.po.CourseMarket;
import com.xuecheng.content.model.po.CoursePublish;
import com.xuecheng.content.model.po.CoursePublishPre;
import com.xuecheng.content.service.CourseBaseInfoService;
import com.xuecheng.content.service.CoursePublishService;
import com.xuecheng.content.service.TeachplanService;
import com.xuecheng.messagesdk.model.po.MqMessage;
import com.xuecheng.messagesdk.service.MqMessageService;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

/**
 * @program: xuecheng-plus-project
 * @description: 课程发布接口实现类
 * @author: ldjc
 * @create: 2024-01-16 20:07
 **/
@Slf4j
@Service
public class CoursePublishServiceImpl implements CoursePublishService {
    @Autowired
    CourseBaseInfoService courseBaseInfoService;
    @Autowired
    TeachplanService teachplanService;
    @Autowired
    CourseMarketMapper courseMarketMapper;
    @Autowired
    private CoursePublishPreMapper coursePublishPreMapper;
    @Autowired
    private CourseBaseMapper courseBaseMapper;
    @Autowired
    private CoursePublishMapper coursePublishMapper;
    //消息表sdk
    @Autowired
    MqMessageService mqMessageService;
    @Autowired
    MediaServiceClient mediaServiceClient;


    @Override
    public CoursePreviewDto getCoursePreviewInfo(Long courseId) {

        //查询课程基本信息和营销信息
        CourseBaseInfoDto courseBaseInfo = courseBaseInfoService.getCourseBaseInfo(courseId);

        //课程计划信息
        List<TeachplanDto> teachplanTree = teachplanService.findTeachplanTree(courseId);

        CoursePreviewDto coursePreviewDto = new CoursePreviewDto();
        coursePreviewDto.setCourseBase(courseBaseInfo);
        coursePreviewDto.setTeachplans(teachplanTree);
        return coursePreviewDto;

    }


    //课程提交审核的业务流程
    @Override
    @Transactional
    public void commitAudit(Long companyId, Long courseId) {
        CourseBaseInfoDto courseBaseInfo = courseBaseInfoService.getCourseBaseInfo(courseId);
        if (courseBaseInfo == null) {
            XueChengPlusException.cast("课程信息找不到");
        }
        //约束
        //1 如果课程的审核状态已经为已提交状态则不允许重复提交
        //课程审核状态
        String auditStatus = courseBaseInfo.getAuditStatus();
        //如果课程的审核状态为已提交状态(202003)则不允许提交
        if (auditStatus.equals("202003")) {
            XueChengPlusException.cast("课程已提交请等待审核");
        }
        //本机构只能提交本机构的课程

        //2 课程的图片,计划信息没有填写不允许提交
        String pic = courseBaseInfo.getPic();
        if (StringUtils.isEmpty(pic)) {
            XueChengPlusException.cast("请先上传课程图片");
        }
        //3 课程的计划信息如果为空则不允许提交
        List<TeachplanDto> teachplanTree = teachplanService.findTeachplanTree(courseId);
        if (teachplanTree == null || teachplanTree.size() == 0) {
            XueChengPlusException.cast("请先提交课程计划信息");
        }
        //查询到课程基本信息,营销信息,计划信息插入到课程预发布表中
        CoursePublishPre coursePublishPre = new CoursePublishPre();
        BeanUtils.copyProperties(courseBaseInfo, coursePublishPre);
        //设置机构id
        coursePublishPre.setCompanyId(companyId);
        //查询课程营销信息
        CourseMarket courseMarket = courseMarketMapper.selectById(courseId);
        //课程营销信息(转json对象为字符串)
        String courseMarketJson = JSON.toJSONString(courseMarket);
        coursePublishPre.setMarket(courseMarketJson);
        //课程计划信息(转json对象为字符串)
        String teachplanTreeJson = JSON.toJSONString(teachplanTree);
        coursePublishPre.setTeachplan(teachplanTreeJson);
        //设置状态码为已提交状态
        coursePublishPre.setStatus("202003");
        //设置提交时间
        coursePublishPre.setCreateDate(LocalDateTime.now());
        //查询预发表,如果有课程提交记录则更新,没有则插入
        CoursePublishPre coursePublishPreObj = coursePublishPreMapper.selectById(courseId);
        if (coursePublishPreObj == null) {
            //向课程预发表插入上述信息
            coursePublishPreMapper.insert(coursePublishPre);
        } else {
            //更新
            coursePublishPreMapper.updateById(coursePublishPre);
        }

        //更新课程基本信息表的审核状态为已提交
        CourseBase courseBase = courseBaseMapper.selectById(courseId);
        courseBase.setAuditStatus("202003"); //审核状态为已提交
        courseBaseMapper.updateById(courseBase);


    }

    @Transactional
    @Override
    public void publish(Long companyId, Long courseId) {
        //查询课程预发布表数据
        CoursePublishPre coursePublishPre = coursePublishPreMapper.selectById(courseId);
        //约束
        //1 课程预发布表中数据为空,则无法发布课程
        if (coursePublishPre == null) {
            XueChengPlusException.cast("课程没有审核记录,无法发布");
        }
        //2 课程如果审核未通过,不允许发布课程
        String status = coursePublishPre.getStatus();
        //202004状态码为审核通过
        if (!status.equals("202004")) {
            XueChengPlusException.cast("课程没有审核通过,不允许发布");
        }

        //向课程预发布表写入数据
        CoursePublish coursePublish = new CoursePublish();
        BeanUtils.copyProperties(coursePublishPre, coursePublish);
        //先查询课程发布信息,如果有信息则修改,否则添加
        CoursePublish coursePublishObj = coursePublishMapper.selectById(courseId);
        if (coursePublishObj == null) {
            coursePublishMapper.insert(coursePublish);
        } else {
            coursePublishMapper.updateById(coursePublish);
        }
        //向消息表写入数据
        saveCoursePublishMessage(courseId);
        //将预发布表中的数据删除
        coursePublishPreMapper.deleteById(courseId);
    }

    //生成静态的html页面
    @Override
    public File generateCourseHtml(Long courseId) {
        //最终的静态文件
        File htmlFile=null;
         try{
             //测试页面静态化
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
             CoursePreviewDto coursePreviewInfo = this.getCoursePreviewInfo( courseId);
             HashMap<Object, Object> map = new HashMap<>();
             map.put("model",coursePreviewInfo);
             //获取前端静态页面的代码数据
             //参数:Template template模板, Object model数据
             String html = FreeMarkerTemplateUtils.processTemplateIntoString(template, map);
             //将html输入IO流
             InputStream inputStream = IOUtils.toInputStream(html, "utf-8");
             //创建临时文件
             htmlFile=File.createTempFile("coursepublish",".html");
             //输出流
             FileOutputStream outputStream = new FileOutputStream(htmlFile);
             //使用流将html内容写入文件
             IOUtils.copy(inputStream,outputStream);

         }catch (Exception e){
              log.error("页面静态化出现问题,课程id:{}",courseId,e);
              e.printStackTrace();
         }

        return htmlFile;
    }

    //上传html页面到minio
    @Override
    public void uploadCourseHtml(Long courseId, File file) {
        try {
        //将file类型转为MultipartFile类型
        MultipartFile multipartFile = MultipartSupportConfig.getMultipartFile(file);
        String upload = mediaServiceClient.upload(multipartFile, "course/"+courseId+".html");
            if(upload==null){
                log.debug("远程调用走降级逻辑得到上传结果为null,课程id:{}",courseId);
                XueChengPlusException.cast("上传静态文件过程中存在异常");
            }
        } catch (Exception e) {
            e.printStackTrace();
            XueChengPlusException.cast("上传静态文件过程中存在异常");
        }
    }

    /**
     * @description 保存消息表记录
     * @param courseId  课程id
     * @return void
     * @author Mr.M
     * @date 2022/9/20 16:32
     */
    private void saveCoursePublishMessage(Long courseId) {
        MqMessage mqMessage = mqMessageService.addMessage("course_publish", String.valueOf(courseId), null, null);
        if (mqMessage == null) {
            XueChengPlusException.cast(CommonError.UNKOWN_ERROR);
        }
    }
    /**
    * @Author: ldjc
    * @Description: 根据课程id查询课程的发布信息
    * @DateTime: 17:14 2024/2/8
    * @Params: [courseId]
    * @Return com.xuecheng.content.model.po.CoursePublish
    */
    public CoursePublish getCoursePublish(Long courseId){
        CoursePublish coursePublish = coursePublishMapper.selectById(courseId);
        return coursePublish ;
    }
}
