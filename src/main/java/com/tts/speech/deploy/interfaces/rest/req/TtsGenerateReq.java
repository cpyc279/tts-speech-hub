package com.tts.speech.deploy.interfaces.rest.req;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TTS 生成请求对象。
 *
 * @author yangchen5
 * @since 2026-03-06 19:38:00
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TtsGenerateReq {

    /**
     * 客户端签名。
     */
    @NotBlank(message = "sign 不能为空")
    @JsonProperty("sign")
    private String sign;

    /**
     * 时间戳。
     */
    @NotBlank(message = "timestamp 不能为空")
    @JsonProperty("timestamp")
    private String timestamp;

    /**
     * 券商编号。
     */
    @JsonProperty("broker_id")
    private String brokerId;

    /**
     * 资金账户。
     */
    @JsonProperty("user_id")
    private String userId;

    /**
     * 业务标识。
     */
    @JsonProperty("business_code")
    private String businessCode;

    /**
     * 设备型号。
     */
    @JsonProperty("device_model")
    private String deviceModel;

    /**
     * 系统版本。
     */
    @JsonProperty("system_version")
    private String systemVersion;

    /**
     * 待合成原始文本列表。
     */
    @NotEmpty(message = "raw_text_list 不能为空")
    @JsonProperty("raw_text_list")
    private List<@NotBlank(message = "raw_text_list 不能包含空文本") String> rawTextList;
}
