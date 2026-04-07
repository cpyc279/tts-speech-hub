package com.tts.speech.deploy.common.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Md5Util 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-19 16:00:00
 */
class Md5UtilTest {

    /**
     * 验证空字符串 MD5 结果稳定。
     */
    @Test
    void testMd5ShouldReturnExpectedValueForEmptyString() {
        String md5Value = Md5Util.md5("");

        Assertions.assertEquals("d41d8cd98f00b204e9800998ecf8427e", md5Value);
    }

    /**
     * 验证普通字符串 MD5 结果稳定。
     */
    @Test
    void testMd5ShouldReturnExpectedValueForPlainText() {
        String md5Value = Md5Util.md5("abc");

        Assertions.assertEquals("900150983cd24fb0d6963f7d28e17f72", md5Value);
    }
}
