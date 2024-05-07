package com.xuecheng.orders;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.xuecheng.orders.config.AlipayConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @program: xuecheng-plus-project
 * @description: 支付宝查询账单测试
 * @author: ldjc
 * @create: 2024-02-14 14:12
 **/
@SpringBootTest
public class AliPayTest {

    @Value("${pay.alipay.APP_ID}")
    String APP_ID;
    @Value("${pay.alipay.APP_PRIVATE_KEY}")
    String APP_PRIVATE_KEY;

    @Value("${pay.alipay.ALIPAY_PUBLIC_KEY}")
    String ALIPAY_PUBLIC_KEY;

    @Test
    public void queryPayResult() throws AlipayApiException {
        //参数1:支付宝网关地址
        AlipayClient alipayClient = new DefaultAlipayClient(AlipayConfig.URL,APP_ID,APP_PRIVATE_KEY,AlipayConfig.FORMAT,AlipayConfig.CHARSET,ALIPAY_PUBLIC_KEY,AlipayConfig.SIGNTYPE);
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", "1757724384626642944");    //用户账单号
//bizContent.put("trade_no", "2014112611001004680073956707");   //支付宝自己的账单号
        request.setBizContent(bizContent.toString());
        AlipayTradeQueryResponse response = alipayClient.execute(request);
        System.out.println(response.getBody());
    }
}
