# REQ-IDENTITY-007 统一错误码体系与国际化

> 域：identity｜横切两端｜后端 /api/store/*、/api/admin/*

## 错误码体系

- **REQ-007-01 响应结构**：所有错误返回统一结构 `{ code, message, details? }`。`code` 为**数字码**（如 40901、41001、42901），集中码表维护；`message` 为人类可读文案（按语言）；`details` 可选（字段级错误、剩余次数等）。
- **REQ-007-02 HTTP 映射**：语义化状态码——400 参数错误 / 401 未认证 / 403 无权限 / 404 不存在 / 409 冲突 / 410 失效（OTP 过期）/ 429 限流 / 500 服务端。数字码高 3 位与 HTTP 状态对应（如 409xx → HTTP 409）。
- **REQ-007-03 码表登记**：identity 域错误码集中登记在码表，至少含：邮箱格式非法、OTP 过期、OTP 锁定、重发过频、限流、消费端账户禁用、后台账户禁用、邮箱已存在、未验证邮箱冲突、角色被占用、无权限、主邮箱不可解绑、低于最小登录方式数。每个 code 附默认 message（EN/ES/FR）。

## 国际化

- **REQ-007-04 消费端语言**：消费端 UI 与错误文案、邮件支持 **EN / ES / FR** 三语。按 `Accept-Language` 或用户偏好选语，缺省 EN。ES/FR 文本可先占位，结构与 key 就位。
- **REQ-007-05 后端返回**：后端返回数字 `code` + 当前语言默认文案；前端按 `code` 映射本地化文案，未知 code 兜底通用提示。
- **REQ-007-06 后台语言**：管理后台仅中文。

## 验收场景

```gherkin
Scenario: REQ-007-01 错误响应结构统一
  When 任意认证接口返回错误
  Then 响应体含数字 code + message，必要时 details

Scenario: REQ-007-04 消费端按语言返回错误文案
  Given 请求头 Accept-Language: fr
  When OTP 过期
  Then 返回 code=41001 且 message 为法语文案

Scenario: REQ-007-02 数字码与 HTTP 语义化对应
  When 提交已存在的管理员邮箱
  Then HTTP 409 且 code 为 409xx 系列（邮箱已存在）
```
