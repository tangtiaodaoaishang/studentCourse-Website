package com.xuecheng.ucenter.service;

import com.xuecheng.ucenter.model.po.XcUser;

/**
 * @author Mr.M
 * @version 1.0
 * @description 微信认证接口
 * @date 2023/2/21 22:15
 */
public interface WxAuthService {
    /**
    * @Author: ldjc
    * @Description: 微信扫码认证 1 申请令牌 2 携带令牌去查询用户信息 3 保存用户信息到数据库
    * @DateTime: 17:31 2024/1/21
    * @Params: [code 授权码]
    * @Return com.xuecheng.ucenter.model.po.XcUser
    */
    public XcUser wxAuth(String code);
}
