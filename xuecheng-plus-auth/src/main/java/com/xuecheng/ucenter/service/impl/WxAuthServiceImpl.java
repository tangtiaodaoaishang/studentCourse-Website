package com.xuecheng.ucenter.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.ucenter.mapper.XcUserMapper;
import com.xuecheng.ucenter.mapper.XcUserRoleMapper;
import com.xuecheng.ucenter.model.dto.AuthParamsDto;
import com.xuecheng.ucenter.model.dto.XcUserExt;
import com.xuecheng.ucenter.model.po.XcUser;
import com.xuecheng.ucenter.model.po.XcUserRole;
import com.xuecheng.ucenter.service.AuthService;
import com.xuecheng.ucenter.service.WxAuthService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * @program: xuecheng-plus-project
 * @description: 微信扫码认证(采用策略模式)
 * @author: ldjc
 * @create: 2024-01-20 19:15
 **/
@Service("wx_authservice")
public class WxAuthServiceImpl implements AuthService, WxAuthService {
    @Autowired
    XcUserMapper xcUserMapper;
    @Autowired
    XcUserRoleMapper xcUserRoleMapper;
    //代理对象
    @Autowired
    WxAuthServiceImpl currentProxy;
    @Value("${weixin.appid}")
    String appid;
    @Value("${weixin.secret}")
    String secret;
    //请求第三方服务的接口
    @Autowired
    RestTemplate restTemplate;
    @Override
    public XcUserExt execute(AuthParamsDto authParamsDto) {
        //获取账号
        String username = authParamsDto.getUsername();
        //查询数据库
        XcUser xcUser = xcUserMapper.selectOne(new LambdaQueryWrapper<XcUser>().eq(XcUser::getUsername, username));
        if (xcUser==null){
            throw new RuntimeException("用户不存在");
        }
        XcUserExt xcUserExt = new XcUserExt();
        BeanUtils.copyProperties(xcUser,xcUserExt);
        return xcUserExt;
    }

    @Override
    public XcUser wxAuth(String code) {
        //1 申请令牌
        Map<String, String> access_token_map = getAccess_token(code);
        //访问令牌
        String access_token = access_token_map.get("access_token");
        String openid = access_token_map.get("openid");
        //2 携带令牌去查询用户信息
        Map<String, String> userinfo = getUserinfo(access_token, openid);
        //3 保存用户信息到数据库
        XcUser xcUser = currentProxy.addWxUser(userinfo);
        return xcUser;
    }

    /**
    * @Author: ldjc
    * @Description: 携带授权码申请令牌
     * POST https://api.weixin.qq.com/sns/oauth2/access_token?appid=APPID&secret=SECRET&code=CODE&grant_type=authorization_code
     * 响应内容:
     * {
     *   "access_token": "ACCESS_TOKEN",
     *   "expires_in": 7200,
     *   "refresh_token": "REFRESH_TOKEN",
     *   "openid": "OPENID",
     *   "scope": "snsapi_userinfo",
     *   "unionid": "o6_bmasdasdsad6_2sgVt7hMZOPfL"
     * }
    * @DateTime: 17:36 2024/1/21
    * @Params: [code 授权码]
    * @Return java.util.Map<java.lang.String,java.lang.String>
    */
    private Map<String,String> getAccess_token(String code) {
        String url_template="https://api.weixin.qq.com/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code";
        //拼接上述url字符串中的参数(用%s拼接)
        String url = String.format(url_template, appid, secret, code);
        //远程调用此url
        ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.POST, null, String.class);
        //获取响应的结果(请求体 response)
        String body = exchange.getBody();
        //将字符串转为map集合
        Map<String,String> map = JSON.parseObject(body, Map.class);
        return map;

    }
    /**
    * @Author: ldjc
    * @Description: 携带令牌去查询用户信息
     * GET https://api.weixin.qq.com/sns/userinfo?access_token=ACCESS_TOKEN&openid=OPENID
     * 响应内容:
     * {
     *   "openid": "OPENID",
     *   "nickname": "NICKNAME",
     *   "sex": 1,
     *   "province": "PROVINCE",
     *   "city": "CITY",
     *   "country": "COUNTRY",
     *   "headimgurl": "https://thirdwx.qlogo.cn/mmopen/g3MonUZtNHkdmzicIlibx6iaFqAc56vxLSUfpb6n5WKSYVY0ChQKkiaJSgQ1dZuTOgvLLrhJbERQQ4eMsv84eavHiaiceqxibJxCfHe/0",
     *   "privilege": ["PRIVILEGE1", "PRIVILEGE2"],
     *   "unionid": " o6_bmasdasdsad6_2sgVt7hMZOPfL"
     * }
    * @DateTime: 18:01 2024/1/21
    * @Params: [access_token, openid]
    * @Return java.util.Map<java.lang.String,java.lang.String>
    */
    private Map<String,String> getUserinfo(String access_token,String openid) {
       String url_template="https://api.weixin.qq.com/sns/userinfo?access_token=%s&openid=%s";
        String url= String.format(url_template, access_token, openid);
        ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
        //转编码(解决属性中乱码问题)
        String body = new String(exchange.getBody().getBytes(StandardCharsets.ISO_8859_1),StandardCharsets.UTF_8);
        Map<String,String> map = JSON.parseObject(body, Map.class);
        return map;

    }
    //保存用户信息
    @Transactional
    public XcUser addWxUser(Map<String,String> userInfo_map){
        //查询unionid
        String unionid = userInfo_map.get("unionid");
        String nickname = userInfo_map.get("nickname");
        //根据unionid查询用户信息
        XcUser xcUser = xcUserMapper.selectOne(new LambdaQueryWrapper<XcUser>().eq(XcUser::getWxUnionid, unionid));
        if(xcUser!=null){
            return xcUser;
        }
        //向数据库新增记录
        //1 向用户表新增记录
        xcUser=new XcUser();
        xcUser.setId(UUID.randomUUID().toString()); //主键
        xcUser.setUsername(unionid);
        xcUser.setPassword(unionid);
        xcUser.setWxUnionid(unionid);
        xcUser.setNickname(nickname);
        xcUser.setName(nickname);
        xcUser.setUtype("101001"); //学生类型
        xcUser.setStatus("1"); //用户状态
        xcUser.setCreateTime(LocalDateTime.now());
        //插入
        int insert = xcUserMapper.insert(xcUser);

        //2 向用户角色关系表新增记录
        XcUserRole xcUserRole = new XcUserRole();
        xcUserRole.setId(UUID.randomUUID().toString());
        xcUserRole.setUserId(xcUser.getId());
        xcUserRole.setRoleId("17"); //学生角色
        xcUserRole.setCreateTime(LocalDateTime.now());
        int insert1 = xcUserRoleMapper.insert(xcUserRole);
        return xcUser;
    }
}
