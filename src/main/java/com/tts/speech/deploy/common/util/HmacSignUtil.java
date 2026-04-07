package com.tts.speech.deploy.common.util;

import com.tts.speech.deploy.common.enums.ErrorCodeEnum;
import com.tts.speech.deploy.common.exception.BizException;
import com.tts.speech.deploy.common.constant.StringPool;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * HMAC 签名工具类。
 *
 * @author yangchen5
 * @since 2026-03-09 21:36:00
 */
@Slf4j
public final class HmacSignUtil {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    private static final String SIGN_SEPARATOR = "_";

    private HmacSignUtil() {
    }

    /**
     * 生成签名。
     *
     * @param timestamp 时间戳
     * @param brokerId 券商标识
     * @param userId 资金账户
     * @param secret 共享密钥
     * @return 签名字符串
     */
    public static String generateSign(
        String timestamp,
        String brokerId,
        String userId,
        String secret) {
        try {
            // 先初始化 HMAC 算法实例，确保服务端与客户端使用同一套摘要算法。
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            // 再把共享密钥按 UTF-8 编码写入算法实例，避免字符集差异导致签名不一致。
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM));
            // 最后对规范化后的待签名串做摘要计算，空 brokerId/userId 均按空字符串占位。
            byte[] signBytes = mac.doFinal(buildPayload(timestamp, brokerId, userId).getBytes(StandardCharsets.UTF_8));
            return bytesToHex(signBytes);
        } catch (Exception exception) {
            log.error("生成签名失败", exception);
            throw new BizException(ErrorCodeEnum.SIGN_INVALID, "签名生成失败");
        }
    }

    /**
     * 构建待签名字符串。
     *
     * @param timestamp 时间戳
     * @param brokerId 券商标识
      * @param userId 资金账户
     * @return 待签名字符串
     */
    private static String buildPayload(String timestamp, String brokerId, String userId) {
        // 先把可空字段统一规范化为空字符串，保证空值场景下的拼接规则完全固定。
        String normalizedBrokerId = normalizeField(brokerId);
        String normalizedUserId = normalizeField(userId);
        // 按 brokerId_userId_timestamp 的固定顺序拼接待签名串，避免客户端和服务端签名不一致。
        return normalizedBrokerId + SIGN_SEPARATOR + normalizedUserId + SIGN_SEPARATOR + timestamp;
    }

    /**
     * 规范化待签名字段。
     *
     * @param fieldValue 原始字段值
     * @return 规范化后的字段值
     */
    private static String normalizeField(String fieldValue) {
        // 对 null、空字符串和全空白字符串统一转为空字符串，只保留分隔位不引入额外字符。
        if (StringUtils.hasText(fieldValue)) {
            return fieldValue;
        }
        return StringPool.EMPTY;
    }

    /**
     * 转十六进制字符串。
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        // 逐字节转成两位十六进制小写字符，保持与接口文档约定一致。
        StringBuilder stringBuilder = new StringBuilder();
        for (byte singleByte : bytes) {
            stringBuilder.append(String.format("%02x", singleByte));
        }
        return stringBuilder.toString();
    }
}
