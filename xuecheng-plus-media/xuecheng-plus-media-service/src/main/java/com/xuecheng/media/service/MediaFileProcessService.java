package com.xuecheng.media.service;

import com.xuecheng.media.model.po.MediaProcess;

import java.util.List;

public interface MediaFileProcessService {
    /**
    * @Author: ldjc
    * @Description: 查看任务列表
    * @DateTime: 12:32 2023/12/13
    * @Params: [shardIndex, shardTotal, count]
    * @Return java.util.List<com.xuecheng.media.model.po.MediaProcess>
    */
    public List<MediaProcess> getMediaProcessList(int shardIndex, int shardTotal, int count);
    /**
    * @Author: ldjc
    * @Description: 开启一个任务
    * @DateTime: 11:42 2023/12/13
    * @Params: [id 任务id]
    * @Return boolean
    */
    public boolean startTask(long id);

    /**
    * @Author: ldjc
    * @Description: 保存任务列表
    * @DateTime: 11:48 2023/12/13
    * @Params: [taskId 任务id, status 任务状态, fileId 文件id, url 文件路径, errorMsg 错误信息]
    * @Return void
    */
    void saveProcessFinishStatus(Long taskId,String status,String fileId,String url,String errorMsg);
}
