package com.tts.speech.deploy.common.util;

import java.util.Collections;
import java.util.List;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * SSML 工具类。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
public final class SsmlUtil {

    private SsmlUtil() {
    }

    /**
     * 判断文本是否包含 SSML。
     *
     * @param text 文本内容
     * @param speakStartTag speak 开始标签
     * @param speakEndTag speak 结束标签
     * @return 是否包含 SSML
     */
    public static boolean containsSsml(String text, String speakStartTag, String speakEndTag) {
        return StringUtils.hasText(text)
            && text.contains(speakStartTag)
            && text.contains(speakEndTag);
    }

    /**
     * 判断文本是否包含标准 name 标签。
     *
     * @param text 文本内容
     * @param nameTagStartTag 标准 name 标签开始标签
     * @param nameTagEndTag name 标签结束标签
     * @return 是否包含 name 标签
     */
    public static boolean containsNameTag(String text, String nameTagStartTag, String nameTagEndTag) {
        return containsNameTag(text, nameTagStartTag, nameTagEndTag, Collections.emptyList());
    }

    /**
     * 判断文本是否包含标准或兼容的 name 标签。
     *
     * @param text 文本内容
     * @param nameTagStartTag 标准 name 标签开始标签
     * @param nameTagEndTag name 标签结束标签
     * @param compatibleNameTagStartTagList 兼容开始标签列表
     * @return 是否包含 name 标签
     */
    public static boolean containsNameTag(
        String text,
        String nameTagStartTag,
        String nameTagEndTag,
        List<String> compatibleNameTagStartTagList) {
        boolean containsNameTag = false;
        if (StringUtils.hasText(text) && text.contains(nameTagEndTag)) {
            containsNameTag = text.contains(nameTagStartTag);
            if (!containsNameTag && !CollectionUtils.isEmpty(compatibleNameTagStartTagList)) {
                containsNameTag = containsCompatibleNameTagStart(text, compatibleNameTagStartTagList);
            }
        }
        return containsNameTag;
    }

    /**
     * 判断文本是否包含兼容的 name 开始标签。
     *
     * @param text 文本内容
     * @param compatibleNameTagStartTagList 兼容开始标签列表
     * @return 是否包含兼容开始标签
     */
    private static boolean containsCompatibleNameTagStart(String text, List<String> compatibleNameTagStartTagList) {
        // 依次判断兼容列表中的开始标签，只要命中一种就视为已包含 name 标签。
        boolean containsCompatibleNameTagStart = false;
        for (String compatibleNameTagStartTag : compatibleNameTagStartTagList) {
            if (StringUtils.hasText(compatibleNameTagStartTag) && text.contains(compatibleNameTagStartTag)) {
                containsCompatibleNameTagStart = true;
                break;
            }
        }
        return containsCompatibleNameTagStart;
    }

    /**
     * 将兼容的 name 标签开始标签统一替换为标准标签。
     *
     * @param text 文本内容
     * @param standardNameTagStartTag 标准 name 标签开始标签
     * @param compatibleNameTagStartTagList 兼容开始标签列表
     * @return 标准化后的文本
     */
    public static String normalizeNameTag(
        String text,
        String standardNameTagStartTag,
        List<String> compatibleNameTagStartTagList) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        if (CollectionUtils.isEmpty(compatibleNameTagStartTagList)) {
            return text;
        }

        String normalizedText = text;
        // 只替换兼容的开始标签，标准标签保持不变，避免重复改写。
        for (String compatibleNameTagStartTag : compatibleNameTagStartTagList) {
            if (!StringUtils.hasText(compatibleNameTagStartTag)) {
                continue;
            }
            if (standardNameTagStartTag.equals(compatibleNameTagStartTag)) {
                continue;
            }
            normalizedText = normalizedText.replace(compatibleNameTagStartTag, standardNameTagStartTag);
        }
        return normalizedText;
    }

    /**
     * 去除文本中的 speak 和标准 name 标签，仅保留纯文本内容。
     *
     * @param text 文本内容
     * @param speakStartTag speak 开始标签
     * @param speakEndTag speak 结束标签
     * @param nameTagStartTag 标准 name 标签开始标签
     * @param nameTagEndTag name 标签结束标签
     * @return 去除 SSML 标签后的纯文本
     */
    public static String removeNameTag(
        String text,
        String speakStartTag,
        String speakEndTag,
        String nameTagStartTag,
        String nameTagEndTag) {
        return removeNameTag(
            text,
            speakStartTag,
            speakEndTag,
            nameTagStartTag,
            nameTagEndTag,
            Collections.emptyList());
    }

    /**
     * 去除文本中的 speak 和标准化后的 name 标签，仅保留纯文本内容。
     *
     * @param text 文本内容
     * @param speakStartTag speak 开始标签
     * @param speakEndTag speak 结束标签
     * @param nameTagStartTag 标准 name 标签开始标签
     * @param nameTagEndTag name 标签结束标签
     * @param compatibleNameTagStartTagList 兼容开始标签列表
     * @return 去除 SSML 标签后的纯文本
     */
    public static String removeNameTag(
        String text,
        String speakStartTag,
        String speakEndTag,
        String nameTagStartTag,
        String nameTagEndTag,
        List<String> compatibleNameTagStartTagList) {
        if (!StringUtils.hasText(text)) {
            return text;
        }

        String normalizedText = normalizeNameTag(text, nameTagStartTag, compatibleNameTagStartTagList);
        // 依次移除 speak 和标准化后的 name 标签，只保留标签内部的普通文本内容。
        return normalizedText.replace(speakStartTag, "")
            .replace(speakEndTag, "")
            .replace(nameTagStartTag, "")
            .replace(nameTagEndTag, "");
    }
}
