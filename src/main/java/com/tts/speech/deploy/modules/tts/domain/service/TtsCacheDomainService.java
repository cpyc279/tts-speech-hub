package com.tts.speech.deploy.modules.tts.domain.service;

import java.util.List;

/**
 * TTS 缓存领域服务接口。
 *
 * @author yangchen5
 * @since 2026-03-09 11:18:00
 */
public interface TtsCacheDomainService {

    /**
     * 过滤删除失败的缓存键。
     *
     * @param keyList 待删除缓存键列表
     * @return 删除失败的缓存键列表
     */
    List<String> filterDeleteFailedKeys(List<String> keyList);

    /**
     * 根据缓存 key 查询缓存 value。
     *
     * @param key 缓存 key
     * @return 缓存 value
     */
    Object getCacheValue(String key);
}
