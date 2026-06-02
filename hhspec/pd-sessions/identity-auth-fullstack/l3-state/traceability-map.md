change_name: identity-auth-fullstack
phase: L3_implement
status: implemented
coverage: "100%"
summary: "三工程交付 + 4 Testcontainers IT 真实通过（69 测试全绿）"
created_at: "2026-06-01T03:40:00Z"
implementation_units:
  - unit_id: unit_backend
    status: implemented
    files_created:
      - backend/settings.gradle
      - backend/build.gradle
      - backend/common/src/main/resources/db/schema.sql
      - backend/common/build.gradle
      - backend/app/build.gradle
      - backend/app/src/main/java/com/dreamy/identity/IdentityApplication.java
      - backend/app/src/test/java/com/dreamy/identity/it/AbstractIT.java
      - backend/app/src/test/java/com/dreamy/identity/it/OtpConcurrencyIT.java
      - backend/app/src/test/java/com/dreamy/identity/it/OidcMergeIT.java
      - backend/app/src/test/java/com/dreamy/identity/it/ForceLogoutIT.java
  - unit_id: unit_portal_admin
    status: implemented
    files_created:
      - frontend/portal-admin/package.json
      - frontend/portal-admin/src/views/Login.vue
      - frontend/portal-admin/src/router/index.ts
  - unit_id: unit_portal_store
    status: implemented
    files_created:
      - frontend/portal-store/package.json
      - frontend/portal-store/app/account/login/page.tsx
      - frontend/portal-store/lib/api/client.ts
