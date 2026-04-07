package com.tts.speech.deploy.modules.tts.domain.repository;

import com.tts.speech.deploy.modules.tts.domain.entity.TtsAudioCacheEntity;
import java.util.List;

/**
 * 语音缓存仓储接口。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
public interface TtsCacheRepository {

    /**
     * 读取音频缓存。
     *
     * @param brokerId 券商 ID
     * @param key 缓存键
     * @return 音频缓存
     */
    TtsAudioCacheEntity getAudioCache(String brokerId, String key);

    /**
     * 写入音频缓存。
     *
     * @param brokerId 券商 ID
     * @param key 缓存键
     * @param audioCacheEntity 音频缓存
     */
    void putAudioCache(String brokerId, String key, TtsAudioCacheEntity audioCacheEntity);

    /**
     * 读取音频 URL 缓存。
     *
     * @param brokerId 券商 ID
     * @param key 缓存键
     * @return 音频 URL
     */
    String getAudioUrlCache(String brokerId, String key);

    /**
     * 写入音频 URL 缓存。
     *
     * @param brokerId 券商 ID
     * @param key 缓存键
     * @param audioUrl 音频 URL
     */
    void putAudioUrlCache(String brokerId, String key, String audioUrl);

    /**
     * 获取账户绑定的 traceId，不存在时创建；空 userId 场景直接返回随机 traceId。
     *
     * @param userId 用户 ID
     * @return traceId
     */
    String getOrCreateTraceId(String userId);

    /**
     * 删除缓存键列表。
     *
     * @param keyList 键列表
     * @return 删除成功数量
     */
    long deleteKeys(List<String> keyList);

    /**
     * 删除单个缓存键。
     *
     * @param key 缓存键
     * @return 是否删除成功
     */
    boolean deleteKey(String key);

    /**
     * 根据缓存 key 查询缓存 value。
     *
     * @param key 缓存 key
     * @return 缓存 value
     */
    Object getCacheValue(String key);
}
