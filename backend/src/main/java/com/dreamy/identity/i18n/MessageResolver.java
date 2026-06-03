package com.dreamy.identity.i18n;

import com.dreamy.identity.error.ErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 错误码文案解析器。
 * 约束: i18n 策略（store en/es/fr by Accept-Language，缺省 en；admin 固定 zh）；
 * 数字 code 稳定锚点，未知 code 兜底 INTERNAL_ERROR 文案（CFL-09）。
 */
@Component
public class MessageResolver {

    private final MessageSource messageSource;

    public MessageResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** 解析指定 ErrorCode 在目标 locale 下的文案 */
    public String resolve(ErrorCode errorCode, Locale locale) {
        try {
            return messageSource.getMessage(errorCode.getMessageKey(), null, locale);
        } catch (NoSuchMessageException ex) {
            // 兜底：未配置文案的 code 回退 INTERNAL_ERROR
            return messageSource.getMessage(ErrorCode.INTERNAL_ERROR.getMessageKey(), null,
                    "Something went wrong, please try again", locale);
        }
    }
}
