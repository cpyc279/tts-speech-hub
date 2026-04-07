package com.tts.speech.deploy.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 后台缓存删除响应 DTO。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCacheDeleteDataDTO {

    /**
     * 删除成功数量。
     */
    @JsonProperty("success_count")
    private Long successCount;

    /**
     * 删除失败的缓存键列表。
     */
    @JsonProperty("fail_key_list")
    private List<String> failKeyList;
}
