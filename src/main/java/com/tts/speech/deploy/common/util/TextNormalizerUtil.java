package com.tts.speech.deploy.common.util;

import com.tts.speech.deploy.common.constant.StringPool;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * 文本标准化工具类。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
public final class TextNormalizerUtil {

    private static final Pattern PUNCTUATION_PATTERN =
        Pattern.compile("[\\p{Punct}\\p{IsPunctuation}，。！？；：、“”‘’（）【】《》]");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Set<NormalizationAction> DEFAULT_NORMALIZATION_ACTIONS =
        Collections.unmodifiableSet(
            EnumSet.of(
                NormalizationAction.IGNORE_PUNCTUATION,
                NormalizationAction.IGNORE_WHITESPACE,
                NormalizationAction.IGNORE_CASE));

    private TextNormalizerUtil() {
    }

    /**
     * 标准化文本。
     *
     * @param text 原始文本
     * @return 标准化结果
     */
    public static String normalize(String text) {
        return normalize(text, DEFAULT_NORMALIZATION_ACTIONS);
    }

    /**
     * 按指定动作标准化文本。
     *
     * @param text 原始文本
     * @param normalizationActionSet 标准化动作集合
     * @return 标准化结果
     */
    public static String normalize(String text, Set<NormalizationAction> normalizationActionSet) {
        if (!StringUtils.hasText(text)) {
            return StringPool.EMPTY;
        }
        if (normalizationActionSet == null || normalizationActionSet.isEmpty()) {
            return text;
        }
        String normalized = text;
        if (normalizationActionSet.contains(NormalizationAction.IGNORE_PUNCTUATION)) {
            normalized = PUNCTUATION_PATTERN.matcher(normalized).replaceAll(StringPool.EMPTY);
        }
        if (normalizationActionSet.contains(NormalizationAction.IGNORE_WHITESPACE)) {
            normalized = WHITESPACE_PATTERN.matcher(normalized).replaceAll(StringPool.EMPTY);
        }
        if (normalizationActionSet.contains(NormalizationAction.IGNORE_CASE)) {
            normalized = normalized.toLowerCase(Locale.ROOT);
        }
        return normalized;
    }

    /**
     * 文本标准化动作枚举。
     */
    public enum NormalizationAction {

        /**
         * 忽略标点。
         */
        IGNORE_PUNCTUATION,

        /**
         * 忽略空白。
         */
        IGNORE_WHITESPACE,

        /**
         * 忽略大小写。
         */
        IGNORE_CASE
    }
}
