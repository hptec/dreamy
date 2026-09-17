//! 邮件模板种子(email_template,主库;启动幂等:按 code+locale 存在即跳过,不覆盖存量)。
//! 种子集 = 身份域 4 code(otp/new_device/change_primary/account_deleted,本域发信用)
//! + 业务域 10 code(翻译自 Java MailTemplateSeedInitializer,Java 经 gRPC TemplateGate 取用)。

use sea_orm::{ConnectionTrait, DatabaseConnection, Statement};

type Seed = (&'static str, &'static str, &'static str, &'static str);

const SEEDS: &[Seed] = &[
    // ===== 身份域 4 code × en/es/fr(Rust mail 发信;vars 对齐各调用点) =====
    // otp:vars = code, ttl(otp.rs send_otp)
    ("otp", "en", "Your Dreamy verification code",
        "Your verification code is {{code}}. It expires in {{ttl}} minutes. If you didn't request this, please ignore this email."),
    ("otp", "es", "Tu código de verificación de Dreamy",
        "Tu código de verificación es {{code}}. Caduca en {{ttl}} minutos. Si no lo solicitaste, ignora este correo."),
    ("otp", "fr", "Votre code de vérification Dreamy",
        "Votre code de vérification est {{code}}. Il expire dans {{ttl}} minutes. Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail."),
    // new_device:vars = device, ip, location(auth.rs 登录新设备通知)
    ("new_device", "en", "New sign-in to your Dreamy account",
        "A new sign-in to your account was detected: {{device}} from {{ip}} ({{location}}). If this wasn't you, please secure your account immediately."),
    ("new_device", "es", "Nuevo inicio de sesión en tu cuenta Dreamy",
        "Hemos detectado un nuevo inicio de sesión: {{device}} desde {{ip}} ({{location}}). Si no fuiste tú, protege tu cuenta de inmediato."),
    ("new_device", "fr", "Nouvelle connexion à votre compte Dreamy",
        "Une nouvelle connexion a été détectée : {{device}} depuis {{ip}} ({{location}}). Si ce n'était pas vous, sécurisez votre compte immédiatement."),
    // change_primary:vars = new_email(account.rs 换主邮箱通知)
    ("change_primary", "en", "Your Dreamy email has been changed",
        "The primary email on your Dreamy account has been changed to {{new_email}}. If this wasn't you, contact support immediately."),
    ("change_primary", "es", "Tu correo de Dreamy ha sido cambiado",
        "El correo principal de tu cuenta Dreamy ahora es {{new_email}}. Si no fuiste tú, contacta con soporte de inmediato."),
    ("change_primary", "fr", "Votre e-mail Dreamy a été modifié",
        "L'e-mail principal de votre compte Dreamy est désormais {{new_email}}. Si ce n'était pas vous, contactez le support immédiatement."),
    // account_deleted:无 vars(account.rs 删号通知)
    ("account_deleted", "en", "Your Dreamy account has been deleted",
        "Your Dreamy account has been deleted as requested. We're sorry to see you go. This email serves as final confirmation."),
    ("account_deleted", "es", "Tu cuenta Dreamy ha sido eliminada",
        "Tu cuenta Dreamy ha sido eliminada según lo solicitado. Lamentamos verte partir. Este correo es la confirmación final."),
    ("account_deleted", "fr", "Votre compte Dreamy a été supprimé",
        "Votre compte Dreamy a été supprimé à votre demande. Nous sommes désolés de vous voir partir. Cet e-mail constitue la confirmation finale."),
    // ===== 业务域 10 code(Java q.mail 消费经 gRPC 取用;翻译自 MailTemplateSeedInitializer) =====
    // order_confirmed(EVT-TRD-001 order.paid)
    ("order_confirmed", "en", "Your Dreamy order {{order_no}} is confirmed",
        "Thank you for your order! Order {{order_no}} ({{currency}} {{total_amount}}) has been confirmed and is now being prepared."),
    ("order_confirmed", "es", "Tu pedido Dreamy {{order_no}} está confirmado",
        "¡Gracias por tu compra! El pedido {{order_no}} ({{currency}} {{total_amount}}) ha sido confirmado y está en preparación."),
    ("order_confirmed", "fr", "Votre commande Dreamy {{order_no}} est confirmée",
        "Merci pour votre commande ! La commande {{order_no}} ({{currency}} {{total_amount}}) est confirmée et en cours de préparation."),
    // order_shipped(EVT-TRD-002 order.shipped)
    ("order_shipped", "en", "Your Dreamy order {{order_no}} has shipped",
        "Good news! Order {{order_no}} is on its way via {{carrier}}. Tracking number: {{tracking_no}}."),
    ("order_shipped", "es", "Tu pedido Dreamy {{order_no}} ha sido enviado",
        "¡Buenas noticias! El pedido {{order_no}} está en camino con {{carrier}}. Número de seguimiento: {{tracking_no}}."),
    ("order_shipped", "fr", "Votre commande Dreamy {{order_no}} a été expédiée",
        "Bonne nouvelle ! La commande {{order_no}} est en route via {{carrier}}. Numéro de suivi : {{tracking_no}}."),
    // refund_resolved(EVT-TRD-004 refund.resolved)
    ("refund_resolved", "en", "Update on your refund for order {{order_no}}",
        "Your refund request {{refund_no}} for order {{order_no}} has been resolved: {{result}}. Amount: {{currency}} {{amount}}. {{reject_reason}}"),
    ("refund_resolved", "es", "Actualización de tu reembolso del pedido {{order_no}}",
        "Tu solicitud de reembolso {{refund_no}} del pedido {{order_no}} ha sido resuelta: {{result}}. Importe: {{currency}} {{amount}}. {{reject_reason}}"),
    ("refund_resolved", "fr", "Mise à jour de votre remboursement, commande {{order_no}}",
        "Votre demande de remboursement {{refund_no}} pour la commande {{order_no}} a été traitée : {{result}}. Montant : {{currency}} {{amount}}. {{reject_reason}}"),
    // showroom_invite(EVT-SHR-001 showroom.invite)
    ("showroom_invite", "en", "{{nickname}}, you're invited to \"{{showroom_name}}\"",
        "You've been chosen for {{product_name}}! Open the showroom to see your look and RSVP: {{invite_url}}"),
    ("showroom_invite", "es", "{{nickname}}, estás invitada a \"{{showroom_name}}\"",
        "¡Has sido elegida para {{product_name}}! Abre el showroom para ver tu vestido: {{invite_url}}"),
    ("showroom_invite", "fr", "{{nickname}}, vous êtes invitée à \"{{showroom_name}}\"",
        "Vous avez été choisie pour {{product_name}} ! Ouvrez le showroom pour découvrir votre tenue : {{invite_url}}"),
    // showroom_assign(EVT-SHR-002 定稿映射)
    ("showroom_assign", "en", "Reminder: order your dress for \"{{showroom_name}}\"",
        "Hi {{nickname}}, a friendly reminder to order {{product_name}} for the big day. Open the showroom: {{invite_url}}"),
    ("showroom_assign", "es", "Recordatorio: pide tu vestido para \"{{showroom_name}}\"",
        "Hola {{nickname}}, te recordamos pedir {{product_name}} para el gran día. Abre el showroom: {{invite_url}}"),
    ("showroom_assign", "fr", "Rappel : commandez votre robe pour \"{{showroom_name}}\"",
        "Bonjour {{nickname}}, petit rappel pour commander {{product_name}} avant le grand jour. Ouvrez le showroom : {{invite_url}}"),
    // showroom_remind(决策 20.5 扩展枚举位)
    ("showroom_remind", "en", "Don't forget your dress for \"{{showroom_name}}\"",
        "Hi {{nickname}}, {{product_name}} is still waiting for you. Open the showroom: {{invite_url}}"),
    ("showroom_remind", "es", "No olvides tu vestido para \"{{showroom_name}}\"",
        "Hola {{nickname}}, {{product_name}} todavía te espera. Abre el showroom: {{invite_url}}"),
    ("showroom_remind", "fr", "N'oubliez pas votre robe pour \"{{showroom_name}}\"",
        "Bonjour {{nickname}}, {{product_name}} vous attend toujours. Ouvrez le showroom : {{invite_url}}"),
    // order_cancelled(order.cancelled;cancel_reason: timeout|customer|admin)
    ("order_cancelled", "en", "Your Dreamy order {{order_no}} has been cancelled",
        "Order {{order_no}} has been cancelled ({{cancel_reason}}). If you were charged, the amount will be refunded to your original payment method. Questions? Just reply to this email."),
    ("order_cancelled", "es", "Tu pedido Dreamy {{order_no}} ha sido cancelado",
        "El pedido {{order_no}} ha sido cancelado ({{cancel_reason}}). Si se realizó algún cargo, se reembolsará a tu método de pago original. ¿Dudas? Responde a este correo."),
    ("order_cancelled", "fr", "Votre commande Dreamy {{order_no}} a été annulée",
        "La commande {{order_no}} a été annulée ({{cancel_reason}}). Si un paiement a été effectué, il sera remboursé sur votre moyen de paiement d'origine. Des questions ? Répondez à cet e-mail."),
    ("order_cancelled", "zh", "您的 Dreamy 订单 {{order_no}} 已取消",
        "订单 {{order_no}} 已取消（{{cancel_reason}}）。如已付款，款项将原路退回。如有疑问请直接回复本邮件。"),
    // order_delivered(order.delivered)
    ("order_delivered", "en", "Your Dreamy order {{order_no}} has been delivered",
        "Order {{order_no}} has been delivered. We hope you love it! If anything isn't right, contact us within 7 days — otherwise the order will be completed automatically."),
    ("order_delivered", "es", "Tu pedido Dreamy {{order_no}} ha sido entregado",
        "El pedido {{order_no}} ha sido entregado. ¡Esperamos que te encante! Si algo no está bien, contáctanos en un plazo de 7 días; de lo contrario, el pedido se completará automáticamente."),
    ("order_delivered", "fr", "Votre commande Dreamy {{order_no}} a été livrée",
        "La commande {{order_no}} a été livrée. Nous espérons qu'elle vous plaira ! En cas de problème, contactez-nous sous 7 jours, sinon la commande sera automatiquement clôturée."),
    ("order_delivered", "zh", "您的 Dreamy 订单 {{order_no}} 已签收",
        "订单 {{order_no}} 已签收，希望您喜欢！如有任何问题请在 7 天内联系我们，否则订单将自动完成。"),
    // order_production(制作阶段 IN_PRODUCTION)
    ("order_production", "en", "Your Dreamy order {{order_no}} is now in production",
        "Great news — our atelier has started crafting order {{order_no}}. We'll email you again as soon as it ships."),
    ("order_production", "es", "Tu pedido Dreamy {{order_no}} ya está en producción",
        "¡Buenas noticias! Nuestro taller ha comenzado a confeccionar el pedido {{order_no}}. Te avisaremos por correo en cuanto se envíe."),
    ("order_production", "fr", "Votre commande Dreamy {{order_no}} est en cours de confection",
        "Bonne nouvelle : notre atelier a commencé la confection de la commande {{order_no}}. Nous vous préviendrons par e-mail dès son expédition."),
    ("order_production", "zh", "您的 Dreamy 订单 {{order_no}} 已开始制作",
        "好消息——工坊已开始制作订单 {{order_no}}。发货后我们会再次邮件通知您。"),
    // refund_requested(refund.requested)
    ("refund_requested", "en", "We've received your refund request for order {{order_no}}",
        "Refund request {{refund_no}} for order {{order_no}} ({{currency}} {{amount}}) has been received and is under review. We'll let you know the outcome by email."),
    ("refund_requested", "es", "Hemos recibido tu solicitud de reembolso del pedido {{order_no}}",
        "La solicitud de reembolso {{refund_no}} del pedido {{order_no}} ({{currency}} {{amount}}) ha sido recibida y está en revisión. Te informaremos del resultado por correo."),
    ("refund_requested", "fr", "Nous avons bien reçu votre demande de remboursement, commande {{order_no}}",
        "La demande de remboursement {{refund_no}} pour la commande {{order_no}} ({{currency}} {{amount}}) a été reçue et est en cours d'examen. Nous vous informerons du résultat par e-mail."),
    ("refund_requested", "zh", "已收到您对订单 {{order_no}} 的退款申请",
        "订单 {{order_no}} 的退款申请 {{refund_no}}（{{currency}} {{amount}}）已受理，正在审核中。审核结果将通过邮件通知您。"),
];

