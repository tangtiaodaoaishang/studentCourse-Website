package com.xuecheng.ucenter.service.impl;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.ucenter.feignclient.CheckCodeClient;
import com.xuecheng.ucenter.mapper.XcUserMapper;
import com.xuecheng.ucenter.model.dto.AuthParamsDto;
import com.xuecheng.ucenter.model.dto.XcUserExt;
import com.xuecheng.ucenter.model.po.XcUser;
import com.xuecheng.ucenter.service.AuthService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * @program: xuecheng-plus-project
 * @description: 账号名密码认证(采用策略模式)
 * @author: ldjc
 * @create: 2024-01-20 19:15
 **/
@Service("password_authservice")
public class PasswordAuthServiceImpl implements AuthService {
    @Autowired
    XcUserMapper xcUserMapper;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    CheckCodeClient checkCodeClient;
    @Override
    public XcUserExt execute(AuthParamsDto authParamsDto) {
        //账号
        String username=authParamsDto.getUsername();
        //输入的验证码值
        String checkcode = authParamsDto.getCheckcode();
        //输入的验证码的key
        String checkcodekey = authParamsDto.getCheckcodekey();
        //判断是否为空
        if(StringUtils.isEmpty(checkcode)||StringUtils.isEmpty(checkcodekey)){
            throw new RuntimeException("请输入验证码");
        }
        //远程调用验证码的接口来校验验证码
        Boolean verify = checkCodeClient.verify(checkcodekey, checkcode);
        if(verify==null||!verify){
            throw new RuntimeException("验证码输入错误");
        }

        //验证账号是否存在
        //根据username(账号)查询数据库
        XcUser xcUser = xcUserMapper.selectOne(new LambdaQueryWrapper<XcUser>().eq(XcUser::getUsername, username));
        //当查询到用户不存在,返回null即可,因为返回null之后spring security框架抛出异常提示用户不存在
        if(xcUser==null){
            throw new RuntimeException("账号不存在");
        }
        //验证密码是否正确
        //如果查询到用户,要拿到加密后的密码
        String passwordDb = xcUser.getPassword();
        //拿到用户输入的密码(原始密码)
        String passwordForm = authParamsDto.getPassword();
        //校验密码
        boolean matches = passwordEncoder.matches(passwordForm, passwordDb);
        if(!matches){
            throw new RuntimeException("账号或者密码错误");
        }
        XcUserExt xcUserExt = new XcUserExt();
        BeanUtils.copyProperties(xcUser,xcUserExt);
        return xcUserExt;
    }
}
