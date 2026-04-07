package com.tts.speech.deploy.common.constant;

/**
 * 语音服务通用常量。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
public final class CommonConstant {

    /**
     * 链路追踪标识字段名。
     */
    public static final String TRACE_ID = "traceId";
    /**
     * 券商编号字段名。
     */
    public static final String BROKER_ID = "brokerId";
    /**
     * 用户编号字段名。
     */
    public static final String USER_ID = "userId";
    /**
     * 客户端编号字段名。
     */
    /**
     * 业务编码字段名。
     */
    public static final String BUSINESS_CODE = "businessCode";
    /**
     * 未知值占位符。
     */
    public static final String UNKNOWN = "unknown";
    /**
     * HTTP 协议前缀。
     */
    public static final String HTTP_PREFIX = "http://";
    /**
     * WAV 文件后缀。
     */
    public static final String WAV_SUFFIX = ".wav";
    /**
     * 日期目录格式。
     */
    public static final String DATE_PATH_PATTERN = "yyyyMMdd";
    /**
     * 客户端到服务端的流向标识。
     */
    public static final String CLIENT_TO_SERVICE = "CLIENT_TO_SERVICE";
    /**
     * 服务端到厂商侧的流向标识。
     */
    public static final String SERVICE_TO_VENDOR = "SERVICE_TO_VENDOR";
    /**
     * 连接事件类型。
     */
    public static final String EVENT_CONNECT = "CONNECT";
    /**
     * 断开事件类型。
     */
    public static final String EVENT_DISCONNECT = "DISCONNECT";
    /**
     * 错误事件类型。
     */
    public static final String EVENT_ERROR = "ERROR";
    /**
     * 接收事件类型。
     */
    public static final String EVENT_RECEIVE = "RECEIVE";
    /**
     * 发送事件类型。
     */
    public static final String EVENT_SEND = "SEND";
    /**
     * 申请令牌动作。
     */
    public static final String ACTION_APPLY_TOKEN = "apply_token";
    /**
     * 发送音频数据动作。
     */
    public static final String ACTION_SEND_DATA = "send_data";
    /**
     * 静音结尾动作。
     */
    public static final String ACTION_MUTE_END_DATA = "mute_end_data";
    /**
     * 音频结束动作。
     */
    public static final String ACTION_END_DATA = "end_data";
    /**
     * 心跳动作。
     */
    public static final String ACTION_HEART_BEAT = "heart_beat";
    /**
     * 签名请求头名称。
     */
    public static final String HEADER_SIGN = "sign";
    /**
     * 时间戳请求头名称。
     */
    public static final String HEADER_TIMESTAMP = "timestamp";
    /**
     * 管理端鉴权请求头名称。
     */
    public static final String HEADER_AUTHORIZATION = "Authorization";
    /**
     * 空 JSON 字符串。
     */
    public static final String EMPTY_JSON = "{}";
    /**
     * 人名识别结果字段键。
     */
    public static final String PEOPLE_NAME_KEY = "人名";
    /**
     * 结果列表字段键。
     */
    public static final String RESULTS_KEY = "results";
    /**
     * 文本字段键。
     */
    public static final String TEXT_KEY = "text";
    /**
     * 数据字段键。
     */
    public static final String DATA_KEY = "data";
    /**
     * 动作字段键。
     */
    public static final String ACTION_KEY = "action";
    /**
     * 文本字段名。
     */
    public static final String TEXT_FIELD = "text";
    /**
     * Base64 音频字段名。
     */
    public static final String BASE64_FIELD = "base64_data";
    /**
     * 最终文本字段名。
     */
    public static final String FINAL_TEXT_FIELD = "final_text";
    /**
     * URL 字段名。
     */
    public static final String URL_FIELD = "url";
    /**
     * 空格字符常量。
     */
    public static final String SPACE = StringPool.SPACE;
    /**
     * NER 模板缓存键前缀。
     */
    public static final String NER_TEMPLATE_KEY_PREFIX = "tts:ner:template";
    /**
     * NER 最近使用缓存键前缀。
     */
    public static final String NER_LRU_KEY_PREFIX = "tts:ner:lru";

    private CommonConstant() {
    }
}
