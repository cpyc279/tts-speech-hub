package com.tts.speech.deploy.modules.tts.infrastructure.repository.impl;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.tts.speech.deploy.common.constant.CommonConstant;
import com.tts.speech.deploy.common.constant.StringPool;
import com.tts.speech.deploy.common.enums.ErrorCodeEnum;
import com.tts.speech.deploy.common.exception.BizException;
import com.tts.speech.deploy.modules.tts.domain.repository.OssStorageRepository;
import com.tts.speech.deploy.modules.tts.infrastructure.config.AmazonS3Properties;
import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechOssProperties;
import jakarta.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * OSS 存储仓储实现。
 *
 * @author yangchen5
 * @since 2026-03-06 20:20:00
 */
@Slf4j
@Service
public class OssStorageRepositoryImpl implements OssStorageRepository {

    private static final String AUDIO_WAV_CONTENT_TYPE = "audio/wav";

    @Resource
    private AmazonS3 amazonS3;

    @Resource
    private AmazonS3Properties amazonS3Properties;

    @Resource
    private SpeechOssProperties speechOssProperties;

    /**
     * 上传 Base64 音频并返回公网访问地址。
     *
     * @param finalText 最终文本
     * @param base64Audio Base64 音频
     * @param traceId 链路追踪标识
     * @return 公网访问地址
     */
    @Override
    public String uploadBase64(String finalText, String base64Audio, String traceId) {
        String fileName = buildObjectKey();
        try {
            byte[] audioBytes = Base64.getDecoder().decode(base64Audio);
            ObjectMetadata objectMetadata = buildObjectMetadata(audioBytes);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(audioBytes);
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                amazonS3Properties.getBucketName(),
                fileName,
                inputStream,
                objectMetadata).withCannedAcl(CannedAccessControlList.PublicRead);
            amazonS3.putObject(putObjectRequest);
            return buildPublicUrl(fileName);
        } catch (IllegalArgumentException exception) {
            log.warn("Base64音频解码失败，traceId={}, finalText={}", traceId, finalText, exception);
            throw new BizException(ErrorCodeEnum.OSS_UPLOAD_FAILED);
        } catch (AmazonServiceException exception) {
            log.warn("上传音频失败，traceId={}, finalText={}", traceId, finalText, exception);
            throw new BizException(ErrorCodeEnum.OSS_UPLOAD_FAILED);
        } catch (SdkClientException exception) {
            log.warn("上传音频失败，traceId={}, finalText={}", traceId, finalText, exception);
            throw new BizException(ErrorCodeEnum.OSS_UPLOAD_FAILED);
        }
    }

    private ObjectMetadata buildObjectMetadata(byte[] audioBytes) {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentLength(audioBytes.length);
        objectMetadata.setContentType(AUDIO_WAV_CONTENT_TYPE);
        return objectMetadata;
    }

    private String buildObjectKey() {
        String fileName = UUID.randomUUID() + CommonConstant.WAV_SUFFIX;
        if (Boolean.TRUE.equals(speechOssProperties.getAppendDatePath())) {
            return String.join(
                StringPool.SLASH,
                speechOssProperties.getObjectPrefix(),
                LocalDate.now().format(DateTimeFormatter.ofPattern(CommonConstant.DATE_PATH_PATTERN)),
                fileName);
        }
        return String.join(StringPool.SLASH, speechOssProperties.getObjectPrefix(), fileName);
    }

    private String buildPublicUrl(String objectKey) {
        return CommonConstant.HTTP_PREFIX + String.join(
            StringPool.SLASH,
            amazonS3Properties.getDomain(),
            amazonS3Properties.getBucketName(),
            objectKey);
    }
}
