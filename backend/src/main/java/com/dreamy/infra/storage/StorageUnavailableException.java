package com.dreamy.infra.storage;

/**
 * 对象存储不可用（决策 9 降级）。域层映射：catalog 预签名 → 502 `502501`、
 * review 买家秀预签名 → 502 `502801`（error-strategy 码表，本异常不内嵌具体域码）。
 */
public class StorageUnavailableException extends RuntimeException {

    public StorageUnavailableException(String message) {
        super(message);
    }

    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
