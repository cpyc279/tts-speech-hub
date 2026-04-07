package com.tts.speech.deploy.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON 工具类。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Slf4j
public final class JsonUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SERIALIZE_OBJECT_FAILED_MESSAGE = "serialize object failed";
    private static final String DESERIALIZE_OBJECT_FAILED_MESSAGE = "deserialize object failed";

    private JsonUtil() {
    }

    /**
     * 序列化对象。
     *
     * @param object 目标对象
     * @return JSON 字符串
     */
    public static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException exception) {
            String objectType = "null";
            if (object != null) {
                objectType = object.getClass().getName();
            }
            log.error("对象序列化失败，对象类型={}", objectType, exception);
            throw new IllegalStateException(SERIALIZE_OBJECT_FAILED_MESSAGE, exception);
        }
    }

    /**
     * 反序列化对象。
     *
     * @param json JSON 字符串
     * @param typeReference 类型引用
     * @param <T> 泛型
     * @return 反序列化对象
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException exception) {
            log.error("JSON反序列化失败，目标类型={}", typeReference.getType(), exception);
            throw new IllegalStateException(DESERIALIZE_OBJECT_FAILED_MESSAGE, exception);
        }
    }

    /**
     * 反序列化列表。
     *
     * @param json JSON 字符串
     * @param typeReference 列表类型
     * @param <T> 泛型
     * @return 列表对象
     */
    public static <T> List<T> fromJsonList(String json, TypeReference<List<T>> typeReference) {
        if (json == null) {
            return Collections.emptyList();
        }
        return fromJson(json, typeReference);
    }

    /**
     * 解析 JSON 节点。
     *
     * @param json JSON 字符串
     * @return JSON 节点
     */
    public static JsonNode readTree(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException exception) {
            log.error("JSON解析失败，payload={}", json, exception);
            throw new IllegalStateException(DESERIALIZE_OBJECT_FAILED_MESSAGE, exception);
        }
    }

    /**
     * 创建空 JSON 对象。
     *
     * @return JSON 对象
     */
    public static ObjectNode createObjectNode() {
        return OBJECT_MAPPER.createObjectNode();
    }

    /**
     * 将对象转换为 JSON 节点。
     *
     * @param object 目标对象
     * @return JSON 节点
     */
    public static JsonNode valueToTree(Object object) {
        return OBJECT_MAPPER.valueToTree(object);
    }
}
