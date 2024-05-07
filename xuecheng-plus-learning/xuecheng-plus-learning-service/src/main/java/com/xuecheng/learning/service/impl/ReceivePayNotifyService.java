package com.xuecheng.learning.service.impl;

import com.alibaba.fastjson.JSON;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.learning.config.PayNotifyConfig;
import com.xuecheng.learning.service.MyCourseTablesService;
import com.xuecheng.messagesdk.model.po.MqMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @program: xuecheng-plus-project
 * @description: 消费方接收生产方消息队列(支付结果)
 * @author: ldjc
 * @create: 2024-02-15 18:09
 **/
@Slf4j
@Service
public class ReceivePayNotifyService {
    @Autowired
    MyCourseTablesService myCourseTablesService;
    //用于接收生产方发送过来的消息队列数据
    @RabbitListener(queues = PayNotifyConfig.PAYNOTIFY_QUEUE)
    public void receive(Message message) {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //解析出消息
        byte[] body = message.getBody();
        String jsonString = new String(body);
        //将字符串结果转为对象类型
        MqMessage mqMessage = JSON.parseObject(jsonString, MqMessage.class);
        //解析消息的内容
        //选课id chooseCourseId
        String chooseCourseId = mqMessage.getBusinessKey1();
        //订单类型
        String orderType = mqMessage.getBusinessKey2();
        //学习中心服务(learning)只要购买课程类(60201)的支付订单
        if(orderType.equals("60201")){
            //根据消息内容,更新选课记录表(xc_choose_course),并且向我的课程表(xc_course_tables)写入消息记录
            boolean b = myCourseTablesService.saveChooseCourseSuccess(chooseCourseId);
            if(!b){
                XueChengPlusException.cast("保存选课记录状态失败");
            }
        }


    }
}
