package com.dreamy.infra.ga4;

/**
 * GA4 拉取失败（SVC-ANA §5.3 失败分类）。
 * timeout=true：DEADLINE_EXCEEDED/socket timeout 形态（兜底链失效时映射 504001）；
 * timeout=false：UNAVAILABLE/PERMISSION_DENIED/配额/网络等（映射 502001）。
 * 分类仅决定 DEC-ANA-5 ⑤ 兜底码，③④ 降级路径对两类一致。
 */
public class Ga4FetchException extends RuntimeException {

    private final boolean timeout;

    public Ga4FetchException(String message, boolean timeout) {
        super(message);
        this.timeout = timeout;
    }

    public Ga4FetchException(String message, boolean timeout, Throwable cause) {
        super(message, cause);
        this.timeout = timeout;
    }

    public boolean isTimeout() {
        return timeout;
    }
}
