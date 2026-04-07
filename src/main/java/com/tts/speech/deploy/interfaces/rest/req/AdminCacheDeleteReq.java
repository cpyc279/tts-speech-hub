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
 * 后台缓存删除请求对象。
 *
 * @author yangchen5
 * @since 2026-03-06 19:38:00
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCacheDeleteReq {

    /**
     * 操作人标识。
     */
    @NotBlank(message = "operator 不能为空")
    @JsonProperty("operator")
    private String operator;

    /**
     * 后台鉴权令牌。
     */
    @JsonProperty("auth_token")
    private String authToken;

    /**
     * 待删除缓存键列表。
     */
    @NotEmpty(message = "key_list 不能为空")
    @JsonProperty("key_list")
    private List<@NotBlank(message = "key_list 不能包含空 key") String> keyList;
}
