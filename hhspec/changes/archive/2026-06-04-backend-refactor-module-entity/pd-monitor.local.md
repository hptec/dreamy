---
active: true
change: backend-refactor-module-entity
started_at: 2026-06-04T12:10:00+08:00
services:
  - role: backend
    id: api
    port: 8080
    cmd_type: java-gradle
    dir: .
    pid: 16061
    url: http://localhost:8080
    log: /Volumes/MAC/workspace/dreamy/logs/identity.log
    offset_file: /tmp/pd-monitor/backend-api-8080.offset
    log_unavailable: false
  - role: frontend
    id: main
    port: 5173
    cmd_type: vite
    dir: .
    pid: 26591
    url: http://localhost:5173
    log_unavailable: true
browser_monitoring: enabled
browser_out: /tmp/pd-monitor/browser.jsonl
portals: "main=http://localhost:5173"
cycle: 2
green_streak: 0
risk_score: 0
fixes:
  - cycle: 1
    problem_id: P0-1
    root_cause: "NoResourceFoundException(访问不存在路径,如健康探测 GET /)落入兜底 handleUnexpected,被记成 [INTERNAL] ERROR+全栈并返回 500"
    files_changed: ["backend/src/main/java/com/dreamy/identity/error/GlobalExceptionHandler.java"]
    fix: "新增 handleNoResource 处理器,映射 NOT_FOUND(40400/404),日志降 DEBUG"
    attempt: 1
    status: verified
  - cycle: 2
    problem_id: P0-2
    root_cause: "OperationLogMapper.streamByFilter 使用 ResultHandler 参数但缺 @ResultType，MyBatis 无法确定结果映射类型，抛 BindingException；次生：GlobalExceptionHandler 处理该异常时 response Content-Type 已锁为 text/csv，写 R 包络引发 HttpMessageNotWritableException"
    files_changed: ["backend/src/main/java/com/dreamy/identity/domain/audit/repository/OperationLogMapper.java"]
    fix: "新增 @ResultType(OperationLogEntity.class) + import，后端重启后 0 新错误"
    attempt: 1
    status: verified
params:
  max_cycles: 30
  interval: 60
  green_streak_target: 3
  no_browser: false
---

# pd:monitor 监控状态 — backend-refactor-module-entity

## 服务清单
- backend(api):8080 PID 91296 — healthy，日志 logs/identity.log
- frontend(main):5173 PID 26591 — healthy，vite 外部运行无独立日志（仅健康+浏览器监控）

## 基线
- 后端日志 offset 已初始化（只采新增）
- 浏览器 Playwright 已安装可用，门户 main 基线 0 问题

## 巡检记录
（见下方周期小结）
