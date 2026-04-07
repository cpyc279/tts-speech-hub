package com.tts.speech.deploy.modules.tts.infrastructure.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tts.speech.deploy.common.constant.CommonConstant;
import com.tts.speech.deploy.common.constant.StringPool;
import com.tts.speech.deploy.common.metrics.TtsMetricsCollector;
import com.tts.speech.deploy.common.redis.RedisOperator;
import com.tts.speech.deploy.common.util.JsonUtil;
import com.tts.speech.deploy.common.util.TraceIdUtil;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsAudioCacheEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsAudioCacheIndexEntity;
import com.tts.speech.deploy.modules.tts.domain.repository.TtsCacheRepository;
import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechTtsProperties;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.DataType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 语音缓存仓储实现。
 *
 * @author yangchen5
 * @since 2026-03-06 18:08:56
 */
@Slf4j
@Component
public class TtsCacheRepositoryImpl implements TtsCacheRepository {

    /**
     * 空删除数量。
     */
    private static final Long EMPTY_DELETE_COUNT = 0L;

    /**
     * traceId 缓存前缀。
     */
    private static final String TRACE_CACHE_KEY_PREFIX = "speech:trace:user";

    /**
     * traceId 缓存 TTL，单位分钟。
     */
    private static final long TRACE_CACHE_TTL_MINUTES = 30L;

    /**
     * Redis 音频缓存读取操作名。
     */
    private static final String REDIS_OPERATION_AUDIO_CACHE_GET = "audio_cache_get";

    /**
     * Redis 音频缓存写入操作名。
     */
    private static final String REDIS_OPERATION_AUDIO_CACHE_PUT = "audio_cache_put";

    /**
     * Redis 音频 URL 缓存读取操作名。
     */
    private static final String REDIS_OPERATION_AUDIO_URL_CACHE_GET = "audio_url_cache_get";

    /**
     * Redis 音频 URL 缓存写入操作名。
     */
    private static final String REDIS_OPERATION_AUDIO_URL_CACHE_PUT = "audio_url_cache_put";

    /**
     * Redis 音频索引读取操作名。
     */
    private static final String REDIS_OPERATION_AUDIO_CACHE_INDEX_READ = "audio_cache_index_read";

    /**
     * Redis 音频索引写入操作名。
     */
    private static final String REDIS_OPERATION_AUDIO_CACHE_INDEX_WRITE = "audio_cache_index_write";

    /**
     * Redis 音频索引删除操作名。
     */
    private static final String REDIS_OPERATION_AUDIO_CACHE_INDEX_DELETE = "audio_cache_index_delete";

    /**
     * Redis traceId 操作名。
     */
    private static final String REDIS_OPERATION_TRACE_ID = "trace_id";

    /**
     * Redis 缓存删除操作名。
     */
    private static final String REDIS_OPERATION_CACHE_DELETE = "cache_delete";

    /**
     * Redis 缓存值读取操作名。
     */
    private static final String REDIS_OPERATION_CACHE_VALUE_GET = "cache_value_get";

    /**
     * 默认 Redis 访问组件，继续服务小 key。
     */
    @Resource
    private RedisOperator redisOperator;

    /**
     * Base64 大 key 专用 Redis 访问组件。
     */
    @Resource(name = "audioCacheRedisOperator")
    private RedisOperator audioCacheRedisOperator;

    /**
     * TTS 配置。
     */
    @Resource
    private SpeechTtsProperties speechTtsProperties;

    /**
     * TTS 指标采集器。
     */
    @Resource
    private TtsMetricsCollector ttsMetricsCollector;


