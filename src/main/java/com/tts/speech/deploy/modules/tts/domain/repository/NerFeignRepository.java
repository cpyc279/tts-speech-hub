package com.tts.speech.deploy.modules.tts.domain.repository;

import java.util.List;
import java.util.Optional;

/**
 * NER 外部服务接口。
 *
 * @author yangchen5
 * @since 2026-03-06 20:20:00
 */
public interface NerFeignRepository {

    /**
     * 按券商维度匹配可直接使用的成熟模板。
     *
     * @param brokerId 券商编号
     * @param rawText 原始文本
     * @param traceId 链路追踪标识
     * @return 模板命中的最终文本
     */
    Optional<String> matchAvailableTemplate(String brokerId, String rawText, String traceId);

    /**
     * 调用 NER 服务解析人名列表。
     *
     * @param brokerId 券商编号
     * @param rawText 原始文本
     * @param traceId 链路追踪标识
     * @return 人名列表
     */
    List<String> parsePeopleNames(String brokerId, String userId, String rawText, String traceId);

    /**
     * 合并模板命中次数并刷新模板过期时间。
     *
     * @param brokerId 券商编号
     * @param rawText 原始文本
     * @param peopleNameList 人名列表
     * @param traceId 链路追踪标识
     */
    void mergeTemplate(String brokerId, String rawText, List<String> peopleNameList, String traceId);
}
