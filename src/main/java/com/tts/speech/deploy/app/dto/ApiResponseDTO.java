package com.tts.speech.deploy.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tts.speech.deploy.common.enums.ErrorCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一接口响应 DTO。
 *
 * @param <T> 响应数据类型
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponseDTO<T> {

    /**
     * 状态码。
     */
    @JsonProperty("status_code")
    private Integer statusCode;

    /**
     * 状态描述信息。
     */
    @JsonProperty("status_msg")
    private String statusMsg;

    /**
     * 业务响应数据。
     */
    @JsonProperty("data")
    private T data;

    /**
     * 构建成功响应。
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 成功响应对象
     */
    public static <T> ApiResponseDTO<T> success(T data) {
        return new ApiResponseDTO<>(ErrorCodeEnum.SUCCESS.getCode(), ErrorCodeEnum.SUCCESS.getMessage(), data);
    }

    /**
     * 构建失败响应。
     *
     * @param statusCode 状态码
     * @param statusMsg  状态描述
     * @param <T>        数据类型
     * @return 失败响应对象
     */
    public static <T> ApiResponseDTO<T> failed(Integer statusCode, String statusMsg) {
        return new ApiResponseDTO<>(statusCode, statusMsg, null);
    }
}
