package com.tts.speech.deploy.app.shared.auth;

import com.tts.speech.deploy.modules.tts.domain.entity.TtsRequestEntity;

/**
 * 客户端签名校验接口。
 *
 * @author yangchen5
 * @since 2026-03-09 21:36:00
 */
public interface SignAuthService {

    /**
     * 校验 TTS 请求签名。
     *
     * @param requestEntity TTS 请求
     */
    void validateTtsSign(TtsRequestEntity requestEntity);

    /**
     * 校验后台固定 token。
     *
     * @param adminToken 后台固定 token
     */
    void validateAdminAuth(String adminToken);
}
