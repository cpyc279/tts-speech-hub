package com.tts.speech.deploy.modules.tts.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * NER 模板缓存实体。
 *
 * @author yangchen5
 * @since 2026-03-18 00:00:00
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NerTemplateCacheEntity {

    /**
     * 模板唯一标识。
     */
    private String templateId;

    /**
     * 模板正则表达式。
     */
    private String templatePattern;

    /**
     * 模板命中次数。
     */
    private Integer useCount;

    /**
     * 模板最近一次使用时间戳。
     */
    private Long lastMatchedAt;
}
