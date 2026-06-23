review_round: 1
change_name: site-decoration-fullstack
verdict: fail
dimensions:
  consistency:
    status: pass
    issues:
      - id: CON-001
        severity: warning
        location: "acceptance.yml#s-009 vs state-machine.yml#banner_status"
        description: >
          banner_status 状态机中 published→archived 有两个转换事件（take_offline 管理端 Toggle 上下线、
          schedule_expire 定时任务），但 acceptance.yml 仅 s-009 覆盖 schedule_expire 事件，
          take_offline 事件未单独测试。FLOW-004 n7 节点明确标注 take_offline 转换，存在测试覆盖缺口。
        suggestion: >
          新增场景 source_id="banner_status.published→archived" act={op: take_offline}
          expect={state: archived, http_status: 200}，与 s-009 (schedule_expire) 并列。
      - id: CON-002
        severity: info
        location: "state-machine.yml#meta.key_decisions_referenced"
        description: >
          state-machine.yml meta.key_decisions_referenced 仅列 [KD-4, KD-11]，
          但 transitions 中实际引用了 KD-BE-2（如 home_page_section_status admin_enable trigger
          标注"需 /site/home 权限，KD-BE-2"）与 KD-1（side_effects 标注"KD-1/KD-4 保存后自动触发缓存失效"）。
          元数据列表与正文引用不一致。
        suggestion: >
          补全 key_decisions_referenced 为 [KD-1, KD-4, KD-11, KD-BE-2]，
          使元数据反映实际引用关系。
      - id: CON-003
        severity: info
        location: "er-diagram.yml#meta.key_decisions_referenced"
        description: >
          er-diagram.yml meta.key_decisions_referenced 列出 [KD-2, KD-3, KD-4, KD-6, KD-7, KD-8, KD-9, KD-10, KD-11, KD-BE-5]，
          但实体描述中亦引用 KD-1（CacheInvalidationLog modified_entities notes "KD-1 HomeBuilder/NavigationConfig 保存后自动触发缓存失效"）。
          KD-1 未列入元数据。
        suggestion: >
          将 KD-1 加入 er-diagram.yml key_decisions_referenced 列表，与 CacheInvalidationLog
          modified_entities notes 保持一致。
  acceptability:
    status: fail
    issues:
      - id: ACC-001
        severity: blocking
        location: "acceptance.yml#s-329 ~ s-338 (10 scenarios)"
        description: >
          10 个 function flow 场景（s-329 FLOW-001 至 s-338 FLOW-010）ctx 字段均为空 `ctx: {}`，
          与 completeness-report.yml 标注的 "10 scenarios have empty ctx" 一致。
          场景缺少前置上下文（如角色/权限/页面位置/初始状态），无法被测试执行器理解。
          FLOW-001~010 在 business-flow.yml 中均明确描述了 trigger 与 nodes[n1]（如 FLOW-001 n1
          "管理员进入 HomeBuilder 页面"、FLOW-008 n1 "匿名用户在首页 Newsletter 区块输入邮箱"），
          但 acceptance.yml 未将这些上下文落入 ctx 字段。
        suggestion: >
          为每个 FLOW 场景补充 ctx，至少包含 actor/permission/page/initial_state。
          示例：
          - s-329 (FLOW-001): ctx: {actor: admin, permission: "/site/home", page: HomeBuilder, initial_state: "已存在 HomePageSection 记录"}
          - s-330 (FLOW-002): ctx: {actor: admin, permission: "/site/navigation", page: NavigationConfig, tab: "main"}
          - s-331 (FLOW-003): ctx: {actor: admin, permission: "/site/announcement", page: NavigationConfig, tab: "announce"}
          - s-332 (FLOW-004): ctx: {actor: admin, permission: "/banners", page: Banners}
          - s-333 (FLOW-005): ctx: {actor: admin, permission: "/cache", page: Publish}
          - s-334 (FLOW-006): ctx: {actor: anonymous, endpoint: "GET /", locale: "en|es|fr"}
          - s-335 (FLOW-007): ctx: {actor: anonymous, endpoint: "任意页面 layout.tsx", locale: "en|es|fr"}
          - s-336 (FLOW-008): ctx: {actor: anonymous, page: "首页 Newsletter 区块", source: home_block, locale: "en|es|fr"}
          - s-337 (FLOW-009): ctx: {actor: system, trigger: "消费端读取 Hero 类型 / 管理端编辑 Hero 属性", cross_domain: true}
          - s-338 (FLOW-010): ctx: {actor: "admin|system|consumer", trigger: "管理端保存 link_type=taxonomy / 消费端派生 href", cross_domain: true}
      - id: ACC-002
        severity: blocking
        location: "acceptance.yml#s-329 ~ s-338 expect 字段"
        description: >
          10 个 function flow 场景的 expect 字段统一为
          `{http_status: 200, state: completed}`，属模糊断言。
          "state: completed" 未表达具体业务可观测结果（如 HomePageSection.sort_order 已更新、
          NewsletterSubscriber.status=subscribed、CacheInvalidationLog 新增一条记录、
          BannerService.findByPosition(HERO) 返回 i18n 文案等），
          测试无法基于此断言判定业务正确性。
        suggestion: >
          为每个 FLOW 场景补充具体业务断言，至少包含 entity/assertion/side_effect。
          示例：
          - s-329 (FLOW-001): expect: {http_status: 200, home_section_updated: true, sort_order_reflected: true, cache_invalidated: "home_section", audit_logged: true}
          - s-332 (FLOW-004): expect: {http_status: 200, banner_status: published, banner_translation_persisted: true, cdn_purged: true}
          - s-336 (FLOW-008): expect: {http_status: 200, subscriber_status: subscribed, subscribed_at_non_null: true, source: home_block, idempotent_on_duplicate: true}
          - s-337 (FLOW-009): expect: {http_status: 200, hero_banner_returned: true, i18n_fields: [title, subtitle, cta_text], fallback_on_empty: true}
      - id: ACC-003
        severity: warning
        location: "acceptance.yml#s-001 ~ s-024 (state machine scenarios)"
        description: >
          状态机场景 expect 字段虽包含 state 与 http_status，但未断言 side_effects 是否触发。
          例如 s-001 (home_page_section admin_enable) 未断言 POST /api/admin/cache/invalidate
          被调用、OperationLogs 写入。state-machine.yml 中 admin_enable/admin_disable 的
          side_effects 明确要求缓存失效，应在 acceptance 中可验证。
        suggestion: >
          为涉及 side_effects 的状态机场景补充断言。
          示例：s-001 expect 增加 `cache_invalidated: "home_section"`、
          `audit_logged: true` 字段。
      - id: ACC-004
        severity: warning
        location: "boundary-scenarios.yml#bs-167 ~ bs-180 (concurrent 类)"
        description: >
          concurrent 类边界场景的 expect 描述"系统识别重复状态变更，只执行一次副作用，另一请求失败或返回已处理"，
          但未明确具体返回哪种情况（409 失败 vs 200 已处理）。两种结果对调用方语义不同，
          需在 acceptance 中明确。
        suggestion: >
          明确并发场景的成功/失败语义。建议：
          首次请求返回 200，重复请求返回 409 CONCURRENT_CONFLICT；
          或首次 200，重复 200 但带 idempotent_replay=true 标识。
          二选一并在所有 concurrent 场景中保持一致。
  be_dim_coverage:
    status: pass
    issues:
      - id: BEDIM-001
        severity: info
        location: "business-flow.yml#FLOW-001/002/003 side_effects"
        description: >
          KD-BE-1（BE-DIM-4 事务/并发/幂等）在 FLOW-001 n5、FLOW-002 n7、FLOW-003 n5
          通过 meta: {tx_boundary: true, concurrency_control: optimistic_lock} 体现 ✅。
          但 side_effects 列表中未显式列出 `type: transaction` 条目，
          建议在 side_effects 中显式标注以便自动化检查。
        suggestion: >
          在 FLOW-001/002/003 side_effects 增加
          `{type: transaction, target: "home_sections + home_section_translations", isolation: default}` 条目。
      - id: BEDIM-002
        severity: info
        location: "business-flow.yml#FLOW-009/010 (KD-BE-5 跨 domain)"
        description: >
          KD-BE-5（跨 domain Service 接口）在 FLOW-009 与 FLOW-010 中正确标注 ✅：
          - FLOW-009 cross_domain: true，notes 引用 KD-BE-5，
            side_effects 含 cross_domain_call BannerService.findByPosition(HERO)
          - FLOW-010 cross_domain: true，notes 引用 KD-BE-5，
            side_effects 含 cross_domain_call TaxonomyService.findById/findChildren/findByType
          - FLOW-002 n5（管理端校验路径）与 FLOW-006 n7/n10/n11/n12（消费端派生路径）
            亦正确标注 cross_domain_call
          覆盖完整，无阻塞问题。
        suggestion: ""
