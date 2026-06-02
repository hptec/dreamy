-- =============================================================================
-- 种子数据补全（L3）— identity-auth-fullstack
-- 补全 identity-ddl.sql 中标注「由 L3 补全」的种子：
--   - permission 菜单权限点字典（来源 portal-admin/src/router/index.js 菜单级路由，RISK-02）
--   - 超管角色 × 全部权限关联（role_permission）
--   - email_template 三语 × 4 类（RISK-04 ES/FR 标 [TRANSLATION_PENDING]）
--   - 超管账户密码哈希（BCrypt of "Admin@123456"，L3 落地可重置）
-- 幂等：使用 INSERT ... ON DUPLICATE KEY UPDATE，可重复执行。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- permission 菜单权限点字典（21 菜单级路由，来源 portal-admin 路由表）
-- -----------------------------------------------------------------------------
INSERT INTO `permission` (`key`,`group`,`label`) VALUES
  ('/',                     '工作台', '工作台'),
  ('/site/home',            '站点装修', '首页装修'),
  ('/site/navigation',      '站点装修', '导航与页脚'),
  ('/site/banners',         '站点装修', 'Banner 管理'),
  ('/products',             '商品管理', '商品列表'),
  ('/categories',           '商品管理', '品类与主题'),
  ('/orders',               '订单管理', '订单列表'),
  ('/refunds',              '订单管理', '退款工单'),
  ('/customers',            '用户管理', '用户列表'),
  ('/marketing/promotions', '营销活动', '优惠券与促销'),
  ('/marketing/email',      '营销活动', '邮件营销'),
  ('/content/blog',         '内容管理', 'Blog 文章'),
  ('/content/weddings',     '内容管理', 'Real Weddings'),
  ('/content/lookbook',     '内容管理', 'Lookbook 与指南'),
  ('/analytics',            '数据分析', '数据看板'),
  ('/publish',              '发布与系统', '发布中心'),
  ('/shipping',             '发布与系统', '物流配置'),
  ('/system/admins',        '系统管理', '管理员管理'),
  ('/system/roles',         '系统管理', '角色权限'),
  ('/system/auth',          '系统管理', '登录与认证'),
  ('/system/logs',          '系统管理', '操作日志')
ON DUPLICATE KEY UPDATE `group`=VALUES(`group`), `label`=VALUES(`label`);

-- -----------------------------------------------------------------------------
-- 超管角色 × 全部权限（显式写满；与应用层短路双保险，RISK-03）
-- -----------------------------------------------------------------------------
INSERT INTO `role_permission` (`role_id`,`permission_key`)
  SELECT '00000000-0000-0000-0000-000000000001', `key` FROM `permission`
ON DUPLICATE KEY UPDATE `permission_key`=VALUES(`permission_key`);

-- -----------------------------------------------------------------------------
-- 超管账户密码哈希（BCrypt of "Admin@123456"）
-- -----------------------------------------------------------------------------
UPDATE `admin_user`
   SET `password_hash` = '$2a$10$P18uxFq2na0XwcipZK74MuoHHcteyR18ShF1ph7Dugc6SdmaZW17.'
 WHERE `id` = '00000000-0000-0000-0000-0000000000a1';

-- -----------------------------------------------------------------------------
-- email_template 三语 × 4 类（12 条；ES/FR [TRANSLATION_PENDING] RISK-04）
-- -----------------------------------------------------------------------------
INSERT INTO `email_template` (`id`,`code`,`locale`,`subject`,`body`) VALUES
  ('00000000-0000-0000-0000-0000000000e1','otp','en','Your Dreamy verification code','Your code is {{code}}. It expires in {{ttl}} minutes.'),
  ('00000000-0000-0000-0000-0000000000e2','otp','es','Tu código de verificación de Dreamy','[TRANSLATION_PENDING] Tu código es {{code}}. Caduca en {{ttl}} minutos.'),
  ('00000000-0000-0000-0000-0000000000e3','otp','fr','Votre code de vérification Dreamy','[TRANSLATION_PENDING] Votre code est {{code}}. Il expire dans {{ttl}} minutes.'),
  ('00000000-0000-0000-0000-0000000000e4','new_device','en','New device sign-in','A new sign-in from {{device}} ({{location}}, {{ip}}). If this was not you, secure your account.'),
  ('00000000-0000-0000-0000-0000000000e5','new_device','es','Inicio de sesión desde un nuevo dispositivo','[TRANSLATION_PENDING] Nuevo inicio de sesión desde {{device}} ({{location}}, {{ip}}).'),
  ('00000000-0000-0000-0000-0000000000e6','new_device','fr','Connexion depuis un nouvel appareil','[TRANSLATION_PENDING] Nouvelle connexion depuis {{device}} ({{location}}, {{ip}}).'),
  ('00000000-0000-0000-0000-0000000000e7','change_primary','en','Primary email changed','Your primary email has been changed to {{new_email}}.'),
  ('00000000-0000-0000-0000-0000000000e8','change_primary','es','Correo principal cambiado','[TRANSLATION_PENDING] Tu correo principal se cambió a {{new_email}}.'),
  ('00000000-0000-0000-0000-0000000000e9','change_primary','fr','E-mail principal modifié','[TRANSLATION_PENDING] Votre e-mail principal a été changé en {{new_email}}.'),
  ('00000000-0000-0000-0000-0000000000ea','account_deleted','en','Account deletion confirmed','Your account has been scheduled for deletion. Data will be anonymized after 30 days.'),
  ('00000000-0000-0000-0000-0000000000eb','account_deleted','es','Eliminación de cuenta confirmada','[TRANSLATION_PENDING] Tu cuenta se eliminará. Los datos se anonimizarán tras 30 días.'),
  ('00000000-0000-0000-0000-0000000000ec','account_deleted','fr','Suppression de compte confirmée','[TRANSLATION_PENDING] Votre compte sera supprimé. Les données seront anonymisées après 30 jours.')
ON DUPLICATE KEY UPDATE `subject`=VALUES(`subject`), `body`=VALUES(`body`);
