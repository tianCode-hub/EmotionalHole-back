package com.myk.emotionalHole.service.impl;

import com.myk.emotionalHole.service.FileUploadService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传服务实现类
 * 优先上传 MinIO 对象存储，MinIO 不可用时自动降级为本地磁盘上传，全链路不中断
 */
@Service
public class FileUploadServiceImpl implements FileUploadService {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadServiceImpl.class);

    private static final Map<String, String> CONTENT_TYPE_MAP = new HashMap<>();

    static {
        CONTENT_TYPE_MAP.put("jpg", "image/jpeg");
        CONTENT_TYPE_MAP.put("jpeg", "image/jpeg");
        CONTENT_TYPE_MAP.put("png", "image/png");
        CONTENT_TYPE_MAP.put("gif", "image/gif");
        CONTENT_TYPE_MAP.put("webp", "image/webp");
    }

    @Autowired(required = false)
    private MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String minioBucketName;

    @Value("${minio.access-path}")
    private String minioAccessPath;

    @Value("${file.upload.path:uploads/images}")
    private String uploadPath;

    @Value("${file.upload.url-prefix:/images/}")
    private String urlPrefix;

    @Value("${file.upload.allowed-types:jpg,jpeg,png,gif,webp}")
    private String allowedTypes;

    /**
     * 上传图片：校验类型→生成UUID文件名→优先上传MinIO，失败降级本地磁盘→返回访问URL
     */
    @Override
    public String uploadImage(MultipartFile file) {
        try {
            // 1. 验证文件是否为空
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("上传的文件不能为空");
            }

            // 2. 验证文件类型
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                throw new IllegalArgumentException("文件名不能为空");
            }

            String fileExtension = getFileExtension(originalFilename);
            List<String> allowedTypeList = Arrays.asList(allowedTypes.split(","));
            if (!allowedTypeList.contains(fileExtension.toLowerCase())) {
                throw new IllegalArgumentException("不支持的文件类型，仅支持: " + allowedTypes);
            }

            // 3. 魔数校验：读取文件头字节验证真实类型，防止伪装图片（如脚本改名 .jpg 上传）
            validateMagicNumber(file, fileExtension);

            // 4. 生成唯一文件名
            String uniqueFileName = UUID.randomUUID().toString().replace("-", "") + "." + fileExtension;

            // 5. 优先上传 MinIO 对象存储
            if (minioClient != null) {
                try {
                    return uploadToMinio(file, uniqueFileName, fileExtension);
                } catch (Exception e) {
                    logger.warn("MinIO 上传失败，降级为本地磁盘上传: {}", e.getMessage());
                }
            }

            // 6. 降级：保存到本地磁盘
            return uploadToLocal(file, uniqueFileName);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 上传到 MinIO 对象存储
     * @return 访问URL：access-path + "/" + bucketName + "/" + objectName
     */
    private String uploadToMinio(MultipartFile file, String uniqueFileName, String fileExtension) throws Exception {
        // 确保 Bucket 存在（不存在则自动创建）
        boolean bucketExists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(minioBucketName).build());
        if (!bucketExists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioBucketName).build());
            logger.info("自动创建 MinIO Bucket: {}", minioBucketName);
        }

        // 上传文件
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioBucketName)
                    .object(uniqueFileName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(CONTENT_TYPE_MAP.getOrDefault(fileExtension.toLowerCase(), "application/octet-stream"))
                    .build());
        }
        logger.info("文件上传 MinIO 成功: {}/{}", minioBucketName, uniqueFileName);

        return minioAccessPath + "/" + minioBucketName + "/" + uniqueFileName;
    }

    /**
     * 降级方案：保存到本地磁盘
     */
    private String uploadToLocal(MultipartFile file, String uniqueFileName) throws IOException {
        // 创建上传目录（使用绝对路径确保目录存在）
        String userDir = System.getProperty("user.dir");
        Path absoluteUploadDir = Paths.get(userDir, uploadPath);
        if (!Files.exists(absoluteUploadDir)) {
            Files.createDirectories(absoluteUploadDir);
            logger.info("创建上传目录: {}", absoluteUploadDir.toAbsolutePath());
        }

        // 保存文件
        Path filePath = absoluteUploadDir.resolve(uniqueFileName);
        File destFile = filePath.toFile();
        file.transferTo(destFile);
        logger.info("文件上传本地成功: {}", filePath.toAbsolutePath());

        // 返回访问URL
        return urlPrefix + uniqueFileName;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex + 1);
        }
        throw new IllegalArgumentException("无法获取文件扩展名");
    }

    /**
     * 魔数校验：读取文件头字节，与扩展名对应的真实图片格式比对。
     * 防止攻击者将可执行文件/脚本改名伪装成图片上传。
     * 支持的格式及头部特征：
     *  - JPEG: FF D8 FF
     *  - PNG:  89 50 4E 47 0D 0A 1A 0A
     *  - GIF:  'GIF8'（GIF87a / GIF89a）
     *  - WEBP: 'RIFF'....'WEBP'（offset 8 起 4 字节）
     */
    private void validateMagicNumber(MultipartFile file, String fileExtension) throws IOException {
        byte[] header = new byte[12];
        try (InputStream in = file.getInputStream()) {
            int read = in.read(header);
            if (read < 4) {
                throw new IllegalArgumentException("文件内容无效或已被破坏");
            }
        }

        boolean matched;
        switch (fileExtension.toLowerCase()) {
            case "jpg":
            case "jpeg":
                matched = startsWith(header, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
                break;
            case "png":
                matched = startsWith(header, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A});
                break;
            case "gif":
                matched = startsWith(header, new byte[]{'G', 'I', 'F', '8'});
                break;
            case "webp":
                matched = startsWith(header, new byte[]{'R', 'I', 'F', 'F'})
                        && header.length >= 12
                        && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
                break;
            default:
                matched = true; // 其他类型由 allowed-types 白名单兜底
        }

        if (!matched) {
            throw new IllegalArgumentException("文件内容与扩展名不匹配，疑似伪装文件，已拒绝上传");
        }
    }

    /** 判断文件头是否以 magicBytes 开头 */
    private boolean startsWith(byte[] header, byte[] magicBytes) {
        if (header.length < magicBytes.length) {
            return false;
        }
        for (int i = 0; i < magicBytes.length; i++) {
            if (header[i] != magicBytes[i]) {
                return false;
            }
        }
        return true;
    }
}
