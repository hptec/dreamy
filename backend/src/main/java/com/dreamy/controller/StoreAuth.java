package com.dreamy.controller;

import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.security.AuthContext;
import com.dreamy.security.AuthPrincipal;

/**
 * store 控制器共用主体解析（BE-DIM-6：customer_id = store JWT subject，请求体夹带 user_id 一律忽略）。
 */
final class StoreAuth {

    private StoreAuth() {
    }

    static Long customerId() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || !AuthPrincipal.TYPE_STORE.equals(principal.type())) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return Long.parseLong(principal.subject());
    }
}
