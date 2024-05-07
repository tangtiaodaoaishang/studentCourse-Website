package com.xuecheng.orders.api;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.orders.config.AlipayConfig;
import com.xuecheng.orders.model.dto.AddOrderDto;
import com.xuecheng.orders.model.dto.PayRecordDto;
import com.xuecheng.orders.model.dto.PayStatusDto;
import com.xuecheng.orders.model.po.XcPayRecord;
import com.xuecheng.orders.service.OrderService;
import com.xuecheng.orders.util.SecurityUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @program: xuecheng-plus-project
 * @description: 支付课程订单接口
 * @author: ldjc
 * @create: 2024-02-14 15:36
 **/
@Api(value = "订单支付接口", tags = "订单支付接口")
@Slf4j
@Controller
public class OrderController {
    @Autowired
    OrderService orderService;
    @Value("${pay.alipay.APP_ID}")
    String APP_ID;
    @Value("${pay.alipay.APP_PRIVATE_KEY}")
    String APP_PRIVATE_KEY;
    @Value("${pay.alipay.ALIPAY_PUBLIC_KEY}")
    String ALIPAY_PUBLIC_KEY;

    @ApiOperation("生成支付二维码")
    @PostMapping("/generatepaycode")
    @ResponseBody
    //AddOrderDto:前端传过来的订单信息
    //生成支付二维码流程:1向学习中心服务添加选课记录 2向orders服务插入订单信息 3向orders服务插入支付记录
    public PayRecordDto generatePayCode(@RequestBody AddOrderDto addOrderDto) {
        SecurityUtil.XcUser user = SecurityUtil.getUser();
        String userId = user.getId();
        //调用service,完成插入订单信息,插入支付记录,生成支付二维码
        PayRecordDto order = orderService.createOrder(userId,addOrderDto);
        return order;
    }

    @ApiOperation("扫码下单接口")
    @GetMapping("/requestpay")
    public void requestpay(String payNo, HttpServletResponse httpResponse) throws IOException, AlipayApiException {
       //传入支付记录号,判断支付记录号是否存在
        XcPayRecord payRecordByPayno = orderService.getPayRecordByPayno(payNo);
        if(payRecordByPayno==null){
            XueChengPlusException.cast("支付记录不存在");
        }
        //支付结果
        String status = payRecordByPayno.getStatus();
        if("601002".equals(status))  {   //601002为已支付状态
            XueChengPlusException.cast("订单已支付,无需重复支付");
        }

        //请求支付宝下单
        AlipayClient alipayClient = new DefaultAlipayClient(AlipayConfig.URL, APP_ID, APP_PRIVATE_KEY, AlipayConfig.FORMAT, AlipayConfig.CHARSET, ALIPAY_PUBLIC_KEY,AlipayConfig.SIGNTYPE);
        //获得初始化的AlipayClient
        AlipayTradeWapPayRequest alipayRequest = new AlipayTradeWapPayRequest();//创建API对应的request
//        alipayRequest.setReturnUrl("http://domain.com/CallBack/return_url.jsp");
//        alipayRequest.setNotifyUrl("http://tjxt-user-t.itheima.net/xuecheng/orders/paynotify");//在公共参数中设置回跳和通知地址
        alipayRequest.setBizContent("{" +
                "    \"out_trade_no\":\""+payNo+"\"," +  //out_trade_no为订单号与课程id有关(该值是唯一的,即判断支付结果是否重复,如果重复则提示订单重复无法支付)
                "    \"total_amount\":"+payRecordByPayno.getTotalPrice()+"," +  //total_amount为支付价格
                "    \"subject\":\""+payRecordByPayno.getOrderName()+"\"," + //subject为支付手机型号
                "    \"product_code\":\"QUICK_WAP_WAY\"" + //product_code值切记不能修改为其他值
                "  }");//填充业务参数
        String form = alipayClient.pageExecute(alipayRequest).getBody(); //调用SDK请求支付宝下订单
        httpResponse.setContentType("text/html;charset=" + AlipayConfig.CHARSET);
        httpResponse.getWriter().write(form);//直接将完整的表单html输出到页面
        httpResponse.getWriter().flush();
    }

    @ApiOperation("查询支付结果")
    @GetMapping("/payresult")
    @ResponseBody
    public PayRecordDto payresult(String payNo) throws IOException {
        //1 查询支付结果
        PayRecordDto payRecordDto = orderService.queryPayResult(payNo);
        //2 当支付完成后,更新支付记录表的支付状态以及订单表中的状态字段信息为支付成功状态
        return payRecordDto;

    }

    /**
    * @Author: ldjc
    * @Description: 接收支付宝支付通知
    * @DateTime: 16:25 2024/2/15
    * @Params: [request, response]
    * @Return void
    */
    @ApiOperation("接收支付结果通知")
    @PostMapping("/paynotify")
    public void paynotify(HttpServletRequest request, HttpServletResponse response) throws IOException, AlipayApiException {
        //获取支付宝POST过来反馈信息
        Map<String,String> params = new HashMap<String,String>();
        Map requestParams = request.getParameterMap();
        for (Iterator iter = requestParams.keySet().iterator(); iter.hasNext();) {
            String name = (String) iter.next();
            String[] values = (String[]) requestParams.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i]
                        : valueStr + values[i] + ",";
            }

            params.put(name, valueStr);
        }

        boolean verify_result = AlipaySignature.rsaCheckV1(params,ALIPAY_PUBLIC_KEY, AlipayConfig.CHARSET, "RSA2");

        if(verify_result){//验证成功

            //商户订单号
            String out_trade_no = new String(request.getParameter("out_trade_no").getBytes("ISO-8859-1"),"UTF-8");
            //支付宝交易号
            String trade_no = new String(request.getParameter("trade_no").getBytes("ISO-8859-1"),"UTF-8");
            //交易金额
            String trade_amount = new String(request.getParameter("trade_amount").getBytes("ISO-8859-1"),"UTF-8");
            //交易状态
            String trade_status = new String(request.getParameter("trade_status").getBytes("ISO-8859-1"),"UTF-8");

          if (trade_status.equals("TRADE_SUCCESS")){
             //更新支付记录表和订单表的状态为成功
              PayStatusDto payStatusDto=new PayStatusDto();
              payStatusDto.setTrade_status(trade_status);
              payStatusDto.setTrade_no(trade_no);
              payStatusDto.setOut_trade_no(out_trade_no);
              payStatusDto.setTotal_amount(trade_amount);
              payStatusDto.setApp_id(APP_ID);
              orderService.saveAliPayStatus(payStatusDto);
            }

            //——请根据您的业务逻辑来编写程序（以上代码仅作参考）——
            response.getWriter().write("success");
            //////////////////////////////////////////////////////////////////////////////////////////
        }else{//验证失败
            response.getWriter().write("fail");


    }
    }

}
