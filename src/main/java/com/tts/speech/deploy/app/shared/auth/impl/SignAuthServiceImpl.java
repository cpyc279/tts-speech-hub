package com.tts.speech.deploy.app.shared.auth.impl;

import com.tts.speech.deploy.app.shared.auth.SignAuthService;
import com.tts.speech.deploy.common.config.SpeechAuthProperties;
import com.tts.speech.deploy.common.enums.ErrorCodeEnum;
import com.tts.speech.deploy.common.exception.BizException;
import com.tts.speech.deploy.common.exception.InvalidParameterException;
import com.tts.speech.deploy.common.util.HmacSignUtil;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsRequestEntity;
import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 客户端签名校验实现。
 *
 * @author yangchen5
 * @since 2026-03-09 21:36:00
 */
@Slf4j
@Component
public class SignAuthServiceImpl implements SignAuthService {

    private static final String EMPTY_SIGN_MESSAGE = "sign 不能为空";
    private static final String EMPTY_TIMESTAMP_MESSAGE = "timestamp 不能为空";
    private static final String INVALID_TIMESTAMP_MESSAGE = "时间戳无效";
    private static final String CLIENT_AUTH_FAILED_MESSAGE = "客户端未授权或密钥未配置";
    private static final String SIGN_INVALID_MESSAGE = "签名校验失败";
    private static final String ADMIN_AUTH_FAILED_MESSAGE = "后台缓存管理鉴权失败";
    private static final String ADMIN_TOKEN_NOT_CONFIGURED_MESSAGE = "后台缓存管理token未配置";

    @Resource
    private SpeechAuthProperties speechAuthProperties;

    /**
     * 校验 TTS 请求签名。
     *
     * @param requestEntity TTS 请求
     */
    @Override
    public void validateTtsSign(TtsRequestEntity requestEntity) {
        // 客户端签名校验开关关闭时，直接放行请求。
        if (!Boolean.TRUE.equals(speechAuthProperties.getEnabled())) {
            return;
        }
        // 统一复用同一套签名校验逻辑，避免协议字段判断分散。
        validateSign(
            requestEntity.getSign(),
            requestEntity.getTimestamp(),
            requestEntity.getBrokerId(),
            requestEntity.getUserId());
    }

    /**
     * 校验后台固定 token。
     *
     * @param adminToken 后台固定 token
     */
    @Override
    public void validateAdminAuth(String adminToken) {
        // 后台缓存管理仅使用配置中的固定 token 做校验。
        String expectedAdminToken = speechAuthProperties.getAdminToken();
        if (!StringUtils.hasText(expectedAdminToken)) {
            // 配置缺失时直接记录配置问题，便于排查环境异常。
            throw new BizException(ErrorCodeEnum.AUTH_FAILED, ADMIN_TOKEN_NOT_CONFIGURED_MESSAGE);
        }
        // 请求头中的 token 缺失或不匹配时，直接按鉴权失败处理。
        if (!expectedAdminToken.equals(adminToken)) {
            // 鉴权失败只记录固定 token 校验失败，不再输出无关的 traceId 信息。
            throw new BizException(ErrorCodeEnum.AUTH_FAILED, ADMIN_AUTH_FAILED_MESSAGE);
        }
    }

    /**
     * 执行统一签名校验。
     *
     * @param sign 客户端签名
     * @param timestamp 时间戳
     * @param brokerId 券商标识
     * @param userId 资金账户
     */
    private void validateSign(String sign, String timestamp, String brokerId, String userId) {
        // 先校验签名字段，避免后续安全比较时出现空值。
        if (!StringUtils.hasText(sign)) {
            throw new InvalidParameterException(EMPTY_SIGN_MESSAGE);
        }
        // 时间戳是签名串的固定组成部分，不能为空。
        if (!StringUtils.hasText(timestamp)) {
            throw new InvalidParameterException(EMPTY_TIMESTAMP_MESSAGE);
        }
        // 解析并校验时间窗口，拒绝过期请求。
        long requestTimestamp = parseTimestamp(timestamp);
        validateTimestamp(requestTimestamp);
        // 根据 brokerId 获取密钥，未命中时回退全局密钥。
        String secret = getSecret(brokerId);
        if (!StringUtils.hasText(secret)) {
            throw new BizException(ErrorCodeEnum.AUTH_FAILED, CLIENT_AUTH_FAILED_MESSAGE);
        }
        // 重新计算服务端签名，再用定长比较方式校验结果。
        String expectedSign = HmacSignUtil.generateSign(timestamp, brokerId, userId, secret);
        if (!MessageDigest.isEqual(
            expectedSign.getBytes(StandardCharsets.UTF_8),
            sign.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))) {
            throw new BizException(ErrorCodeEnum.SIGN_INVALID, SIGN_INVALID_MESSAGE);
        }
    }

    /**
     * 解析时间戳。
     *
     * @param timestamp 时间戳字符串
     * @return 时间戳秒值
     */
    private long parseTimestamp(String timestamp) {
        try {
            return Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            log.error("解析时间戳失败，timestamp={}", timestamp, exception);
            throw new BizException(ErrorCodeEnum.TIMESTAMP_INVALID, INVALID_TIMESTAMP_MESSAGE);
        }
    }

    /**
     * 校验时间戳窗口。
     *
     * @param requestTimestamp 请求时间戳
     */
    private void validateTimestamp(long requestTimestamp) {
        // 使用秒级时间戳做绝对值比较，兼容少量时钟偏差。
        long currentTimestamp = Instant.now().getEpochSecond();
        long toleranceSeconds = speechAuthProperties.getTimestampToleranceSeconds();
        if (Math.abs(currentTimestamp - requestTimestamp) > toleranceSeconds) {
            throw new BizException(ErrorCodeEnum.TIMESTAMP_INVALID, INVALID_TIMESTAMP_MESSAGE);
        }
    }

    /**
     * 获取密钥。
     *
     * @param brokerId 券商标识
     * @return 密钥
     */
    private String getSecret(String brokerId) {
        // brokerId 为空时直接回退全局密钥。
        if (!StringUtils.hasText(brokerId)) {
            return speechAuthProperties.getGlobalSecret();
        }
        // 优先读取券商级密钥配置。
        Map<String, String> brokerSecrets = speechAuthProperties.getBrokerSecrets();
        if (brokerSecrets != null && brokerSecrets.containsKey(brokerId)) {
            return brokerSecrets.get(brokerId);
        }
        // 未配置券商专属密钥时继续回退全局密钥。
        return speechAuthProperties.getGlobalSecret();
    }
}
