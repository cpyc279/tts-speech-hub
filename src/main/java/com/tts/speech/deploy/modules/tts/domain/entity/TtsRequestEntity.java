package com.tts.speech.deploy.modules.tts.domain.entity;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TTS 请求领域实体。
 *
 * @author yangchen5
 * @since 2026-03-06 21:10:00
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TtsRequestEntity {

    /**
     * 券商 ID。
     */
    private String brokerId;

    /**
     * 资金账户。
     */
    private String userId;

    /**
     * 业务标识。
     */
    private String businessCode;

    /**
     * 设备型号。
     */
    private String deviceModel;

    /**
     * 系统版本。
     */
    private String systemVersion;

    /**
     * 请求签名。
     */
    private String sign;

    /**
     * 时间戳。
     */
    private String timestamp;

    /**
     * 链路追踪 ID。
     */
    private String traceId;

    /**
     * 原始文本列表。
     */
    private List<String> rawTextList;
}
