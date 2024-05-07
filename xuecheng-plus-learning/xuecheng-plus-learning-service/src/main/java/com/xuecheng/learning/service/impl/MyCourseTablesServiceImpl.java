package com.xuecheng.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.base.model.PageResult;
import com.xuecheng.content.model.po.CoursePublish;
import com.xuecheng.learning.feignclient.ContentServiceClient;
import com.xuecheng.learning.mapper.XcChooseCourseMapper;
import com.xuecheng.learning.mapper.XcCourseTablesMapper;
import com.xuecheng.learning.model.dto.MyCourseTableParams;
import com.xuecheng.learning.model.dto.XcChooseCourseDto;
import com.xuecheng.learning.model.dto.XcCourseTablesDto;
import com.xuecheng.learning.model.po.XcChooseCourse;
import com.xuecheng.learning.model.po.XcCourseTables;
import com.xuecheng.learning.service.MyCourseTablesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @program: xuecheng-plus-project
 * @description: 选课相关接口实现
 * @author: ldjc
 * @create: 2024-02-08 17:18
 **/
@Service
@Slf4j
public class MyCourseTablesServiceImpl implements MyCourseTablesService {
    @Autowired
    XcChooseCourseMapper xcChooseCourseMapper;
    @Autowired
    XcCourseTablesMapper xcCourseTablesMapper;
    //远程调用内容管理服务接口
    @Autowired
    ContentServiceClient contentServiceClient;

    @Transactional
    @Override
    //XcChooseCourseDto 选课记录表实体类
    public XcChooseCourseDto addChooseCourse(String userId, Long courseId) {
        //选课服务远程调用内容管理服务查询课程的收费规则
        CoursePublish coursepublish = contentServiceClient.getCoursepublish(courseId);
        if (coursepublish == null) {
            XueChengPlusException.cast("课程不存在");
        }
        //获取课程的收费规则信息
        String charge = coursepublish.getCharge();
        //用于存储选课记录
        XcChooseCourse xcChooseCourse=null;
        //"201000"是免费课程
        if ("201000".equals(charge)) {
            //如果是免费课程,会向选课记录表和我的课程表写数据
            //1 向选课记录表写数据
            xcChooseCourse = addFreeCoruse(userId, coursepublish);
            //2 向课程表写数据
            XcCourseTables xcCourseTables = addCourseTabls(xcChooseCourse);
        } else {
            //如果是收费课程,会向选课记录表写数据
           xcChooseCourse = addChargeCoruse(userId, coursepublish);

        }
        //判断学生的学习资格(XcCourseTablesDto 课程表实体类)
        XcCourseTablesDto learningStatus = getLearningStatus(userId, courseId);
        //构造返回值
        XcChooseCourseDto xcChooseCourseDto = new XcChooseCourseDto();
        BeanUtils.copyProperties(xcChooseCourse,xcChooseCourseDto);
        xcChooseCourseDto.setLearnStatus(learningStatus.getLearnStatus());
        return xcChooseCourseDto;
    }

    //判断学生的学习资格
    //学习资格，[{"code":"702001","desc":"正常学习"},{"code":"702002","desc":"没有选课或选课后没有支付"},{"code":"702003","desc":"已过期需要申请续期或重新支付"}]
    @Override
    public XcCourseTablesDto getLearningStatus(String userId, Long courseId) {
        //最终返回的学习资格结果
        XcCourseTablesDto xcCourseTablesDto = new XcCourseTablesDto();
        //1 查询我的课程表,如果查不到记录,则说明没有选课
        XcCourseTables xcCourseTables = getXcCourseTables(userId, courseId);
        if(xcCourseTables==null){
            //{"code":"702002","desc":"没有选课或选课后没有支付"}
            xcCourseTablesDto.setLearnStatus("702002");
            return xcCourseTablesDto;

        }
        //2 如果查到了记录,判断课程是否过期,如果过期也不能继续学习
        //获取课程截止时间
        LocalDateTime validtimeEnd = xcCourseTables.getValidtimeEnd();
        if(validtimeEnd.isBefore(LocalDateTime.now())){
            //课程过期
            //{"code":"702003","desc":"已过期需要申请续期或重新支付"}
            BeanUtils.copyProperties(xcCourseTables,xcCourseTablesDto);
            xcCourseTablesDto.setLearnStatus("702003");
            return xcCourseTablesDto;
        //3 如果查到了记录并且课程没有过期则可以继续学习
        }else{
            //{"code":"702001","desc":"正常学习"}
            BeanUtils.copyProperties(xcCourseTables,xcCourseTablesDto);
            xcCourseTablesDto.setLearnStatus("702001");
            return xcCourseTablesDto;
        }

    }

