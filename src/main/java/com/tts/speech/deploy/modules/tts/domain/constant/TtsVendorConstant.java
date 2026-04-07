package com.tts.speech.deploy.modules.tts.domain.constant;

/**
 * TTS 厂商协议常量。
 *
 * @author yangchen5
 * @since 2026-03-09 13:39:46
 */
public final class TtsVendorConstant {

    /**
     * 表单参数字段名。
     */
    public static final String FORM_KEY_PARAM = "param";
    /**
     * 表单时间戳字段名。
     */
    public static final String FORM_KEY_TS = "ts";
    /**
     * 表单密钥字段名。
     */
    public static final String FORM_KEY_SECRET_KEY = "secretKey";
    /**
     * 小模型成功响应码。
     */
    public static final Integer SMALL_MODEL_SUCCESS_CODE = 1;
    /**
     * 大模型成功响应码。
     */
    public static final Integer LARGE_MODEL_SUCCESS_CODE = 10000;
    /**
     * 降级场景错误码。
     */
    public static final Integer FALLBACK_ERROR_CODE = -1;

    private TtsVendorConstant() {
    }
}