summary:
  total_issues: 8
  blocking_count: 2
  warning_count: 3
  info_count: 3
  dimension_status:
    consistency: pass
    acceptability: fail
    be_dim_coverage: pass
  key_findings:
    - "16 个 key_decisions 全部在图文件中体现（KD-1~KD-11 + KD-BE-1~5），覆盖完整"
    - "10 个 FLOW 场景 ctx 为空且 expect 模糊（仅 http_status+state），是本轮 blocking 主因"
    - "增量模式 meta.source 标注正确：new/baseline/modified 三态分明，无错标"
    - "KD-BE-5 跨 domain 在 FLOW-009/FLOW-010 完整体现，domain 边界清晰"
    - "边界场景覆盖 8 类（含 callsite-compat 额外类），超出 7 类最低要求"
    - "banner_status.published→archived 的 take_offline 事件未单独测试（warning）"
    - "状态机场景未断言 side_effects 触发（warning）"
next_action: >
  fail → 回到 Phase 3.1 重跑：
  1. 重点修复 ACC-001/ACC-002：为 s-329~s-338 补充 ctx（actor/permission/page/initial_state）
     与具体 expect 断言（entity/assertion/side_effect）；
  2. 修复 ACC-003：为状态机场景补充 side_effects 触发断言；
  3. 修复 ACC-004：统一 concurrent 类场景的成功/失败语义；
  4. 修复 CON-001：新增 banner_status.published→archived (take_offline) 测试场景；
  5. 修复 CON-002/CON-003：补全 state-machine.yml 与 er-diagram.yml 的 key_decisions_referenced 元数据；
  6. 重跑 completeness-report.yml 验证 verdict=pass 后进入 Phase 3.7。