    /**
     * ???????
     *
     * @param brokerId ?? ID
     * @param key ???
     * @return ????
     */
    @Override
    public TtsAudioCacheEntity getAudioCache(String brokerId, String key) {
        String cacheValue = audioCacheRedisOperator.getValue(
            REDIS_OPERATION_AUDIO_CACHE_GET,
            key,
            null,
            "?????????brokerId={}, key={}",
            brokerId,
            key);
        if (!StringUtils.hasText(cacheValue)) {
            deleteAudioCacheIndex(normalizeBrokerId(brokerId), List.of(key));
            return null;
        }

        TtsAudioCacheEntity audioCacheEntity = deserializeAudioCache(key, cacheValue);
        if (audioCacheEntity == null || !StringUtils.hasText(audioCacheEntity.getBase64Audio())) {
            return null;
        }

        String normalizedBrokerId = normalizeBrokerId(brokerId);
        TtsAudioCacheIndexEntity existingIndexEntity = readAudioCacheIndex(normalizedBrokerId, key);
        audioCacheEntity.setCreatedAt(resolveAudioCacheCreatedAt(existingIndexEntity));
        refreshAudioCacheIndex(normalizedBrokerId, key, existingIndexEntity);
        log.info(
            "TTS???????traceId={}, brokerId={}, cacheKey={}",
            TraceIdUtil.getCurrentTraceId(),
            brokerId,
            key);
        return audioCacheEntity;
    }

    /**
     * ???????
     *
     * @param brokerId ?? ID
     * @param key ???
     * @param audioCacheEntity ????
     */
    @Override
    public void putAudioCache(String brokerId, String key, TtsAudioCacheEntity audioCacheEntity) {
        String normalizedBrokerId = normalizeBrokerId(brokerId);
        long currentTimestamp = System.currentTimeMillis();

        Map<String, TtsAudioCacheIndexEntity> indexMap = readAudioCacheIndexMap(normalizedBrokerId);
        removeExpiredAudioCacheEntries(normalizedBrokerId, indexMap, currentTimestamp);
        removeDanglingAudioCacheEntries(normalizedBrokerId, indexMap);

        TtsAudioCacheIndexEntity existingIndexEntity = indexMap.get(key);
        trimAudioCacheCountBeforeInsertIfNecessary(normalizedBrokerId, key, indexMap, existingIndexEntity);

        audioCacheRedisOperator.setValue(
            REDIS_OPERATION_AUDIO_CACHE_PUT,
            key,
            JsonUtil.toJson(audioCacheEntity),
            buildAudioCacheTtlDuration(),
            "?????????brokerId={}, key={}",
            brokerId,
            key);
        writeAudioCacheIndex(normalizedBrokerId, buildAudioCacheIndexEntity(key, existingIndexEntity, currentTimestamp));
    }

    /**
     * ???? URL ???
     *
     * @param brokerId ?? ID
     * @param key ???
     * @return ?? URL
     */
    @Override
    public String getAudioUrlCache(String brokerId, String key) {
        return redisOperator.getValue(
            REDIS_OPERATION_AUDIO_URL_CACHE_GET,
            key,
            null,
            "???? URL ?????brokerId={}, key={}",
            brokerId,
            key);
    }

    /**
     * ???? URL ???
     *
     * @param brokerId ?? ID
     * @param key ???
     * @param audioUrl ?? URL
     */
    @Override
    public void putAudioUrlCache(String brokerId, String key, String audioUrl) {
        if (!StringUtils.hasText(audioUrl)) {
            return;
        }

        redisOperator.setValue(
            REDIS_OPERATION_AUDIO_URL_CACHE_PUT,
            key,
            audioUrl,
            buildAudioCacheTtlDuration(),
            "???? URL ?????brokerId={}, key={}",
            brokerId,
            key);
    }

