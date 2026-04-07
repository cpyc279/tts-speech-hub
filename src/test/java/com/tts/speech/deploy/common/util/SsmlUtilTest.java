package com.tts.speech.deploy.common.util;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * SsmlUtil 单元测试。
 *
 * @author yangchen5
 * @since 2026-04-03 00:00:00
 */
class SsmlUtilTest {

    /**
     * 标准 name 标签开始标签。
     */
    private static final String STANDARD_NAME_TAG_START = "<say-as interpret-as=\"name\">";

    /**
     * name 标签结束标签。
     */
    private static final String NAME_TAG_END = "</say-as>";

    /**
     * speak 开始标签。
     */
    private static final String SPEAK_START = "<speak>";

    /**
     * speak 结束标签。
     */
    private static final String SPEAK_END = "</speak>";

    /**
     * 兼容 name 标签开始标签列表。
     */
    private static final List<String> COMPATIBLE_NAME_TAG_START_LIST = List.of(
        "<say-as interpret-as='name'>",
        "<say-as interpret-as=’name‘>",
        "<say-as interpret-as=‘name’>",
        "<say-as interpret-as=name>");

    @Test
    void testContainsNameTagShouldRecognizeCompatibleStartTag() {
        boolean containsNameTag = SsmlUtil.containsNameTag(
            "<speak>娆㈣繋<say-as interpret-as=name>寮犱笁</say-as>鍔炵悊涓氬姟</speak>",
            STANDARD_NAME_TAG_START,
            NAME_TAG_END,
            COMPATIBLE_NAME_TAG_START_LIST);

        Assertions.assertTrue(containsNameTag);
    }

    @Test
    void testContainsNameTagShouldReturnFalseWhenNoSupportedStartTagExists() {
        boolean containsNameTag = SsmlUtil.containsNameTag(
            "<speak>欢迎张三</say-as>办理业务</speak>",
            STANDARD_NAME_TAG_START,
            NAME_TAG_END,
            COMPATIBLE_NAME_TAG_START_LIST);

        Assertions.assertFalse(containsNameTag);
    }

    @Test
    void testNormalizeNameTagShouldReplaceCompatibleStartTagWithStandardTag() {
        String normalizedText = SsmlUtil.normalizeNameTag(
            "<speak>娆㈣繋<say-as interpret-as='name'>寮犱笁</say-as><say-as interpret-as=’name‘>鏉庡洓</say-as></speak>",
            STANDARD_NAME_TAG_START,
            COMPATIBLE_NAME_TAG_START_LIST);

        Assertions.assertEquals(
            "<speak>娆㈣繋<say-as interpret-as=\"name\">寮犱笁</say-as><say-as interpret-as=\"name\">鏉庡洓</say-as></speak>",
            normalizedText);
    }

    @Test
    void testRemoveNameTagShouldRemoveCompatibleStartTagAfterNormalization() {
        String plainText = SsmlUtil.removeNameTag(
            "<speak>娆㈣繋<say-as interpret-as=‘name’>寮犱笁</say-as>鍔炵悊涓氬姟</speak>",
            SPEAK_START,
            SPEAK_END,
            STANDARD_NAME_TAG_START,
            NAME_TAG_END,
            COMPATIBLE_NAME_TAG_START_LIST);

        Assertions.assertEquals("娆㈣繋寮犱笁鍔炵悊涓氬姟", plainText);
    }
}
