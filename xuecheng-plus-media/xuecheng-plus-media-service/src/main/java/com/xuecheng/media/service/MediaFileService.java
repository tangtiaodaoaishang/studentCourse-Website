package com.xuecheng.media.service;

import com.xuecheng.base.model.PageParams;
import com.xuecheng.base.model.PageResult;
import com.xuecheng.base.model.RestResponse;
import com.xuecheng.media.model.dto.QueryMediaParamsDto;
import com.xuecheng.media.model.dto.UploadFileParamsDto;
import com.xuecheng.media.model.dto.UploadFileResultDto;
import com.xuecheng.media.model.po.MediaFiles;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

/**
 * @description 媒资文件管理业务类
 * @author Mr.M
 * @date 2022/9/10 8:55
 * @version 1.0
 */
public interface MediaFileService {

 /**
  * @description 媒资文件查询方法
  * @param pageParams 分页参数
  * @param queryMediaParamsDto 查询条件
  * @return com.xuecheng.base.model.PageResult<com.xuecheng.media.model.po.MediaFiles>
  * @author Mr.M
  * @date 2022/9/10 8:57
 */
 public PageResult<MediaFiles> queryMediaFiels(Long companyId,PageParams pageParams, QueryMediaParamsDto queryMediaParamsDto);

 //上传文件
 //三个参数:文件本地路径(localFilePath),本地文件信息(uploadFileParamsDto),机构id(companyId),新的文件本地目录(objectName):如果传入objectName参数,要按objectName的目录去存储,如果没有该参数,则继续按照localFilePath的目录(年月日)去存储
 public UploadFileResultDto uploadFile(Long companyId,UploadFileParamsDto uploadFileParamsDto,String localFilePath,String objectName);
 public MediaFiles addMediaFilesToDb(Long companyId, String fileMd5, UploadFileParamsDto uploadFileParamsDto, String bucket, String objectName);
 public RestResponse<Boolean> checkChunk(String fileMd5,int chunk);
 public RestResponse<Boolean> checkFile(String fileMd5);
 public RestResponse uploadChunk(String fileMd5,int chunk,String localFilePath);
 public RestResponse mergechunks(Long companyId,String fileMd5,int chunkTotal, UploadFileParamsDto uploadFileParamsDto);
 public File downloadFileFromMinIO(String bucket, String objectName);
 public boolean addMediaFilesToMinIo(String localFilePath, String mimeType, String bucket, String objectName);
 //根据媒资的id查询文件信息
 MediaFiles getFileById(String mediaId);
}
