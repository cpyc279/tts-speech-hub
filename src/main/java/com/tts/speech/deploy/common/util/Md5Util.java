package com.tts.speech.deploy.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.extern.slf4j.Slf4j;

/**
 * MD5 工具类。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Slf4j
public final class Md5Util {

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
    private static final String MD5_ALGORITHM = "MD5";
    private static final String MD5_ALGORITHM_NOT_FOUND_MESSAGE = "MD5 algorithm not found";
    private static final int HEX_BIT_SHIFT = 4;
    private static final int HEX_RADIX = 2;
    private static final int BYTE_MASK = 0xFF;
    private static final int LOW_NIBBLE_MASK = 0x0F;

    private Md5Util() {
    }

    /**
     * 计算 MD5。
     *
     * @param content 原始内容
     * @return MD5 字符串
     */
    public static String md5(String content) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(MD5_ALGORITHM);
            byte[] digest = messageDigest.digest(content.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            log.error("计算MD5失败，系统未找到MD5算法", exception);
            throw new IllegalStateException(MD5_ALGORITHM_NOT_FOUND_MESSAGE, exception);
        }
    }

    /**
     * 将字节数组转换为十六进制。
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String toHex(byte[] bytes) {
        char[] chars = new char[bytes.length * HEX_RADIX];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & BYTE_MASK;
            chars[index * HEX_RADIX] = HEX_DIGITS[value >>> HEX_BIT_SHIFT];
            chars[index * HEX_RADIX + 1] = HEX_DIGITS[value & LOW_NIBBLE_MASK];
        }
        return new String(chars);
    }
}
