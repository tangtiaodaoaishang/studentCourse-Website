package com.xuecheng.media.service.impl;

import com.xuecheng.media.mapper.MediaFilesMapper;
import com.xuecheng.media.mapper.MediaProcessHistoryMapper;
import com.xuecheng.media.mapper.MediaProcessMapper;
import com.xuecheng.media.model.po.MediaFiles;
import com.xuecheng.media.model.po.MediaProcess;
import com.xuecheng.media.model.po.MediaProcessHistory;
import com.xuecheng.media.service.MediaFileProcessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.events.Event;

import java.time.LocalDateTime;
import java.util.List;

/**
* @Author: ldjc
* @Description: MediaFileProcess接口实现
* @DateTime: 19:16 2023/12/12
* @Params:
* @Return
*/
@Slf4j
@Service
public class MediaFileProcessServiceImpl implements MediaFileProcessService {
    @Autowired
    private MediaProcessMapper m;
    @Autowired
    private MediaFilesMapper m1;
    @Autowired
    private MediaProcessHistoryMapper m2;
    /**
    * @Author: ldjc
    * @Description: 查询任务列表
    * @DateTime: 12:31 2023/12/13
    * @Params: [shardIndex, shardTotal, count]
    * @Return java.util.List<com.xuecheng.media.model.po.MediaProcess>
    */
    @Override
    public List<MediaProcess> getMediaProcessList(int shardIndex, int shardTotal, int count) {
        List<MediaProcess> mediaProcesses = m.selectListByShardIndex(shardTotal, shardIndex, count);
        return mediaProcesses;
    }

    /**
    * @Author: ldjc
    * @Description: 乐观锁开启一个任务
    * @DateTime: 12:31 2023/12/13
    * @Params: [id]
    * @Return boolean
    */
    @Override
    public boolean startTask(long id) {
        int result = m.startTask(id);
        return result<=0?false:true;
    }
    /**
    * @Author: ldjc
    * @Description: 保存任务列表
    * @DateTime: 12:33 2023/12/13
    * @Params: [taskId, status, fileId, url, errorMsg]
    * @Return void
    */
    @Override
    public void saveProcessFinishStatus(Long taskId, String status, String fileId, String url, String errorMsg) {
        //更新的任务
        MediaProcess mediaProcess = m.selectById(taskId);
        if(mediaProcess==null){
            return;
        }
        //======如果任务执行失败=======
         if(status.equals("3")){
             //更新media_process表的状态
             mediaProcess.setStatus("3");
             //失败次数+1
             mediaProcess.setFailCount(mediaProcess.getFailCount()+1);
             mediaProcess.setErrormsg(errorMsg);
             m.updateById(mediaProcess);
             //将上边的更新方式更改为以下有效的更新方式
//             m.update();
             return;
         }
        //======如果任务执行成功========
        //文件表记录
        MediaFiles mediaFiles = m1.selectById(fileId);
        //更新medio_file表中的url
        mediaFiles.setUrl(url);
        m1.updateById(mediaFiles);
        //更新media_process表中的状态
        mediaProcess.setStatus("2");
        mediaProcess.setFinishDate(LocalDateTime.now());
        mediaProcess.setUrl(url);
        m.updateById(mediaProcess);
        //将media_process表记录数据插入到media_process_history表中
        MediaProcessHistory mediaProcessHistory = new MediaProcessHistory();
        BeanUtils.copyProperties(mediaProcess,mediaProcessHistory);
        m2.insert(mediaProcessHistory);
        //从media_process表删除当前任务
        m.deleteById(taskId);
    }

}