/// 启动期播种(幂等:code+locale 存在即跳过;失败仅 WARN 不阻断启动)
pub async fn seed_mail_templates(db: &DatabaseConnection) {
    let mut created = 0usize;
    for (code, locale, subject, body) in SEEDS {
        let exists = db
            .query_one(Statement::from_sql_and_values(
                sea_orm::DatabaseBackend::MySql,
                r#"SELECT 1 FROM email_template WHERE code = ? AND locale = ? LIMIT 1"#,
                [(*code).into(), (*locale).into()],
            ))
            .await;
        if matches!(exists, Ok(Some(_))) {
            continue;
        }
        let result = db
            .execute(Statement::from_sql_and_values(
                sea_orm::DatabaseBackend::MySql,
                r#"INSERT INTO email_template (code, locale, subject, body) VALUES (?, ?, ?, ?)"#,
                [(*code).into(), (*locale).into(), (*subject).into(), (*body).into()],
            ))
            .await;
        match result {
            Ok(_) => created += 1,
            Err(e) => {
                tracing::warn!("[seed] 邮件模板播种失败 code={code} locale={locale}:{e}");
            }
        }
    }
    if created > 0 {
        tracing::info!("[seed] 邮件模板播种完成:新增 {created} 条(身份 4 code + 业务 10 code)");
    }
}