    @Override
    public boolean saveChooseCourseSuccess(String chooseCourseId) {
       //根据选课的id查询选课表
        XcChooseCourse chooseCourse = xcChooseCourseMapper.selectById(chooseCourseId);
        if(chooseCourse==null){
            log.debug("接收到购买课程的消息,根据课程id从数据库找不到选课记录,选课id:{}",chooseCourseId);
            return false;
        }
        //选课状态
        String status = chooseCourse.getStatus();
        //701002:选课状态为待支付状态
        //只有待支付才更新为已支付状态
        if("701002".equals(status)){
           //更新选课记录表的状态为支付成功
            chooseCourse.setStatus("701001");
            int i = xcChooseCourseMapper.updateById(chooseCourse);
            if(i<=0){
                log.debug("更新选课记录状态失败:{}",chooseCourse);
                XueChengPlusException.cast("更新选课记录状态失败:{}");
            }
            //向我的课程表插入消息队列数据
            XcCourseTables xcCourseTables = addCourseTabls(chooseCourse);
        }
        return true;
    }

    //我的学习功能查询课程表信息逻辑(即xc_course_tables)
    @Override
    public PageResult<XcCourseTables> mycoursetables(MyCourseTableParams params) {
        //当前用户id
        String userId = params.getUserId();
        //分页中的当前页码
        int pageNo = params.getPage();
        //分页中的每页记录数(页码)
        int size = params.getSize();
        Page<XcCourseTables> xcCourseTablesPage = new Page<>(pageNo,size);
        LambdaQueryWrapper<XcCourseTables> lambdaQueryWrapper  = new LambdaQueryWrapper<XcCourseTables>().eq(XcCourseTables::getUserId, userId);
        //根据用户id查询课程表数据
        Page<XcCourseTables> result = xcCourseTablesMapper.selectPage(xcCourseTablesPage, lambdaQueryWrapper);
        //PageResult类中参数:List<T> items, long counts, long page, long pageSize
        //数据列表
        List<XcCourseTables> records = result.getRecords();
        //总记录数
        long total = result.getTotal();
        PageResult<XcCourseTables> pageResult = new PageResult<>(records, total, pageNo, size);
        return pageResult;
    }

    //添加免费课程,免费课程加入选课记录表、我的课程表
    public XcChooseCourse addFreeCoruse(String userId, CoursePublish coursepublish) {
        //课程id
        Long courseId = coursepublish.getId();
        //判断如果存在免费的课程记录并且选课状态为成功,直接返回第一条记录
        LambdaQueryWrapper<XcChooseCourse> eq = new LambdaQueryWrapper<XcChooseCourse>().eq(XcChooseCourse::getUserId, userId)
                .eq(XcChooseCourse::getCourseId,courseId)
                .eq(XcChooseCourse::getOrderType,"701001") //"701001"为免费课程
                .eq(XcChooseCourse::getStatus,"701001"); //"701001"为选课状态成功
        List<XcChooseCourse> xcChooseCourses = xcChooseCourseMapper.selectList(eq);
        if(xcChooseCourses.size()>0){
            return xcChooseCourses.get(0);
        }
        //向选课记录表去写入数据
        XcChooseCourse xcChooseCourse = new XcChooseCourse();
        xcChooseCourse.setCourseId(courseId);
        xcChooseCourse.setCourseName(coursepublish.getName());
        xcChooseCourse.setUserId(userId);
        xcChooseCourse.setCompanyId(coursepublish.getCompanyId());
        xcChooseCourse.setOrderType("700001"); //"700001"为免费课程
        xcChooseCourse.setCreateDate(LocalDateTime.now());
        xcChooseCourse.setCoursePrice(coursepublish.getPrice());
        xcChooseCourse.setValidDays(365);   //(课程有效期)
        xcChooseCourse.setStatus("701001"); //"701001"为选课成功
        xcChooseCourse.setValidtimeStart(LocalDateTime.now()); //开始时间
        xcChooseCourse.setValidtimeEnd(LocalDateTime.now().plusDays(365)); //结束时间(当前时间+有效期)
        int insert = xcChooseCourseMapper.insert(xcChooseCourse);
        if(insert<=0){
            XueChengPlusException.cast("添加选课记录失败");
        }

        return xcChooseCourse;
    }


