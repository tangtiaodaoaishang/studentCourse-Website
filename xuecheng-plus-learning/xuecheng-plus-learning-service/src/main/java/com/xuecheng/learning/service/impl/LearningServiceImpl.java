package com.xuecheng.learning.service.impl;

import com.alibaba.fastjson.JSON;
import com.xuecheng.base.model.RestResponse;
import com.xuecheng.base.utils.StringUtil;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.model.po.CoursePublish;
import com.xuecheng.content.model.po.Teachplan;
import com.xuecheng.learning.feignclient.ContentServiceClient;
import com.xuecheng.learning.feignclient.MediaServiceClient;
import com.xuecheng.learning.model.dto.XcCourseTablesDto;
import com.xuecheng.learning.service.LearningService;
import com.xuecheng.learning.service.MyCourseTablesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @program: xuecheng-plus-project
 * @description: 在线学习接口实现类
 * @author: ldjc
 * @create: 2024-02-15 21:47
 **/
@Service
@Slf4j
public class LearningServiceImpl implements LearningService {
    @Autowired
    MyCourseTablesService myCourseTablesService;
    @Autowired
    ContentServiceClient contentServiceClient;
    @Autowired
    MediaServiceClient mediaServiceClient;
    @Override
    public RestResponse<String> getVideo(String userId, Long courseId, Long teachplanId, String mediaId) {
        //查询课程发布信息
        CoursePublish coursepublish = contentServiceClient.getCoursepublish(courseId);
        //如果课程发布信息为空,则不在继续
        if(coursepublish==null){
            return RestResponse.validfail("课程不存在");
        }
        //远程调用内容管理服务根据课程计划id(teachplanId)去查询课程计划信息,从而拿到is_preview字段判断视频是否具有试学资格(值为1是具有试学资格)
        //也可以从课程发布信息(coursepublish)中拿到课程计划信息,从而拿到is_preview字段判断视频是否具有试学资格(值为1是具有试学资格)
        //TODO 如果支持试学,也可以直接调用媒资服务获取视频地址
       /* String teachplan = coursepublish.getTeachplan();
        Teachplan teachplan1 = JSON.parseObject(JSON.toJSONString(teachplan), TeachplanDto.class);
        String isPreview = teachplan1.getIsPreview();
        if("1".equals(isPreview)){
            RestResponse<String> playUrlByMediaId = mediaServiceClient.getPlayUrlByMediaId(mediaId);
            return  playUrlByMediaId;
        }*/


        //1 判断用户是否登录以及是否具备学习资格
        //用户已登录
        if(StringUtil.isNotEmpty(userId)){
          //获取学习资格
            XcCourseTablesDto learningStatus = myCourseTablesService.getLearningStatus(userId, courseId);
            //[{"code":"702001","desc":"正常学习"},{"code":"702002","desc":"没有选课或选课后没有支付"},{"code":"702003","desc":"已过期需要申请续期或重新支付"}]
            String learnStatus = learningStatus.getLearnStatus();
            if("702002".equals(learnStatus)){
                return RestResponse.validfail("无法学习,因为没有选课或选课后没有支付");
            }else if("702003".equals(learnStatus)){
                return RestResponse.validfail("已过期需要申请续期或重新支付");
            }else{
                //2 有学习资格后,远程调用媒资服务查询视频的播放地址
                RestResponse<String> playUrlByMediaId = mediaServiceClient.getPlayUrlByMediaId(mediaId);
                return  playUrlByMediaId;
            }
        }
        //如果用户没有登陆
        //取出课程的收费规则
        String charge = coursepublish.getCharge();
        //如果当前课程是免费课程,则直接进入学习
        if ("201000".equals(charge)) {
            //有学习资格,远程调用媒资服务查询视频的播放地址
            RestResponse<String> playUrlByMediaId = mediaServiceClient.getPlayUrlByMediaId(mediaId);
            return  playUrlByMediaId;
        }

        return RestResponse.validfail("该课程需要购买");
    }
}
