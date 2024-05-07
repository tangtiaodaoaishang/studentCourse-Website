package com.xuecheng.ucenter.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.ucenter.mapper.XcMenuMapper;
import com.xuecheng.ucenter.mapper.XcUserMapper;
import com.xuecheng.ucenter.model.dto.AuthParamsDto;
import com.xuecheng.ucenter.model.dto.XcUserExt;
import com.xuecheng.ucenter.model.po.XcMenu;
import com.xuecheng.ucenter.model.po.XcUser;
import com.xuecheng.ucenter.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @program: xuecheng-plus-project
 * @description: 连接数据库验证用户信息实现类
 * @author: ldjc
 * @create: 2024-01-20 18:03
 **/
@Slf4j
@Component
public class UserServiceImpl implements UserDetailsService {
    @Autowired
    XcUserMapper xcUserMapper;
    //权限菜单映射
    @Autowired
    XcMenuMapper xcMenuMapper;
    @Autowired
    ApplicationContext applicationContext;

    //传入的请求认证参数就是AuthParamsDto类中的属性
    @Override
    public UserDetails loadUserByUsername(String s) throws UsernameNotFoundException {
        //将传入的json数据转成AuthParamsDto对象
        AuthParamsDto authParamsDto=null;
        try{
            authParamsDto = JSON.parseObject(s,AuthParamsDto.class);
        }catch (Exception e){
            throw new RuntimeException("请求认证的参数不符合要求");
        }
        //认证类型
        String authType = authParamsDto.getAuthType();
        //根据认证类型从spring容器中取出指定的bean对象
        String beanName=authType+"_authservice";
        AuthService bean = applicationContext.getBean(beanName, AuthService.class);
        //调用统一的execute方法完成认证
        XcUserExt execute = bean.execute(authParamsDto);
        //封装execute用户信息为UserDetails对象
        //最终就是根据以下userDetails对象生成令牌
        UserDetails userDetails = getUserPrincipal(execute);
        return userDetails;
    }

    /**
     * @program: xuecheng-plus-project
     * @description: 查询用户信息
     * @author: ldjc
     * @create: 2024-01-20 18:03
     **/
    public UserDetails getUserPrincipal(XcUserExt xcUser){
        //密码
        String password = xcUser.getPassword();
        //初始权限(authorities数组)
        String[] authorities={"test"};
        //用户表中的userId字段
        String userId = xcUser.getId();
        //根据用户表中的userId字段查询出权限表中的权限信息
        List<XcMenu> xcMenus = xcMenuMapper.selectPermissionByUserId(userId);
        if(xcMenus.size()>0){
            ArrayList<Object> permissions = new ArrayList<>();
            xcMenus.forEach(i->{
                //往空的ArrayList集合添加权限表中特定的权限标识符字段值
                permissions.add(i.getCode());
            });
            //将permissions集合转成authorities数组
             authorities= permissions.toArray(new String[0]);
        }
        //设置密码为空,防止前端看到密码数据
        xcUser.setPassword(null);
        //将用户的信息转为JSON
        String userJson = JSON.toJSONString(xcUser);
        //spring security的登录信息对象userDetails,按照JWT令牌格式去储存信息,以便后续生成JWT令牌
        UserDetails userDetails = User.withUsername(userJson).password(password).authorities(authorities).build();
        return userDetails;
    }
}
