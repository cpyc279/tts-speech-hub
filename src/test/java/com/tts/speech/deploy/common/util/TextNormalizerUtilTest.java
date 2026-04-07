package com.tts.speech.deploy.common.util;

import java.util.Collections;
import java.util.EnumSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * TextNormalizerUtil 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-19 16:00:00
 */
class TextNormalizerUtilTest {

    /**
     * 验证默认标准化会统一处理标点、空白和大小写。
     */
    @Test
    void testNormalizeShouldApplyDefaultActions() {
        String normalizedText = TextNormalizerUtil.normalize(" 张三，Hello World！ ");

        Assertions.assertEquals("张三helloworld", normalizedText);
    }

    /**
     * 验证自定义动作集合只执行指定处理。
     */
    @Test
    void testNormalizeShouldApplySpecifiedActionsOnly() {
        String normalizedText = TextNormalizerUtil.normalize(
            " 张三，Hello World！ ",
            EnumSet.of(TextNormalizerUtil.NormalizationAction.IGNORE_PUNCTUATION));

        Assertions.assertEquals(" 张三Hello World ", normalizedText);
    }

    /**
     * 验证空动作集合时返回原文。
     */
    @Test
    void testNormalizeShouldKeepOriginalTextWhenNoActionSpecified() {
        String normalizedText = TextNormalizerUtil.normalize(" 张三，Hello ", Collections.emptySet());

        Assertions.assertEquals(" 张三，Hello ", normalizedText);
    }
}
