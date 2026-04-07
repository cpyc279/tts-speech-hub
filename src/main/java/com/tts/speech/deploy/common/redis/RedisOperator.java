package com.tts.speech.deploy.common.redis;

import com.tts.speech.deploy.common.metrics.TtsMetricsCollector;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 访问组件。
 *
 * @author yangchen5
 * @since 2026-03-20 11:20:00
 */
@Slf4j
@Component
public class RedisOperator {

    /**
     * RedisTemplate 组件。
     */
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * TTS Redis 降级指标采集器。
     */
    private final TtsMetricsCollector ttsMetricsCollector;

    /**
     * 构造 Redis 访问组件。
     *
     * @param redisTemplate RedisTemplate 组件
     * @param ttsMetricsCollector TTS Redis 降级指标采集器
     */
    public RedisOperator(
        @Qualifier("stringRedisTemplate") RedisTemplate<String, String> redisTemplate,
        TtsMetricsCollector ttsMetricsCollector) {
        // 绑定当前 RedisTemplate，统一复用既有的异常处理与监控逻辑。
        this.redisTemplate = redisTemplate;
        // 记录 Redis 降级指标时统一走同一个采集器，避免统计口径分叉。
        this.ttsMetricsCollector = ttsMetricsCollector;
    }

    /**
     * 读取字符串缓存。
     *
     * @param operation 操作名
     * @param key 缓存键
     * @param fallbackValue 降级值
     * @param errorMessage 错误日志模板
     * @param arguments 日志参数
     * @return 缓存值
     */
    public String getValue(String operation, String key, String fallbackValue, String errorMessage, Object... arguments) {
        // 字符串读取统一收口到这里，确保异常日志和指标口径一致。
        return execute(
            operation,
            () -> redisTemplate.opsForValue().get(key),
            fallbackValue,
            errorMessage,
            arguments);
    }

    /**
     * 写入字符串缓存。
     *
     * @param operation 操作名
     * @param key 缓存键
     * @param value 缓存值
     * @param ttl 过期时间
     * @param errorMessage 错误日志模板
     * @param arguments 日志参数
     * @return 是否成功
     */
    public boolean setValue(
        String operation,
        String key,
        String value,
        Duration ttl,
        String errorMessage,
        Object... arguments) {
        // 字符串写入统一在这里执行，避免业务代码重复处理 Redis 异常。
        return execute(
            operation,
            () -> {
                redisTemplate.opsForValue().set(key, value, ttl);
                return true;
            },
            false,
            errorMessage,
            arguments);
    }

    /**
     * 仅当键不存在时写入字符串缓存。
     *
     * @param operation 操作名
     * @param key 缓存键
     * @param value 缓存值
     * @param ttl 过期时间
     * @param fallbackValue 降级值
     * @param errorMessage 错误日志模板
     * @param arguments 日志参数
     * @return 是否写入成功
     */
    public Boolean setIfAbsent(
        String operation,
        String key,
        String value,
        Duration ttl,
        Boolean fallbackValue,
        String errorMessage,
        Object... arguments) {
        // setIfAbsent 也走统一异常封装，保证并发竞争与 Redis 异常场景都可控。
        return execute(
            operation,
            () -> redisTemplate.opsForValue().setIfAbsent(key, value, ttl),
            fallbackValue,
            errorMessage,
            arguments);
    }

