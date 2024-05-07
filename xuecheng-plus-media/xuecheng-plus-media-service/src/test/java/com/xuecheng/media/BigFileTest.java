package com.xuecheng.media;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @program: xuecheng-plus-project
 * @description: 测试大文件上传类
 * @author: ldjc
 * @create: 2023-12-05 16:54
 **/
public class BigFileTest {
    /**
    * @Author: ldjc
    * @Description: 分块测试方法
    * @DateTime: 17:11 2023/12/5
    * @Params: []
    * @Return void
    */
    @Test
    public void testChunk() throws IOException {
         //源文件
        File sourceFile=new File("C:\\Users\\HP\\Downloads\\a.mp4");
        //分块文件存储路径
        String chunkFilePath="E:\\xuecheng-plus-project\\files\\";
        //分块文件大小
        int chunkSize=1024*1024*5; //5K
        //分块文件个数
        //Math.ceil 向上取整
        int chunkNum=(int)Math.ceil(sourceFile.length()*1.0/chunkSize);
        //使用流从源文件中读取数据,向分块文件中写数据
        //设置模式为只读
        RandomAccessFile raf_r = new RandomAccessFile(sourceFile,"r");
        //缓冲区
        byte[] bytes=new byte[1024];
        for (int i = 0; i < chunkNum; i++) {
            File chunkFile = new File(chunkFilePath + i);
            //创建分块文件的写入流
            RandomAccessFile raf_rw= new RandomAccessFile(chunkFile,"rw");
            int len=-1;
            while ((len=raf_r.read(bytes))!=-1){
                raf_rw.write(bytes,0,len);
                if(chunkFile.length()>=chunkSize){
                     break;
                }
            }
            raf_rw.close();
        }
        raf_r.close();
    }
    /**
    * @Author: ldjc
    * @Description: 合并测试方法
    * @DateTime: 17:12 2023/12/5
    * @Params: []
    * @Return void
    */
    @Test
    public  void testMerge() throws IOException {
       //分块文件目录
        String chunkFilePath="E:\\xuecheng-plus-project\\files";
        File file = new File(chunkFilePath);
        //源文件
        File sourceFile = new File("C:\\Users\\HP\\Downloads\\a.mp4");
        //合并后的文件
        File mergeFile = new File("C:\\Users\\HP\\Downloads\\a1.mp4");
        //取出所有的分块文件
        File[] files = file.listFiles();
        //将数组转成list
        List<File> files1 = Arrays.asList(files);
        //对分块文件进行排序
        Collections.sort(files1, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                //让集合升序排序
                return Integer.parseInt(o1.getName())-Integer.parseInt(o2.getName());
            }
        });
        RandomAccessFile raf_rw= new RandomAccessFile(mergeFile,"rw");
        //缓冲区
        byte[] bytes=new byte[1024];
        //遍历分块文件
        for (File file3 : files1) {
            //读分块的流
            RandomAccessFile raf_r= new RandomAccessFile(file3,"r");
            int len=-1;
            while((len=raf_r.read(bytes))!=-1){
                     raf_rw.write(bytes,0,len);
            }
            raf_r.close();
        }
        raf_rw.close();
        //合并文件完成后对合并的文件进行校验(md5)
        //合并文件的md5值
        FileInputStream fileInputStream_merge = new FileInputStream(mergeFile);
        //源文件的md5值
        FileInputStream fileInputStream_source = new FileInputStream(sourceFile);
        String md5_merge = DigestUtils.md5Hex(fileInputStream_merge);
        String md5_source = DigestUtils.md5Hex(fileInputStream_source);
        if(md5_merge.equals(md5_source)){
            System.out.println("文件合并成功");
        }

    }
}
