package com.tts.speech.deploy.modules.tts.infrastructure.repository.impl;

import com.hexin.speech.base.auth.util.AuthUtil;
import com.tts.speech.deploy.common.constant.StringPool;
import com.tts.speech.deploy.common.util.JsonUtil;
import com.tts.speech.deploy.common.util.Md5Util;
import com.tts.speech.deploy.modules.tts.domain.constant.TtsVendorConstant;
import com.tts.speech.deploy.modules.tts.domain.repository.TtsVendorAuthRepository;
import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechTtsProperties;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 厂商鉴权仓储实现。
 *
 * @author yangchen5
 * @since 2026-03-06 20:20:00
 */
@Component
public class TtsVendorAuthRepositoryImpl implements TtsVendorAuthRepository {

    private static final String TEXT_KEY = "text";
    private static final String ENGINE_NAME_KEY = "engineName";
    private static final String APP_ID_KEY = "appId";
    private static final String AUDIO_TYPE_KEY = "audioType";
    private static final String PITCH_KEY = "pitch";
    private static final String VOLUME_KEY = "vol";
    private static final String SAMPLING_RATE_KEY = "samplingRate";
    private static final String SAMPLE_DEPTH_KEY = "sampleDepth";
    private static final String SPEED_KEY = "speed";

    @Resource
    private SpeechTtsProperties speechTtsProperties;

    /**
     * 构建小模型表单参数。
     *
     * @param finalText 最终文本
     * @return 表单参数
     */
    @Override
    public Map<String, String> buildSmallModelForm(String finalText) {
        SpeechTtsProperties.SmallModelFormConfig smallModelFormConfig = speechTtsProperties.getEndpoint().getSmallModelForm();
        // 按接口约定组装小模型请求参数。
        Map<String, Object> paramMap = new LinkedHashMap<>();
        paramMap.put(TEXT_KEY, finalText);
        paramMap.put(ENGINE_NAME_KEY, speechTtsProperties.getEndpoint().getSmallModelEngineName());
        paramMap.put(APP_ID_KEY, speechTtsProperties.getEndpoint().getAppId());
        // 其余表单参数统一从配置读取，避免实现层保留魔法值。
        paramMap.put(AUDIO_TYPE_KEY, smallModelFormConfig.getAudioType());
        paramMap.put(PITCH_KEY, smallModelFormConfig.getPitch());
        paramMap.put(VOLUME_KEY, smallModelFormConfig.getVolume());
        paramMap.put(SAMPLING_RATE_KEY, smallModelFormConfig.getSamplingRate());
        paramMap.put(SAMPLE_DEPTH_KEY, smallModelFormConfig.getSampleDepth());
        paramMap.put(SPEED_KEY, smallModelFormConfig.getSpeed());

        // 小模型签名规则固定为 md5(appKey + param + timestamp)。
        String param = JsonUtil.toJson(paramMap);
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String secretKey = Md5Util.md5(speechTtsProperties.getEndpoint().getAppKey() + param + timestamp);

        Map<String, String> formMap = new LinkedHashMap<>();
        formMap.put(TtsVendorConstant.FORM_KEY_PARAM, param);
        formMap.put(TtsVendorConstant.FORM_KEY_TS, timestamp);
        formMap.put(TtsVendorConstant.FORM_KEY_SECRET_KEY, secretKey);
        return formMap;
    }

    /**
     * 构建大模型鉴权头。
     *
     * @param body 请求体
     * @return 鉴权值
     */
    @Override
    public String buildLargeModelAuthorization(String body) {
        String requestBody = body;
        if (requestBody == null) {
            // 下游鉴权工具要求 body 非 null，这里统一按空字符串处理。
            requestBody = StringPool.EMPTY;
        }
        // 大模型鉴权改为复用厂商提供的鉴权工具，小模型表单签名逻辑保持不变。
        return buildLargeModelAuthorizationToken(
            speechTtsProperties.getEndpoint().getAppId(),
            speechTtsProperties.getEndpoint().getAppKey(),
            System.currentTimeMillis(),
            UUID.randomUUID().toString(),
            speechTtsProperties.getEndpoint().getLargeModelPath(),
            requestBody);
    }

    /**
     * 构建大模型鉴权令牌。
     *
     * @param appId 应用 ID
     * @param appKey 应用密钥
     * @param timestamp 请求时间戳
     * @param nonce 请求随机串
     * @param path 接口路径
     * @param body 请求体
     * @return 鉴权令牌
     */
    static String buildLargeModelAuthorizationToken(
        String appId,
        String appKey,
        long timestamp,
        String nonce,
        String path,
        String body) {
        // 直接复用厂商鉴权工具，确保签名算法与接入文档保持一致。
        return AuthUtil.genAuthForPostString(appId, appKey, timestamp, nonce, path, body);
    }
}
