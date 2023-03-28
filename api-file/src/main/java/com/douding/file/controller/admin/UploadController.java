package com.douding.file.controller.admin;

import com.alibaba.fastjson.JSON;
import com.douding.server.domain.Test;
import com.douding.server.dto.FileDto;
import com.douding.server.dto.ResponseDto;
import com.douding.server.enums.FileUseEnum;
import com.douding.server.exception.BusinessException;
import com.douding.server.exception.BusinessExceptionCode;
import com.douding.server.service.FileService;
import com.douding.server.service.TestService;
import com.douding.server.util.Base64ToMultipartFile;
import com.douding.server.util.UuidUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.*;
import java.util.List;

/*
    返回json 应用@RestController
    返回页面  用用@Controller
 */
@RequestMapping("/admin/file")
@RestController
public class UploadController {

    private static final Logger LOG = LoggerFactory.getLogger(UploadController.class);
    public  static final String BUSINESS_NAME ="文件上传";
    @Resource
    private TestService testService;

    @Value("${file.path}")
    private String FILE_PATH;

    @Value("${file.domain}")
    private String FILE_DOMAIN;

    @Resource
    private FileService fileService;

    @RequestMapping("/upload")
    public ResponseDto upload(@RequestBody FileDto fileDto) throws Exception {
        System.out.println("========== upload ==============");

        //将base64转MultipartFile
        MultipartFile multipartFile = Base64ToMultipartFile.base64ToMultipart(fileDto.getShard());
        //获取本地文件夹地址,拼接传入的参数use，得到一个本地文件夹路径，并判断是否存在，若不存在则直接创建
        String localDirPath = FILE_PATH + FileUseEnum.getByCode(fileDto.getUse());
        LOG.info("本地文件夹地址:{}", localDirPath);
        File dirFile = new File(localDirPath);
        //如果目标文件夹不存在，则直接创建一个
        if (!dirFile.exists() && !dirFile.mkdirs()) {
            LOG.error("文件夹创建失败，待创建路径:{}", localDirPath);
            throw new Exception("文件夹创建失败，创建路径:" + localDirPath);
        }
        //本地文件全路径
        String fileFullPath = localDirPath +
                File.separator +
                fileDto.getKey() +
                "." +
                fileDto.getSuffix();
        //创建文件分片全路径，将multipartFile写入到这个路径中
        String fileShardFullPath = fileFullPath +
                "." +
                fileDto.getShardIndex();

        multipartFile.transferTo(new File(fileShardFullPath));
        //更新文件表信息，无上传过这个文件则插入一条，反之直接更新索引值
        String relaPath = FileUseEnum.getByCode(fileDto.getUse()) + "/" + fileDto.getKey() + "." + fileDto.getSuffix();
        fileDto.setPath(relaPath);
        fileService.save(fileDto);
        //判断当前分片索引是否等于分片总数，如果等于分片总数则执行文件合并
        if (fileDto.getShardIndex().equals(fileDto.getShardTotal())) {
            fileDto.setPath(fileFullPath);
            //文件合并
            merge(fileDto);
        }

        ResponseDto responseDto = new ResponseDto();
        FileDto result = new FileDto();
        //设置文件映射地址给前端
        result.setPath(FILE_DOMAIN + "/" + relaPath);
        responseDto.setContent(result);
        LOG.info("文件分片上传结束，请求结果:{}", JSON.toJSONString(responseDto));
        return responseDto;

//        ResponseDto responseDto = new ResponseDto();
//        fileService.save(fileDto);
//        responseDto.setContent(fileDto);
//        LOG.info("responseDto", responseDto);
//        return responseDto;
    }

    //合并分片
    public void merge(FileDto fileDto) throws Exception {
        LOG.info("合并分片开始");
        System.out.println("========== merge ==============");
//        fileDto.setPath(FILE_PATH);
//        System.out.println(FILE_PATH);
//        System.out.println(FILE_DOMAIN);
//        upload(fileDto);
        String path = fileDto.getPath();
        try (OutputStream outputStream = new FileOutputStream(path, true)) {
            for (Integer i = 1; i <= fileDto.getShardTotal(); i++) {
                try (FileInputStream inputStream = new FileInputStream(path + "." + i);) {
                    byte[] bytes = new byte[10 * 1024 * 1024];
                    int len;
                    while ((len = inputStream.read(bytes)) != -1) {
                        outputStream.write(bytes, 0, len);
                    }
                }

            }
        } catch (Exception e) {
            LOG.error("文件合并失败，失败原因:{}", e.getMessage(), e);
        }


        //删除所有分片
        for (Integer i = 1; i <= fileDto.getShardTotal(); i++) {
            File file = new File(path + "." + i);
            file.delete();
        }

    }

    @GetMapping(value = "/check/{key}")
    public ResponseDto check(@PathVariable String key) throws Exception {
        LOG.info("检查上传分片开始：{}", key);
        System.out.println("========== check ==============");
        if (StringUtils.isEmpty(key)) {
            throw new BusinessException(BusinessExceptionCode.USER_LOGIN_NAME_EXIST);
        }
        FileDto fileDto = fileService.findByKey(key);
        ResponseDto responseDto = new ResponseDto();
        //如果不为空，则返回映射地址以及文件上传进度
        if (fileDto != null) {
            //将文件映射地址告知前端
            fileDto.setPath(FILE_DOMAIN + "/" + fileDto.getPath());
            responseDto.setContent(fileDto);
        }
        return responseDto;

//        if (file.isEmpty()) {
//            throw new RuntimeException("请选择图片");
//        }
//        // 获取名字
//        String name = file.getOriginalFilename();
//        System.out.println(name);
//        // 创建文件对象
////        File temp = new File(uploadPath);
//
//        FileDto fileDto = new FileDto();
//        fileDto.setKey(key);
//        merge(fileDto);
//
//        ResponseDto responseDto = new ResponseDto();
//        responseDto.setContent(fileDto);
//        return responseDto;
    }

}//end class
