package com.tts.speech.deploy.modules.tts.domain.service;

import com.tts.speech.deploy.common.enums.TtsEndpointEnum;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsAudioEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsPreparedTextEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsRequestEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsResponseEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsRouteEntity;
import java.util.List;

/**
 * TTS 领域服务接口。
 *
 * @author yangchen5
 * @since 2026-03-06 20:15:00
 */
public interface TtsDomainService {

    /**
     * 执行端到端 TTS 合成。
     *
     * @param requestEntity TTS 请求
     * @return TTS 响应
     */
    TtsResponseEntity synthesize(TtsRequestEntity requestEntity);

    /**
     * 校验领域请求规则。
     *
     * @param requestEntity TTS 请求
     */
    void validateRequest(TtsRequestEntity requestEntity);

    /**
     * 为批量执行准备文本记录。
     *
     * @param requestEntity TTS 请求
     * @param modelCode 解析后的模型编码
     * @return 预处理文本列表
     */
    List<TtsPreparedTextEntity> prepareTexts(TtsRequestEntity requestEntity, String modelCode);

    /**
     * 执行主路端点，并在需要时切换备用端点。
     *
     * @param preparedTextEntityList 预处理文本列表
     * @param routeEntity 路由结果
     * @param traceId 链路追踪标识
     * @return 音频列表
     */
    List<TtsAudioEntity> executeWithFallback(
        List<TtsPreparedTextEntity> preparedTextEntityList,
        TtsRouteEntity routeEntity,
        String traceId);

    /**
     * 执行一轮整批端点合成。
     *
     * @param preparedTextEntityList 预处理文本列表
     * @param endpointEnum 目标端点
     * @param modelCode 模型编码
     * @param traceId 链路追踪标识
     * @return 音频列表
     */
    List<TtsAudioEntity> executeSingleRound(
        List<TtsPreparedTextEntity> preparedTextEntityList,
        TtsEndpointEnum endpointEnum,
        String modelCode,
        String traceId);

    /**
     * 将全部音频上传到 OSS。
     *
     * @param audioEntityList 音频列表
     * @param traceId 链路追踪标识
     * @return 上传后的音频列表
     */
    List<TtsAudioEntity> uploadAudioList(List<TtsAudioEntity> audioEntityList, String traceId);

    /**
     * 持久化非 SSML 文本的缓存记录。
     *
     * @param audioEntityList 音频列表
     * @param modelCode 模型编码
     * @param brokerId 券商 ID
     */
    void persistCache(List<TtsAudioEntity> audioEntityList, String modelCode, String brokerId);

}
