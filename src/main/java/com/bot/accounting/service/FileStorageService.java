package com.bot.accounting.service;

import com.bot.accounting.config.MinioConfig;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {
    
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    
    /**
     * 上传文件到 MinIO
     */
    public String uploadFile(InputStream inputStream, String fileName, String contentType) 
            throws Exception {
        try {
            // 确保 bucket 存在
            if (!bucketExists()) {
                createBucket();
            }
            
            log.info("正在上传文件到 MinIO: {}", fileName);
            
            // 上传文件
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(fileName)
                    .stream(inputStream, -1, 50 * 1024 * 1024) // 最大 50MB
                    .contentType(contentType)
                    .build()
            );
            
            log.info("文件上传成功：{}", fileName);
            return fileName;
            
        } catch (Exception e) {
            log.error("文件上传失败：{}", fileName, e);
            throw new RuntimeException("文件上传失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 生成预签名下载 URL（临时访问链接）
     * @param fileName 文件名
     * @param expirySeconds 有效期（秒）
     * @return 下载链接
     */
    public String getPresignedUrl(String fileName, int expirySeconds) {
        try {
            log.debug("生成预签名 URL: {}, 有效期：{}秒", fileName, expirySeconds);
            
            String url = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(io.minio.http.Method.GET)
                    .bucket(minioConfig.getBucket())
                    .object(fileName)
                    .expiry(expirySeconds, TimeUnit.SECONDS)
                    .build()
            );
            
            log.info("生成预签名 URL 成功：{}", url);
            return url;
            
        } catch (Exception e) {
            log.error("生成预签名 URL 失败：{}", fileName, e);
            throw new RuntimeException("生成下载链接失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 下载文件
     */
    public InputStream downloadFile(String fileName) {
        try {
            log.debug("下载文件：{}", fileName);
            
            return minioClient.getObject(
                io.minio.GetObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(fileName)
                    .build()
            );
            
        } catch (Exception e) {
            log.error("下载文件失败：{}", fileName, e);
            throw new RuntimeException("下载文件失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 删除文件
     */
    public void deleteFile(String fileName) {
        try {
            log.info("删除文件：{}", fileName);
            
            minioClient.removeObject(
                io.minio.RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(fileName)
                    .build()
            );
            
            log.info("文件删除成功：{}", fileName);
            
        } catch (Exception e) {
            log.error("文件删除失败：{}", fileName, e);
            throw new RuntimeException("文件删除失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 检查 bucket 是否存在
     */
    private boolean bucketExists() throws Exception {
        return minioClient.bucketExists(
            BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build()
        );
    }
    
    /**
     * 创建 bucket
     */
    private void createBucket() throws Exception {
        log.info("创建存储桶：{}", minioConfig.getBucket());
        
        minioClient.makeBucket(
            MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build()
        );
        
        log.info("存储桶创建成功：{}", minioConfig.getBucket());
    }
}