    /**
     * ??????? traceId????????? userId ???????? traceId?
     *
     * @param userId ?? ID
     * @return traceId
     */
    @Override
    public String getOrCreateTraceId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return TraceIdUtil.getOrCreateTraceId(null);
        }

        String cacheKey = String.join(StringPool.COLON, TRACE_CACHE_KEY_PREFIX, userId);
        String cachedTraceId = redisOperator.getValue(
            REDIS_OPERATION_TRACE_ID,
            cacheKey,
            null,
            "?? traceId ?????userId={}, cacheKey={}",
            userId,
            cacheKey);
        if (StringUtils.hasText(cachedTraceId)) {
            log.info("traceId?????userId={}, cacheKey={}, traceId={}", userId, cacheKey, cachedTraceId);
            return cachedTraceId;
        }

        String newTraceId = TraceIdUtil.getOrCreateTraceId(null);
        Boolean created = redisOperator.setIfAbsent(
            REDIS_OPERATION_TRACE_ID,
            cacheKey,
            newTraceId,
            Duration.ofMinutes(TRACE_CACHE_TTL_MINUTES),
            Boolean.FALSE,
            "?? traceId ?????userId={}, cacheKey={}",
            userId,
            cacheKey);
        if (Boolean.TRUE.equals(created)) {
            return newTraceId;
        }

        String existingTraceId = redisOperator.getValue(
            REDIS_OPERATION_TRACE_ID,
            cacheKey,
            null,
            "?? traceId ?????userId={}, cacheKey={}",
            userId,
            cacheKey);
        if (StringUtils.hasText(existingTraceId)) {
            log.info("traceId?????userId={}, cacheKey={}, traceId={}", userId, cacheKey, existingTraceId);
            return existingTraceId;
        }
        return newTraceId;
    }

    /**
     * ????????
     *
     * @param keyList ???
     * @return ??????
     */
    @Override
    public long deleteKeys(List<String> keyList) {
        return redisOperator.deleteKeys(
            REDIS_OPERATION_CACHE_DELETE,
            keyList,
            EMPTY_DELETE_COUNT,
            "?????????keyList={}",
            keyList);
    }

    /**
     * ????????
     *
     * @param key ???
     * @return ??????
     */
    @Override
    public boolean deleteKey(String key) {
        Boolean deleted = redisOperator.deleteKey(
            REDIS_OPERATION_CACHE_DELETE,
            key,
            Boolean.FALSE,
            "???????key={}",
            key);
        return Boolean.TRUE.equals(deleted);
    }

    /**
     * ???? key ???? value?
     *
     * @param key ?? key
     * @return ?? value
     */
    @Override
    public Object getCacheValue(String key) {
        DataType dataType = redisOperator.type(
            REDIS_OPERATION_CACHE_VALUE_GET,
            key,
            null,
            "?????????key={}",
            key);
        if (DataType.HASH.equals(dataType)) {
            return redisOperator.getHashEntries(
                REDIS_OPERATION_CACHE_VALUE_GET,
                key,
                Collections.emptyMap(),
                "?? Hash ?????key={}",
                key);
        }

        return redisOperator.getValue(
            REDIS_OPERATION_CACHE_VALUE_GET,
            key,
            null,
            "??????????key={}",
            key);
    }

    private Map<String, TtsAudioCacheIndexEntity> readAudioCacheIndexMap(String normalizedBrokerId) {
        String indexKey = buildAudioCacheIndexKey(normalizedBrokerId);
        Map<String, String> indexValueMap = redisOperator.getHashEntries(
            REDIS_OPERATION_AUDIO_CACHE_INDEX_READ,
            indexKey,
            Collections.emptyMap(),
            "???????????brokerId={}, indexKey={}",
            normalizedBrokerId,
            indexKey);
        if (CollectionUtils.isEmpty(indexValueMap)) {
            return new LinkedHashMap<>();
        }

        Map<String, TtsAudioCacheIndexEntity> indexMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> indexEntry : indexValueMap.entrySet()) {
            TtsAudioCacheIndexEntity indexEntity = deserializeAudioCacheIndex(indexEntry);
            if (indexEntity != null) {
                indexMap.put(indexEntity.getCacheKey(), indexEntity);
            }
        }
        return indexMap;
    }

    private TtsAudioCacheIndexEntity readAudioCacheIndex(String normalizedBrokerId, String cacheKey) {
        String indexValue = redisOperator.getHashValue(
            REDIS_OPERATION_AUDIO_CACHE_INDEX_READ,
            buildAudioCacheIndexKey(normalizedBrokerId),
            cacheKey,
            null,
            "???????????brokerId={}, cacheKey={}",
            normalizedBrokerId,
            cacheKey);
        if (!StringUtils.hasText(indexValue)) {
            return null;
        }

        try {
            return JsonUtil.fromJson(indexValue, new TypeReference<TtsAudioCacheIndexEntity>() {
            });
        } catch (IllegalStateException exception) {
            log.warn("?????????????brokerId={}, cacheKey={}", normalizedBrokerId, cacheKey, exception);
            return null;
        }
    }

    private TtsAudioCacheIndexEntity deserializeAudioCacheIndex(Map.Entry<String, String> indexEntry) {
        try {
            TtsAudioCacheIndexEntity indexEntity = JsonUtil.fromJson(
                indexEntry.getValue(),
                new TypeReference<TtsAudioCacheIndexEntity>() {
                });
            indexEntity.setCacheKey(indexEntry.getKey());
            return indexEntity;
        } catch (IllegalStateException exception) {
            log.warn("?????????????cacheKey={}", indexEntry.getKey(), exception);
            return null;
        }
    }

    private void removeExpiredAudioCacheEntries(
        String normalizedBrokerId,
        Map<String, TtsAudioCacheIndexEntity> indexMap,
        long currentTimestamp) {
        List<String> expiredCacheKeyList = BrokerScopedCacheEvictionSupport.collectExpiredEntryIdList(
            indexMap,
            indexEntity -> isExpiredAudioCache(indexEntity, currentTimestamp),
            TtsAudioCacheIndexEntity::getCacheKey);
        if (CollectionUtils.isEmpty(expiredCacheKeyList)) {
            return;
        }

        deleteAudioCacheEntries(normalizedBrokerId, expiredCacheKeyList);
        expiredCacheKeyList.forEach(indexMap::remove);
        ttsMetricsCollector.recordAudioCacheExpiredCleanup(normalizedBrokerId);
    }

    private void removeDanglingAudioCacheEntries(String normalizedBrokerId, Map<String, TtsAudioCacheIndexEntity> indexMap) {
        Map<String, TtsAudioCacheIndexEntity> snapshotIndexMap = new LinkedHashMap<>(indexMap);
        List<String> danglingCacheKeyList = new java.util.ArrayList<>();

        for (TtsAudioCacheIndexEntity indexEntity : snapshotIndexMap.values()) {
            DataType dataType = audioCacheRedisOperator.type(
                REDIS_OPERATION_AUDIO_CACHE_GET,
                indexEntity.getCacheKey(),
                null,
                "???????????brokerId={}, cacheKey={}",
                normalizedBrokerId,
                indexEntity.getCacheKey());
            if (DataType.NONE.equals(dataType)) {
                danglingCacheKeyList.add(indexEntity.getCacheKey());
            }
        }
        if (CollectionUtils.isEmpty(danglingCacheKeyList)) {
            return;
        }

        deleteAudioCacheIndex(normalizedBrokerId, danglingCacheKeyList);
        danglingCacheKeyList.forEach(indexMap::remove);
        ttsMetricsCollector.recordAudioCacheDanglingCleanup(normalizedBrokerId);
    }

    private void trimAudioCacheCountBeforeInsertIfNecessary(
        String normalizedBrokerId,
        String incomingCacheKey,
        Map<String, TtsAudioCacheIndexEntity> indexMap,
        TtsAudioCacheIndexEntity existingIndexEntity) {
        Integer maxCountPerBroker = speechTtsProperties.getCache().getBase64MaxCountPerBroker();
        if (maxCountPerBroker == null || maxCountPerBroker <= 0) {
            return;
        }
        if (existingIndexEntity != null) {
            return;
        }
        if (indexMap.size() < maxCountPerBroker) {
            return;
        }

        TtsAudioCacheIndexEntity removedIndexEntity = BrokerScopedCacheEvictionSupport.findLeastRecentlyUsedEntry(
            indexMap.values(),
            TtsAudioCacheIndexEntity::getLastAccessedAt,
            TtsAudioCacheIndexEntity::getCacheKey);
        if (removedIndexEntity == null) {
            return;
        }
        log.info(
            "TTS??????????????????????traceId={}, brokerId={}, incomingCacheKey={}, evictedCacheKey={}, currentCount={}, maxCount={}",
            TraceIdUtil.getCurrentTraceId(),
            normalizedBrokerId,
            incomingCacheKey,
            removedIndexEntity.getCacheKey(),
            indexMap.size(),
            maxCountPerBroker);
        deleteAudioCacheEntries(normalizedBrokerId, List.of(removedIndexEntity.getCacheKey()));
        indexMap.remove(removedIndexEntity.getCacheKey());
        ttsMetricsCollector.recordAudioCacheCapacityEviction(normalizedBrokerId);
    }

    private void deleteAudioCacheEntries(String normalizedBrokerId, List<String> cacheKeyList) {
        if (CollectionUtils.isEmpty(cacheKeyList)) {
            return;
        }

        audioCacheRedisOperator.deleteKeys(
            REDIS_OPERATION_CACHE_DELETE,
            cacheKeyList,
            EMPTY_DELETE_COUNT,
            "???????????brokerId={}, cacheKeyList={}",
            normalizedBrokerId,
            cacheKeyList);
        List<String> audioUrlCacheKeyList = cacheKeyList.stream()
            .map(this::buildAudioUrlCacheKey)
            .filter(StringUtils::hasText)
            .toList();
        if (!CollectionUtils.isEmpty(audioUrlCacheKeyList)) {
            redisOperator.deleteKeys(
                REDIS_OPERATION_CACHE_DELETE,
                audioUrlCacheKeyList,
                EMPTY_DELETE_COUNT,
                "?????? URL ?????brokerId={}, cacheKeyList={}",
                normalizedBrokerId,
                audioUrlCacheKeyList);
        }
        deleteAudioCacheIndex(normalizedBrokerId, cacheKeyList);
    }
    private void deleteAudioCacheIndex(String normalizedBrokerId, List<String> cacheKeyList) {
        if (CollectionUtils.isEmpty(cacheKeyList)) {
            return;
        }

        // 索引 field 直接使用 cacheKey，删除时与 value key 一一对应。
        redisOperator.deleteHashFields(
            REDIS_OPERATION_AUDIO_CACHE_INDEX_DELETE,
            buildAudioCacheIndexKey(normalizedBrokerId),
            cacheKeyList.toArray(),
            0L,
            "删除音频缓存索引失败，brokerId={}, cacheKeyList={}",
            normalizedBrokerId,
            cacheKeyList);
    }

    /**
     * 刷新音频缓存索引。
     *
     * @param normalizedBrokerId 归一化券商 ID
     * @param cacheKey 缓存键
     * @param existingIndexEntity 已存在索引
     */
    private void refreshAudioCacheIndex(
        String normalizedBrokerId,
        String cacheKey,
        TtsAudioCacheIndexEntity existingIndexEntity) {
        long currentTimestamp = System.currentTimeMillis();

        // 命中缓存后只刷新活跃时间和过期时间，保留原始创建时间。
        writeAudioCacheIndex(normalizedBrokerId, buildAudioCacheIndexEntity(cacheKey, existingIndexEntity, currentTimestamp));
    }

    /**
     * 写入音频缓存索引。
     *
     * @param normalizedBrokerId 归一化券商 ID
     * @param indexEntity 索引实体
     */
    private void writeAudioCacheIndex(String normalizedBrokerId, TtsAudioCacheIndexEntity indexEntity) {
        // 索引始终走小 key 通道，保持其他 Redis 操作仍为 200ms。
        redisOperator.putHashValue(
            REDIS_OPERATION_AUDIO_CACHE_INDEX_WRITE,
            buildAudioCacheIndexKey(normalizedBrokerId),
            indexEntity.getCacheKey(),
            JsonUtil.toJson(indexEntity),
            "写入音频缓存索引失败，brokerId={}, cacheKey={}",
            normalizedBrokerId,
            indexEntity.getCacheKey());
    }

    /**
     * 构建音频缓存索引实体。
     *
     * @param cacheKey 缓存键
     * @param existingIndexEntity 已存在索引
     * @param currentTimestamp 当前时间戳
     * @return 索引实体
     */
    private TtsAudioCacheIndexEntity buildAudioCacheIndexEntity(
        String cacheKey,
        TtsAudioCacheIndexEntity existingIndexEntity,
        long currentTimestamp) {
        Long createdAt = currentTimestamp;
        if (existingIndexEntity != null && existingIndexEntity.getCreatedAt() != null) {
            createdAt = existingIndexEntity.getCreatedAt();
        }

        // 新旧缓存统一刷新活跃时间和过期时间。
        return TtsAudioCacheIndexEntity.builder()
            .cacheKey(cacheKey)
            .createdAt(createdAt)
            .lastAccessedAt(currentTimestamp)
            .expireAt(buildAudioCacheExpireAt(currentTimestamp))
            .build();
    }

    /**
     * 判断音频缓存是否已过期。
     *
     * @param indexEntity 索引实体
     * @param currentTimestamp 当前时间戳
     * @return 是否过期
     */
    private static boolean isExpiredAudioCache(TtsAudioCacheIndexEntity indexEntity, long currentTimestamp) {
        if (indexEntity == null || indexEntity.getExpireAt() == null) {
            return false;
        }
        return currentTimestamp >= indexEntity.getExpireAt();
    }

    /**
     * 构建音频缓存过期时间戳。
     *
     * @param currentTimestamp 当前时间戳
     * @return 过期时间戳
     */
    private long buildAudioCacheExpireAt(long currentTimestamp) {
        Long base64TtlSeconds = speechTtsProperties.getCache().getBase64TtlSeconds();
        return currentTimestamp + Duration.ofSeconds(base64TtlSeconds).toMillis();
    }

    /**
     * 构建音频缓存 TTL。
     *
     * @return TTL
     */
    private Duration buildAudioCacheTtlDuration() {
        return Duration.ofSeconds(speechTtsProperties.getCache().getBase64TtlSeconds());
    }

    /**
     * 获取音频缓存创建时间。
     *
     * @param existingIndexEntity 已存在索引
     * @return 创建时间
     */
    private static Long resolveAudioCacheCreatedAt(TtsAudioCacheIndexEntity existingIndexEntity) {
        if (existingIndexEntity == null) {
            return null;
        }
        return existingIndexEntity.getCreatedAt();
    }

    /**
     * 构建音频缓存索引键。
     *
     * @param normalizedBrokerId 归一化券商 ID
     * @return 索引键
     */
    private String buildAudioCacheIndexKey(String normalizedBrokerId) {
        return String.join(
            StringPool.COLON,
            speechTtsProperties.getCache().getBase64IndexKeyPrefix(),
            normalizedBrokerId);
    }

    /**
     * 根据 Base64 缓存键构建音频 URL 缓存键。
     *
     * @param base64CacheKey Base64 缓存键
     * @return 音频 URL 缓存键
     */
    private String buildAudioUrlCacheKey(String base64CacheKey) {
        if (!StringUtils.hasText(base64CacheKey)) {
            return null;
        }

        String base64KeyPrefix = speechTtsProperties.getCache().getBase64KeyPrefix();
        String prefixWithSeparator = base64KeyPrefix + StringPool.COLON;
        if (!base64CacheKey.startsWith(prefixWithSeparator)) {
            return null;
        }

        return speechTtsProperties.getCache().getUrlKeyPrefix()
            + base64CacheKey.substring(base64KeyPrefix.length());
    }

    /**
     * 反序列化音频缓存。
     *
     * @param key 缓存键
     * @param cacheValue 缓存内容
     * @return 音频缓存实体
     */
    private TtsAudioCacheEntity deserializeAudioCache(String key, String cacheValue) {
        try {
            return JsonUtil.fromJson(cacheValue, new TypeReference<TtsAudioCacheEntity>() {
            });
        } catch (IllegalStateException exception) {
            log.warn("反序列化音频缓存失败，key={}", key, exception);
            return null;
        }
    }

    /**
     * 归一化 brokerId。
     *
     * @param brokerId 原始 brokerId
     * @return 归一化后的 brokerId
     */
    private static String normalizeBrokerId(String brokerId) {
        if (StringUtils.hasText(brokerId)) {
            return brokerId;
        }
        return CommonConstant.UNKNOWN;
    }
}
