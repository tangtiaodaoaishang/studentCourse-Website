package com.xuecheng.media.service.jobhandler;

import com.xuecheng.base.utils.Mp4VideoUtil;
import com.xuecheng.media.model.po.MediaProcess;
import com.xuecheng.media.service.MediaFileProcessService;
import com.xuecheng.media.service.MediaFileService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @program: xuecheng-plus-project
 * @description:XxlJob开发示例(Bean模式)
 * @author: ldjc
 * @create: 2023-12-13 19:53
 **/
@Slf4j
@Component
public class VideoTask {
    @Autowired
    private MediaFileProcessService m;
    @Autowired
    private MediaFileService m1;
    //ffmpeg的路径
    @Value("${videoprocess.ffmpegpath}")
    private String  ffmpegPath;
    /**
     * 1、视频处理任务
     */
    @XxlJob("videoJobHandler")
    public void shardingJobHandler() throws Exception {

        // 分片参数
        int shardIndex = XxlJobHelper.getShardIndex(); //执行器的序号,从0开始
        int shardTotal = XxlJobHelper.getShardTotal(); //执行器总数

        //确定电脑cpu的核心数,避免过多资源浪费
        int processors = Runtime.getRuntime().availableProcessors();
        //1.查询待处理任务
        List<MediaProcess> mediaProcessList = m.getMediaProcessList(shardTotal,shardIndex,processors);
        //任务数量
        int size = mediaProcessList.size();
        log.debug("取到的处理视频任务数:"+size);
        if(size<=0){
            return;
        }
        //2.新建一个线程池
        ExecutorService executorService = Executors.newFixedThreadPool(size);
        //使用的计数器
        CountDownLatch countDownLatch=new CountDownLatch(size);
        mediaProcessList.forEach(mediaProcess -> {
         //3.将任务加入线程池
            executorService.execute(()->{
                try {
                    //任务id
                    Long taskId = mediaProcess.getId();
                    //获取文件md5值(文件id)
                    String fileId = mediaProcess.getFileId();
                    //开启任务(乐观锁)  false表示已有任务抢占
                    boolean b = m.startTask(taskId);
                    if (!b) {
                        log.debug("抢占任务失败,任务id:{}", taskId);
                        return;
                    }

                    //桶名
                    String bucket = mediaProcess.getBucket();
                    //对象名(路径) objectName
                    String objectName = mediaProcess.getFilePath();

                    //4.下载minio视频到本地
                    File file = m1.downloadFileFromMinIO(bucket, objectName);
                    if (file == null) {
                        log.debug("下载视频出错,任务id:{},bucket:{},objectName:{}", taskId, bucket, objectName);
                        //保存任务处理失败的结果
                        m.saveProcessFinishStatus(taskId, "3", fileId, null, "下载视频到本地失败");
                        return;
                    }
                    //源avi视频的路径
                    String video_path = file.getAbsolutePath();
                    //转换后mp4文件的名称
                    String mp4_name = fileId + ".mp4";
                    //先创建一个临时文件,作为转换后的文件
                    File mp4File = null;
                    try {
                        mp4File = File.createTempFile("minio", ".mp4");
                    } catch (IOException e) {
                        log.debug("创建临时文件异常,{}", e.getMessage());
                        //保存任务处理失败的结果
                        m.saveProcessFinishStatus(taskId, "3", fileId, null, "创建临时文件异常");
                    }
                    //转换后mp4文件的路径
                    String mp4_path = mp4File.getAbsolutePath();
                    //创建工具类对象
                    //5.执行视频转码
                    Mp4VideoUtil videoUtil = new Mp4VideoUtil(ffmpegPath, video_path, mp4_name, mp4_path);
                    //开始视频转换，成功将返回success,失败返回失败原因
                    String result = videoUtil.generateMp4();
                    if (!result.equals("sccess")) {
                        log.debug("视频转码失败,原因:{},bucket:{},objectName:{}", result, bucket, objectName);
                        //保存任务视频转码失败的结果
                        m.saveProcessFinishStatus(taskId, "3", fileId, null, result);
                        return;
                    }
                    //6.上传到minio
                    boolean b1 = m1.addMediaFilesToMinIo(mp4File.getAbsolutePath(), "video/mp4", bucket, objectName);
                    if (!b1) {
                        log.debug("上传mp4视频到minio失败,taskId:{}", taskId);
                        //保存任务处理失败的结果
                        m.saveProcessFinishStatus(taskId, "3", fileId, null, "上传mp4视频到minio失败");
                        return;
                    }
                    //mp4文件的url
                    String filePath = getFilePath(fileId, ".mp4");
                    //保存任务处理成功的结果
                    m.saveProcessFinishStatus(taskId, "2", fileId, filePath, "任务处理成功");
                }finally {
                    //计数器减去1
                    countDownLatch.countDown();

                }

            });
        });
        //阻塞(直到size减为0,才取消阻塞,从而结束整个方法)
        //注意可能出现执行方法体时出现断电等不确定因素时,计数器就不会减去1,从而让任务无限次进入阻塞状态
           //--措施:设置一个最大限度的等待时间,超过该时间则自动解除阻塞状态(以下设置的是30分钟)
         countDownLatch.await(30, TimeUnit.MINUTES);

    }
    /**
    * @Author: ldjc
    * @Description: 获取上传到minio视频的路径(md5第一位/md5第二位/md5值/md5值.后缀名)
    * @DateTime: 21:02 2023/12/13
    * @Params: [fileMd5, fileExt]
    * @Return java.lang.String
    */
    private String getFilePath(String fileMd5,String fileExt){
        return fileMd5.substring(0,1)+"/"+fileMd5.substring(1,2)+"/"+fileMd5+"/"+fileMd5+fileExt;
    }
}
