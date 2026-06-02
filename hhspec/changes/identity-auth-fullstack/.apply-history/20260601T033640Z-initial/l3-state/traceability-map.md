change_name: identity-auth-fullstack
phase: L3_implement
status: implemented
coverage: "100%"
summary: "三工程全部落地：backend(编译通过) + portal-admin(build通过) + portal-store(build通过)"
sub_maps:
  - traceability-map-backend.md
  - traceability-map-portal-admin.md
  - traceability-map-portal-store.md
created_at: "2026-06-01T01:15:00Z"

# l3-gate.sh 文件存在性校验用：每个单元列代表性已落盘文件
implementation_units:
  - unit_id: unit_backend
    status: implemented
    files_created:
      - backend/settings.gradle
      - backend/build.gradle
      - backend/gradlew
      - backend/common/src/main/resources/db/schema.sql
      - backend/common/src/main/resources/db/seed-supplement.sql
      - backend/common/src/main/resources/i18n/messages_en.properties
      - backend/common/src/main/resources/i18n/messages_zh.properties
      - backend/app/src/main/java/com/dreamy/identity/IdentityApplication.java
  - unit_id: unit_portal_admin
    status: implemented
    files_created:
      - frontend/portal-admin/package.json
      - frontend/portal-admin/vite.config.ts
      - frontend/portal-admin/src/views/Login.vue
      - frontend/portal-admin/src/views/AdminList.vue
      - frontend/portal-admin/src/views/RoleManagement.vue
      - frontend/portal-admin/src/views/AuthSettings.vue
      - frontend/portal-admin/src/views/OperationLogs.vue
      - frontend/portal-admin/src/views/CustomerDetail.vue
      - frontend/portal-admin/src/router/index.ts
      - frontend/portal-admin/src/api/client.ts
  - unit_id: unit_portal_store
    status: implemented
    files_created:
      - frontend/portal-store/package.json
      - frontend/portal-store/next.config.mjs
      - frontend/portal-store/app/account/login/page.tsx
      - frontend/portal-store/app/account/page.tsx
      - frontend/portal-store/app/account/settings/page.tsx
      - frontend/portal-store/app/account/security/page.tsx
      - frontend/portal-store/lib/api/client.ts
