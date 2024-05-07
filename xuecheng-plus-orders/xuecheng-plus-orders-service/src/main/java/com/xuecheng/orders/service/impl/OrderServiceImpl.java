package com.xuecheng.orders.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.base.utils.IdWorkerUtils;
import com.xuecheng.base.utils.QRCodeUtil;
import com.xuecheng.messagesdk.model.po.MqMessage;
import com.xuecheng.messagesdk.service.MqMessageService;
import com.xuecheng.orders.config.AlipayConfig;
import com.xuecheng.orders.config.PayNotifyConfig;
import com.xuecheng.orders.mapper.XcOrdersGoodsMapper;
import com.xuecheng.orders.mapper.XcOrdersMapper;
import com.xuecheng.orders.mapper.XcPayRecordMapper;
import com.xuecheng.orders.model.dto.AddOrderDto;
import com.xuecheng.orders.model.dto.PayRecordDto;
import com.xuecheng.orders.model.dto.PayStatusDto;
import com.xuecheng.orders.model.po.XcOrders;
import com.xuecheng.orders.model.po.XcOrdersGoods;
import com.xuecheng.orders.model.po.XcPayRecord;
import com.xuecheng.orders.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Correlation;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @program: xuecheng-plus-project
 * @description: 订单相关的实现接口
 * @author: ldjc
 * @create: 2024-02-14 15:45
 **/
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    XcOrdersMapper ordersMapper;
    @Autowired
    XcOrdersGoodsMapper ordersGoodsMapper;
    @Autowired
    XcPayRecordMapper xcPayRecordMapper;
    @Autowired
    OrderServiceImpl currentProxy;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    MqMessageService mqMessageService;

    //支付二维码的url值
    @Value("${pay.qrcodeurl}")
    String qrcodeurl;
    @Value("${pay.alipay.APP_ID}")
    String APP_ID;
    @Value("${pay.alipay.APP_PRIVATE_KEY}")
    String APP_PRIVATE_KEY;
    @Value("${pay.alipay.ALIPAY_PUBLIC_KEY}")
    String ALIPAY_PUBLIC_KEY;

    @Transactional
    @Override
    public PayRecordDto createOrder(String userId, AddOrderDto addOrderDto) {
        //1 插入订单信息(订单主表和订单明细表)
        XcOrders xcOrders = saveXcOrders(userId, addOrderDto);

        //2 插入支付记录
        XcPayRecord payRecord = createPayRecord(xcOrders);
        //支付号(用于传给后续二维码的out_trade_no值)
        Long payNo = payRecord.getPayNo();

        //3 生成支付二维码
        QRCodeUtil qrCodeUtil = new QRCodeUtil();
        //支付二维码的url
        String url = String.format(qrcodeurl,payNo);
        //二维码的图片
        String qrCode=null;
        try{
            qrCode = qrCodeUtil.createQRCode(url, 200, 200);
        }catch (Exception e){
            XueChengPlusException.cast("生成二维码失败");
        }
        //PayRecordDto实体类数据是XcPayRecord(支付记录表)数据+二维码数据
        PayRecordDto payRecordDto = new PayRecordDto();
        BeanUtils.copyProperties(payRecord,payRecordDto);
        payRecordDto.setQrcode(qrCode);
        return payRecordDto;
    }

    @Override
    public XcPayRecord getPayRecordByPayno(String payNo) {
        XcPayRecord xcPayRecord = xcPayRecordMapper.selectOne(new LambdaQueryWrapper<XcPayRecord>().eq(XcPayRecord::getPayNo, payNo));
        return xcPayRecord;
    }

    @Override
    public PayRecordDto queryPayResult(String payNo) {
        //1 调用支付宝的查询api接口来查询支付结果
        PayStatusDto payStatusDto = queryPayResultFromAlipay(payNo);
        System.out.println(payStatusDto);
        //2 拿到支付结果后,要更新支付记录表以及订单表的状态字段值
        currentProxy.saveAliPayStatus(payStatusDto);
        //要返回最新的支付记录信息
        XcPayRecord payRecordByPayno = getPayRecordByPayno(payNo);
        PayRecordDto payRecordDto = new PayRecordDto();
        BeanUtils.copyProperties(payRecordByPayno,payRecordDto);
        return payRecordDto;
    }
    /**
     * 请求支付宝查询支付结果
     * @param payNo 支付交易号
     * @return 支付结果
     */
    public PayStatusDto queryPayResultFromAlipay(String payNo){
        //参数1:支付宝网关地址
        AlipayClient alipayClient = new DefaultAlipayClient(AlipayConfig.URL,APP_ID,APP_PRIVATE_KEY,AlipayConfig.FORMAT,AlipayConfig.CHARSET,ALIPAY_PUBLIC_KEY,AlipayConfig.SIGNTYPE);
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", payNo);    //用户账单号
//bizContent.put("trade_no", "2014112611001004680073956707");   //支付宝自己的账单号
        request.setBizContent(bizContent.toString());
        //支付宝返回支付的信息
        String body=null;
        try{
            AlipayTradeQueryResponse response = alipayClient.execute(request);
            //如果交易不成功
            if (!response.isSuccess()){
               XueChengPlusException.cast("请求支付宝查询支付结果失败");
            }
            body = response.getBody();
        }catch (Exception e){
            XueChengPlusException.cast("请求支付宝查询支付结果异常");
        }
        Map bodyMap = JSON.parseObject(body, Map.class);
        Map alipay_trade_query_response =(Map) bodyMap.get("alipay_trade_query_response");
        //解析支付结果
        PayStatusDto payStatusDto = new PayStatusDto();
        payStatusDto.setOut_trade_no(payNo);
        payStatusDto.setTrade_no((String)alipay_trade_query_response.get("trade_no")); //支付宝的交易号
        payStatusDto.setTrade_status((String)alipay_trade_query_response.get("trade_status")); //交易状态
        payStatusDto.setApp_id(APP_ID);
        payStatusDto.setTotal_amount((String)alipay_trade_query_response.get("total_amount")); //总金额
        return payStatusDto;
    }
    /**
     * @description 保存支付宝支付结果
     * @param payStatusDto  支付结果信息(payStatusDto:从支付宝查询过来的支付信息)
     * @return void
     * @author Mr.M
     * @date 2022/10/4 16:52
     */
    @Transactional
    public void saveAliPayStatus(PayStatusDto payStatusDto){
        //1 判断支付是否成功,成功在执行以下两步
        //支付记录号
        String payNo = payStatusDto.getOut_trade_no();
        XcPayRecord payRecordByPayno = getPayRecordByPayno(payNo);
        if(payRecordByPayno==null){
            XueChengPlusException.cast("找不到相关的支付记录");
        }
        //拿到相关联的订单id(注意是支付记录表中的订单id)
        Long orderId = payRecordByPayno.getOrderId();
        XcOrders xcOrders = ordersMapper.selectById(orderId);
        if(xcOrders==null){
            XueChengPlusException.cast("找不到相关联的订单");
        }
        //支付状态(注意是支付记录表中的状态)
        String status = payRecordByPayno.getStatus();
        //如果支付记录表中的status字段值已经为成功状态,就不用在更新值了
        if("601002".equals(status)){  //601002为已支付状态
           //如果已经成功
            return;
        }
        //如果支付成功执行以下步骤
        String trade_status = payStatusDto.getTrade_status(); //从支付宝查询到的支付状态信息
        if(trade_status.equals("TRADE_SUCCESS")){ //支付宝返回的结果为支付成功
            //2 更新支付记录表的状态为支付成功
            payRecordByPayno.setStatus("601002"); //601002为支付记录表支付状态为已支付状态
            payRecordByPayno.setOutPayNo(payStatusDto.getTrade_no()); //第三方支付订单号
            payRecordByPayno.setOutPayChannel("AliPay"); //第三方支付交易渠道编号
            payRecordByPayno.setPaySuccessTime(LocalDateTime.now()); //支付成功时间
            xcPayRecordMapper.updateById(payRecordByPayno);
            //3 更新订单表的状态为支付成功
            xcOrders.setStatus("600002"); //600002:订单表支付状态为成功
            ordersMapper.updateById(xcOrders);

            //将支付消息写到数据库(mq_message)表
            MqMessage mqMessage = mqMessageService.addMessage("payresult_notify", xcOrders.getOutBusinessId(), xcOrders.getOrderType(), null);
            //发送支付消息
            notifyPayResult(mqMessage);

        }

    }

    //生产方(订单服务--orders)向消费方(学习中心服务--learning)发送消息队列(订单信息)
    @Override
    public void notifyPayResult(MqMessage message) {
        //消息内容
        String json = JSON.toJSONString(message);
        //创建一个持久化消息(MessageDeliveryMode.PERSISTENT:持久化)
        Message messageObj = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8)).setDeliveryMode(MessageDeliveryMode.PERSISTENT).build();
        //消息id
        Long id = message.getId();
        //全局消息id
        CorrelationData correlationData = new CorrelationData(id.toString());
        //使用CorrelationData指定回调方法
        correlationData.getFuture().addCallback(result->{
            //消息队列确认机制
            if(result.isAck()){
              //消息已成功发送到交换机
                log.debug("发送消息成功:{}",json);
                //将消息从数据库(mq_message)表中删掉
                mqMessageService.completed(id);
            }else {
              //消息发送失败
                log.debug("发送消息失败:{}",json);
            }
        },ex->{
           //发生异常了
            log.debug("发送消息出现异常:{}",json);
        });
        //发送消息
        rabbitTemplate.convertAndSend(PayNotifyConfig.PAYNOTIFY_EXCHANGE_FANOUT,"",messageObj,correlationData);
    }

    /**
    * @Author: ldjc
    * @Description: 保存支付记录
    * @DateTime: 17:17 2024/2/14
    * @Params: [orders]
    * @Return com.xuecheng.orders.model.po.XcPayRecord
    */
    public XcPayRecord createPayRecord(XcOrders orders){
        //订单id
        Long orderId = orders.getId();
        XcOrders xcOrders = ordersMapper.selectById(orderId);
        //1 如果此订单不存在,不能添加支付记录信息
        if(xcOrders==null){
            XueChengPlusException.cast("订单不存在");
        }
        //2 如果此订单支付结果为成功状态,也不能添加支付记录信息(避免重复支付)
        //订单状态
        String status = xcOrders.getStatus();
        if("601002".equals(status)){  //601002为已支付状态
          XueChengPlusException.cast("此订单已支付");
        }
        //3 添加支付记录
        XcPayRecord xcPayRecord = new XcPayRecord();
        //支付记录id,将来用于支付宝扫码支付号,用雪花算法工具类保证该字段的唯一性
        xcPayRecord.setPayNo(IdWorkerUtils.getInstance().nextId());
        xcPayRecord.setOrderId(orderId);
        xcPayRecord.setOrderName(xcOrders.getOrderName());
        xcPayRecord.setTotalPrice(xcOrders.getTotalPrice());
        xcPayRecord.setCurrency("CNY");
        xcPayRecord.setCreateDate(LocalDateTime.now());
        xcPayRecord.setStatus("601001");  //601001为未支付状态
        xcPayRecord.setUserId(xcOrders.getUserId());
        int insert = xcPayRecordMapper.insert(xcPayRecord);
        if(insert<=0){
            XueChengPlusException.cast("插入支付记录失败");
        }
         return xcPayRecord;
    }



    /**
    * @Author: ldjc
    * @Description: 保存订单信息
    * @DateTime: 15:58 2024/2/14
    * @Params: [userId, addOrderDto]
    * @Return com.xuecheng.orders.model.po.XcOrders
    */
    @Transactional
    public XcOrders saveXcOrders(String userId, AddOrderDto addOrderDto){
        //进行幂等性判断(同一个选课记录只能保存一个订单信息)
        XcOrders xcOrders = getOrderByBusinessId(addOrderDto.getOutBusinessId());
       if(xcOrders!=null){
           return xcOrders;
       }
        //插入订单信息(订单主表和订单明细表)
        //1 插入订单主表
         xcOrders = new XcOrders();
        //调用雪花算法工具类实现订单id唯一性
         xcOrders.setId(IdWorkerUtils.getInstance().nextId());
         xcOrders.setTotalPrice(addOrderDto.getTotalPrice());
         xcOrders.setCreateDate(LocalDateTime.now());
         xcOrders.setStatus("600001"); //600001为未支付状态
        xcOrders.setUserId(userId);
        xcOrders.setOrderType("60201"); //60201为订单类型是购买课程类型
        xcOrders.setOrderName(addOrderDto.getOrderName());
        xcOrders.setOrderDescrip(addOrderDto.getOrderDescrip());
        xcOrders.setOrderDetail(addOrderDto.getOrderDetail());
        xcOrders.setOutBusinessId(addOrderDto.getOutBusinessId()); //如果是选课,这里记录的是选课表的id
        int insert = ordersMapper.insert(xcOrders);
        if(insert<=0){
            XueChengPlusException.cast("添加订单失败");
        }

        //拿到订单id
        Long orderId = xcOrders.getId();
        //2 插入订单明细表
        //将前端传入账单明细的json串转成List集合
        String orderDetail = addOrderDto.getOrderDetail();
        List<XcOrdersGoods> xcOrdersGoods = JSON.parseArray(orderDetail, XcOrdersGoods.class);
        //遍历XcOrdersGoods数据
        xcOrdersGoods.forEach(goods ->{
            goods.setOrderId(orderId);
            //插入到订单明细表
            int insert1 = ordersGoodsMapper.insert(goods);

        });
        return xcOrders;

    }
    /**
    * @Author: ldjc
    * @Description: 根据业务id查询订单,业务id就是选课记录中的主键id
    * @DateTime: 16:01 2024/2/14
    * @Params: [businessId]
    * @Return com.xuecheng.orders.model.po.XcOrders
    */
    public XcOrders getOrderByBusinessId(String businessId) {
        XcOrders orders = ordersMapper.selectOne(new LambdaQueryWrapper<XcOrders>().eq(XcOrders::getOutBusinessId, businessId));
        return orders;
    }
}