    //添加到我的课程表
    public XcCourseTables addCourseTabls(XcChooseCourse xcChooseCourse) {
        //选课状态为成功才可以向课程表添加记录
        String status = xcChooseCourse.getStatus();
        if(!"701001".equals(status)){
          XueChengPlusException.cast("选课没有成功,无法添加到课程表");
        }
        XcCourseTables xcCourseTables = getXcCourseTables(xcChooseCourse.getUserId(), xcChooseCourse.getCourseId());
        if(xcCourseTables!=null){
            return xcCourseTables;
        }
         xcCourseTables = new XcCourseTables();
        BeanUtils.copyProperties(xcChooseCourse,xcCourseTables);
        xcCourseTables.setChooseCourseId(xcChooseCourse.getId()); //记录选课表中的主键
        xcCourseTables.setCourseType(xcChooseCourse.getOrderType()); //选课类型
        xcCourseTables.setUpdateDate(LocalDateTime.now());
        int insert = xcCourseTablesMapper.insert(xcCourseTables);
        if(insert<=0){
            XueChengPlusException.cast("添加我的课程表失败");
        }
        return xcCourseTables;
    }
    /**
     * @description 查询课程表记录
     * @param userId
     * @param courseId
     * @return com.xuecheng.learning.model.po.XcCourseTables
     * @author Mr.M
     * @date 2022/10/2 17:07
     */
    public XcCourseTables getXcCourseTables(String userId,Long courseId){
        //因为课程表字段设计了唯一约束条件,因此采用selectOne即可
        XcCourseTables xcCourseTables = xcCourseTablesMapper.selectOne(new LambdaQueryWrapper<XcCourseTables>().eq(XcCourseTables::getUserId, userId).eq(XcCourseTables::getCourseId, courseId));
        return xcCourseTables;

    }
    //添加收费课程
    public XcChooseCourse addChargeCoruse(String userId, CoursePublish coursepublish) {
        //课程id
        Long courseId = coursepublish.getId();
        //判断如果存在收费的课程记录并且选课状态为待支付,直接返回第一条记录
        LambdaQueryWrapper<XcChooseCourse> eq = new LambdaQueryWrapper<XcChooseCourse>().eq(XcChooseCourse::getUserId, userId)
                .eq(XcChooseCourse::getCourseId,courseId)
                .eq(XcChooseCourse::getOrderType,"700002") //"700002"为收费课程
                .eq(XcChooseCourse::getStatus,"701002"); //"701002"为选课状态为待支付
        List<XcChooseCourse> xcChooseCourses = xcChooseCourseMapper.selectList(eq);
        if(xcChooseCourses.size()>0){
            return xcChooseCourses.get(0);
        }
        //向选课记录表去写入数据
        XcChooseCourse xcChooseCourse = new XcChooseCourse();
        xcChooseCourse.setCourseId(courseId);
        xcChooseCourse.setCourseName(coursepublish.getName());
        xcChooseCourse.setUserId(userId);
        xcChooseCourse.setCompanyId(coursepublish.getCompanyId());
        xcChooseCourse.setOrderType("700002"); //"700002"为收费课程
        xcChooseCourse.setCreateDate(LocalDateTime.now());
        xcChooseCourse.setCoursePrice(coursepublish.getPrice());
        xcChooseCourse.setValidDays(365);   //(课程有效期)
        xcChooseCourse.setStatus("701002"); //"701002"为选课状态为待支付
        xcChooseCourse.setValidtimeStart(LocalDateTime.now()); //开始时间
        xcChooseCourse.setValidtimeEnd(LocalDateTime.now().plusDays(365)); //结束时间(当前时间+有效期)
        int insert = xcChooseCourseMapper.insert(xcChooseCourse);
        if(insert<=0){
            XueChengPlusException.cast("添加选课记录失败");
        }

        return xcChooseCourse;

    }
}
