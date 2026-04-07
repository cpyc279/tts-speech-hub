package com.tts.speech.deploy.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码枚举。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Getter
@AllArgsConstructor
public enum ErrorCodeEnum {

    /**
     * 成功。
     */
    SUCCESS(0, "success"),
    /**
     * 请求参数非法。
     */
    INVALID_PARAMETER(400001, "请求参数非法"),
    /**
     * 原始文本列表为空。
     */
    EMPTY_RAW_TEXT_LIST(400002, "raw_text_list 为空"),
    /**
     * 缓存键列表为空。
     */
    EMPTY_KEY_LIST(400003, "key_list 为空"),
    /**
     * 鉴权失败。
     */
    AUTH_FAILED(400004, "鉴权失败"),
    /**
     * 时间戳无效。
     */
    TIMESTAMP_INVALID(400005, "时间戳无效"),
    /**
     * 签名校验失败。
     */
    SIGN_INVALID(400006, "签名校验失败"),
    /**
     *
     */
    SYSTEM_ERROR(500000, "系统错误"),
    /**
     * TTS 主备接口全部调用失败。
     */
    TTS_ALL_FAILED(500101, "同花顺 TTS 全部接口调用失败"),
    /**
     * TTS 路由配置缺失。
     */
    TTS_ROUTE_MISSING(500102, "TTS 路由配置缺失"),
    /**
     * 调用小模型 TTS 失败。
     */
    TTS_SMALL_MODEL_CALL_FAILED(500103, "调用小模型 TTS 失败"),
    /**
     * 调用大模型 TTS 失败。
     */
    TTS_LARGE_MODEL_CALL_FAILED(500104, "调用大模型 TTS 失败"),
    /**
     * NER 服务调用失败。
     */
    NER_CALL_FAILED(500105, "NER 服务调用失败"),
    /**
     * OSS 上传失败。
     */
    OSS_UPLOAD_FAILED(500106, "OSS 上传失败"),
    /**
     * 缓存删除失败。
     */
    CACHE_DELETE_FAILED(500301, "缓存删除失败");

    /**
     * 错误码。
     */
    private final Integer code;

    /**
     * 错误描述。
     */
    private final String message;
}
