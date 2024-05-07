package com.xuecheng.ucenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuecheng.ucenter.model.po.XcMenu;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author itcast
 */
public interface XcMenuMapper extends BaseMapper<XcMenu> {
    //查询指定用户的权限信息sql语句(通过5张表,1 用户表 2 用户与角色的中间表 3 角色表 4 角色与权限菜单中间表 5 权限菜单表)
    //过程:根据用户表中的userId字段连接用户与角色的中间表查询到对应的role_id字段,然后通过role_id字段连接角色与权限菜单中间表查询出对应的menu_id字段,最后通过menu_id字段连接权限菜单表查询出对应的权限信息
    @Select("SELECT	* FROM xc_menu WHERE id IN (SELECT menu_id FROM xc_permission WHERE role_id IN ( SELECT role_id FROM xc_user_role WHERE user_id = #{userId} ))")
    List<XcMenu> selectPermissionByUserId(@Param("userId") String userId);
}
