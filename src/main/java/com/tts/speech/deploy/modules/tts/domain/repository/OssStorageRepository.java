package com.tts.speech.deploy.modules.tts.domain.repository;

/**
 * OSS 存储服务接口。
 *
 * @author yangchen5
 * @since 2026-03-06 20:20:00
 */
public interface OssStorageRepository {

    /**
     * 上传 Base64 音频并返回公网地址。
     *
     * @param finalText 最终文本
     * @param base64Audio Base64 音频
     * @param traceId 链路追踪标识
     * @return 公网访问地址
     */
    String uploadBase64(String finalText, String base64Audio, String traceId);
}