    /**
     * 读取 Hash 全量数据。
     *
     * @param operation 操作名
     * @param key Hash 键
     * @param fallbackValue 降级值
     * @param errorMessage 错误日志模板
     * @param arguments 日志参数
     * @return Hash 数据
     */
    public Map<String, String> getHashEntries(
        String operation,
        String key,
        Map<String, String> fallbackValue,
        String errorMessage,
        Object... arguments) {
        // Hash 全量读取统一收口，避免各业务仓储重复操作 RedisTemplate。
        return execute(
            operation,
            () -> redisTemplate.opsForHash().entries(key).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    entry -> String.valueOf(entry.getKey()),
                    entry -> String.valueOf(entry.getValue()))),
            fallbackValue,
            errorMessage,
            arguments);
    }

    /**
     * 读取 Hash 单个字段。
     *
     * @param operation 操作名
     * @param key Hash 键
     * @param hashKey 字段键
     * @param fallbackValue 降级值
     * @param errorMessage 错误日志模板
     * @param arguments 日志参数
     * @return 字段值
     */
    public String getHashValue(
        String operation,
        String key,
        String hashKey,
        String fallbackValue,
        String errorMessage,
        Object... arguments) {
        // Hash 单字段读取统一收口，方便后续扩展统一监控逻辑。
        return execute(
            operation,
            () -> {
                Object value = redisTemplate.opsForHash().get(key, hashKey);
                if (value == null) {
                    return null;
                }
                return String.valueOf(value);
            },
            fallbackValue,
            errorMessage,
            arguments);
    }

    /**
     * 写入 Hash 单个字段。
     *
     * @param operation 操作名
     * @param key Hash 键
     * @param hashKey 字段键
     * @param value 字段值
     * @param errorMessage 错误日志模板
     * @param arguments 日志参数
     * @return 是否成功
     */
    public boolean putHashValue(
        String operation,
        String key,
        String hashKey,
        String value,
        String errorMessage,
        Object... arguments) {
        // Hash 写入统一收口，保证异常日志和告警指标只在这一处维护。
        return execute(
            operation,
            () -> {
                redisTemplate.opsForHash().put(key, hashKey, value);
                return true;
            },
            false,
            errorMessage,
            arguments);
    }

    /**
     * 删除 Hash 字段列表。
     *
     * @param operation 操作名
     * @param key Hash 键
     * @param hashKeys 字段键列表
     * @param fallbackValue 降级值
     * @param errorMessage 错误日志模板
     * @param arguments 日志参数
     * @return 删除结果
     */
    public Long deleteHashFields(
        String operation,
        String key,
        Object[] hashKeys,
        Long fallbackValue,
        String errorMessage,
        Object... arguments) {
        // Hash 删除统一收口，避免业务类直接感知 Redis 异常细节。
        return execute(
            operation,
            () -> redisTemplate.opsForHash().delete(key, hashKeys),
            fallbackValue,
            errorMessage,
            arguments);
    }

    /**
     * 刷新键过期时间。
     *
     * @param operation 操作名
     * @param key 缓存键
     * @param ttl 过期时间
     * @param fallbackValue 降级值
     * @param errorMessage 错误日志模板
     * @param arguments 日志参数
     * @return 是否成功
     */
    public Boolean expire(
        String operation,
        String key,
        Duration ttl,
        Boolean fallbackValue,
        String errorMessage,
        Object... arguments) {
        // 过期时间刷新统一走这里，确保异常时只降级不打断业务流程。
        return execute(
            operation,
            () -> redisTemplate.expire(key, ttl),
            fallbackValue,
            errorMessage,
            arguments);
    }

    /**
     * 批量删除键。
     *
     * @param operation 操作名
     * @param keyList 键列表
     * @param fallbackValue 降级值
     * @param errorMessage 错误日志模板
     * @param arguments 日志参数
     * @return 删除数量
     */
    public Long deleteKeys(
        String operation,
        List<String> keyList,
        Long fallbackValue,
        String errorMessage,
        Object... arguments) {
        // 批量删除统一收口，后续新增清理逻辑时无需重复处理异常。
        return execute(
            operation,
            () -> redisTemplate.delete(keyList),
            fallbackValue,
            errorMessage,
            arguments);
    }

    /**
     * 删除单个键。
     *
     * @param operation 操作名
     * @param key 缓存键
     * @param fallbackValue 降级值
     * @param errorMessage 错误日志模板
     * @param arguments 日志参数
     * @return 是否成功
     */
    public Boolean deleteKey(
        String operation,
        String key,
        Boolean fallbackValue,
        String errorMessage,
        Object... arguments) {
        // 单键删除统一收口，保持失败时的日志和指标格式一致。
        return execute(
            operation,
            () -> redisTemplate.delete(key),
            fallbackValue,
            errorMessage,
            arguments);
    }

    /**
     * 读取键类型。
     *
     * @param operation 操作名
     * @param key 缓存键
     * @param fallbackValue 降级值
     * @param errorMessage 错误日志模板
     * @param arguments 日志参数
     * @return 键类型
     */
    public DataType type(
        String operation,
        String key,
        DataType fallbackValue,
        String errorMessage,
        Object... arguments) {
        // 键类型查询统一收口，避免业务代码直接依赖 RedisTemplate。
        return execute(
            operation,
            () -> redisTemplate.type(key),
            fallbackValue,
            errorMessage,
            arguments);
    }

    /**
     * 执行 Redis 调用。
     *
     * @param operation 操作名
     * @param supplier 调用逻辑
     * @param fallbackValue 降级值
     * @param errorMessage 错误日志模板
     * @param arguments 日志参数
     * @param <T> 返回值类型
     * @return 调用结果
     */
    private <T> T execute(
        String operation,
        Supplier<T> supplier,
        T fallbackValue,
        String errorMessage,
        Object... arguments) {
        try {
            // Redis 可用时直接执行原始调用，保证行为与原实现一致。
            return supplier.get();
        } catch (RuntimeException exception) {
            // Redis 异常时统一记录错误日志并打告警指标，再返回降级值。
            log.error(errorMessage, appendException(arguments, exception));
            ttsMetricsCollector.recordRedisDegrade(operation);
            return fallbackValue;
        }
    }

    /**
     * 追加异常对象到日志参数列表。
     *
     * @param arguments 原始日志参数
     * @param exception 异常对象
     * @return 新的日志参数列表
     */
    private static Object[] appendException(Object[] arguments, RuntimeException exception) {
        Object[] extendedArguments = new Object[arguments.length + 1];
        // 先复制原始日志参数，保持占位符顺序不变。
        System.arraycopy(arguments, 0, extendedArguments, 0, arguments.length);
        // 把异常对象追加到最后，便于日志框架自动输出堆栈。
        extendedArguments[arguments.length] = exception;
        return extendedArguments;
    }
}
