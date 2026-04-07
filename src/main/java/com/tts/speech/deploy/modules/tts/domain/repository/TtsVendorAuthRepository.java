package com.tts.speech.deploy.modules.tts.domain.repository;

import java.util.Map;

/**
 * 下游鉴权服务接口。
 *
 * @author yangchen5
 * @since 2026-03-06 20:20:00
 */
public interface TtsVendorAuthRepository {

    /**
     * 构建小模型表单参数。
     *
     * @param finalText 最终文本
     * @return 表单参数映射
     */
    Map<String, String> buildSmallModelForm(String finalText);

    /**
     * 构建大模型鉴权头。
     *
     * @param body 请求体
     * @return 鉴权头值
     */
    String buildLargeModelAuthorization(String body);
}
