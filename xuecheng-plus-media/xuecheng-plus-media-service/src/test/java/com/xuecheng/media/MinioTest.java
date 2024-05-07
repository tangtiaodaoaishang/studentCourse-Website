package com.xuecheng.media;

import com.alibaba.nacos.common.utils.IoUtils;
import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoUtil;
import com.sun.org.apache.bcel.internal.generic.NEW;
import io.minio.*;
import io.minio.errors.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.io.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

//测试minio的sdk功能(文件上传,删除,查询等等)
public class MinioTest {
    MinioClient minioClient =
            //构造者模式
            MinioClient.builder()
                    .endpoint("http://127.0.0.1:9000")
                    .credentials("minioadmin", "minioadmin")
                    .build();

    //上传文件
    @Test
    public void test_upload() throws Exception {
        //通过扩展名得到媒体资源类型mimetype(依赖叫做simplemagic)
        ContentInfo extensionMatch = ContentInfoUtil.findExtensionMatch(".mp4");
        String mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE; //通用mimeType,字节流
        if(extensionMatch!=null){
             mimeType = extensionMatch.getMimeType();
        }

        UploadObjectArgs uploadObjectArgs=UploadObjectArgs.builder()
                        //确定桶的目录
                        .bucket("testbucket")
                        //确定桶的对象名
                        //.object("a.mp4") //在桶下根目录存储该文件
                        .object("test/01/a.mp4") //在桶下test/01/目录存储该文件
                        //设置媒体文件类型
                        .contentType(mimeType)
                        //指定本地的文件目录
                        .filename("C:\\Users\\HP\\Downloads\\a.mp4")
                        .build();
        minioClient.uploadObject(uploadObjectArgs);
    }
    //删除文件
    @Test
    public void test_delete() throws Exception {
        RemoveObjectArgs r=RemoveObjectArgs.builder().bucket("testbucket").object("a.mp4").build();
        minioClient.removeObject(r);
    }

    //查询文件 从minio中下载
    @Test
    public void test_getFile() throws IOException, InvalidKeyException, InvalidResponseException, InsufficientDataException, NoSuchAlgorithmException, ServerException, InternalException, XmlParserException, ErrorResponseException {
        GetObjectArgs getObjectArgs = GetObjectArgs.builder().bucket("testbucket").object("test/01/a.mp4").build();
//        GetObjectResponse object = minioClient.getObject(getObjectArgs);
        //查询远程服务获取到的一个流对象(注意远程获取通过网络,不稳定,会导致md5传输不一致)
        FilterInputStream inputStream = minioClient.getObject(getObjectArgs);
        //指定输出流
        FileOutputStream outputStream=new FileOutputStream(new File("C:\\Users\\HP\\Downloads\\a1.mp4"));
        IoUtils.copy(inputStream,outputStream);

        //校验文件的完整性,进行对文件的内容判断md5是否一致,如果一致则是完整的
        //读取初始文件的md5值
        FileInputStream fileInputStream1 = new FileInputStream(new File("C:\\Users\\HP\\Downloads\\a.mp4"));
        String source_md5 = DigestUtils.md5Hex(fileInputStream1);
        //读取下载到本地文件文件的md5值
        String local_md5 = DigestUtils.md5Hex(new FileInputStream(new File("C:\\Users\\HP\\Downloads\\a1.mp4")));
        if(source_md5.equals(local_md5)){
            System.out.println("下载成功");
        }
    }
    @Test
    //将分块文件上传到minio
    public void uploadChunk() throws IOException, InvalidKeyException, InvalidResponseException, InsufficientDataException, NoSuchAlgorithmException, ServerException, InternalException, XmlParserException, ErrorResponseException {
        for (int i=0;i<39;i++){
            UploadObjectArgs uploadObjectArgs=UploadObjectArgs.builder()
                    //确定桶的目录
                    .bucket("testbucket")
                    .object("chunk/"+i) //在桶下test/01/目录存储该文件
                    //指定本地的分块文件目录
                    .filename("E:\\xuecheng-plus-project\\files\\"+i)
                    .build();
            //上传文件
            minioClient.uploadObject(uploadObjectArgs);
            System.out.println("上传分块"+i+"成功");

        }

    }
    //调用minio接口,合并分块
    @Test
    public void testMerge() throws IOException, InvalidKeyException, InvalidResponseException, InsufficientDataException, NoSuchAlgorithmException, ServerException, InternalException, XmlParserException, ErrorResponseException {
//        List<ComposeSource> sources=new ArrayList<>();
        //指定分块文件的信息
      /*  for(int i=0;i<193;i++){
            ComposeSource composeSource= ComposeSource.builder().bucket("testbucket").object("chunk/"+i).build();
            sources.add(composeSource);
        }*/

        //Stream流优化for循环
        List<ComposeSource>  sources= Stream.iterate(0, i -> ++i).limit(39).map(i -> ComposeSource.builder().bucket("testbucket").object("chunk/" + i).build()).collect(Collectors.toList());
        //确定合并后的objectName等信息
        ComposeObjectArgs   composeObjectArgs= ComposeObjectArgs.builder().bucket("testbucket")
                .object("merge01.mp4")
                .sources(sources) //指定源文件
                .build();
        //合并分块文件(注意minio合并分块文件时,指定每个分块文件大小必须是5k,否则会报错)
        minioClient.composeObject(composeObjectArgs);
    }
    //批量清理分块文件

}
