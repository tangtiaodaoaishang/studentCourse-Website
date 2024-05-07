package com.xuecheng.media.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuecheng.media.model.po.MediaProcess;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author itcast
 */
public interface MediaProcessMapper extends BaseMapper<MediaProcess> {
    /**
    * @Author: ldjc
    * @Description: 查询待处理任务列表
    * @DateTime: 19:09 2023/12/12
    * @Params: [shardTotal 分片总数.shardIndex 分片序号,count 查询行数]
    * @Return java.util.List<com.xuecheng.media.model.po.MediaProcess>
    */
    @Select("select * from media_process t where t.id % #{shardTotal}= #{shardIndex} and (t.status=1 or t.status=3) and t.fail_count<3 limit #{count})")
    List<MediaProcess> selectListByShardIndex(@Param("shardTotal") int shardTotal,@Param("shardIndex") int shardIndex,@Param("count") int count);
    /**
    * @Author: ldjc
    * @Description: 开启一个任务(使用数据库乐观锁机制)
    * @DateTime: 11:35 2023/12/13
    * @Params: [id 任务id]
    * @Return int
    */
   @Update("update media_process m set m.status='4' where (m.status='1' or m.status='3') and m.fail_count<3 and m.id=#{id}")
    int startTask(@Param("id") long id);
}
