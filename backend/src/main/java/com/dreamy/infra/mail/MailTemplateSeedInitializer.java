package com.dreamy.infra.mail;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.authconfig.entity.EmailTemplate;
import com.dreamy.domain.authconfig.repository.EmailTemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 交易/Showroom 邮件模板种子（决策 16 三语渲染 / showroom-data-detail 163「三语模板归 q.mail 分册
 * 按 type×locale 渲染」）。复用 identity email_template 表与 EmailTemplateRenderer {{var}} 渲染惯例，
 * 6 类型 × en/es/fr 简洁正文 + order-flow-complete 4 类型 × en/es/fr/zh；
 * 幂等策略与 DataInitializer 一致（按 code+locale 查 → 缺则建）。
 * 监听 ApplicationReadyEvent（晚于 huihao-mysql DDLInit 建表）。
 */
@Component
@Order(20)
public class MailTemplateSeedInitializer {

    private static final Logger log = LoggerFactory.getLogger(MailTemplateSeedInitializer.class);

    private record Seed(String code, String locale, String subject, String body) {
    }

    /** 6 类型 × 三语简洁模板（变量与发布侧 payload 字段对齐：TradingEventsPublisher / ShowroomEventPublisher） */
    private static final List<Seed> SEEDS = List.of(
            // order_confirmed（EVT-TRD-001 order.paid）
            new Seed("order_confirmed", "en", "Your Dreamy order {{order_no}} is confirmed",
                    "Thank you for your order! Order {{order_no}} ({{currency}} {{total_amount}}) has been confirmed and is now being prepared."),
            new Seed("order_confirmed", "es", "Tu pedido Dreamy {{order_no}} está confirmado",
                    "¡Gracias por tu compra! El pedido {{order_no}} ({{currency}} {{total_amount}}) ha sido confirmado y está en preparación."),
            new Seed("order_confirmed", "fr", "Votre commande Dreamy {{order_no}} est confirmée",
                    "Merci pour votre commande ! La commande {{order_no}} ({{currency}} {{total_amount}}) est confirmée et en cours de préparation."),
            // order_shipped（EVT-TRD-002 order.shipped）
            new Seed("order_shipped", "en", "Your Dreamy order {{order_no}} has shipped",
                    "Good news! Order {{order_no}} is on its way via {{carrier}}. Tracking number: {{tracking_no}}."),
            new Seed("order_shipped", "es", "Tu pedido Dreamy {{order_no}} ha sido enviado",
                    "¡Buenas noticias! El pedido {{order_no}} está en camino con {{carrier}}. Número de seguimiento: {{tracking_no}}."),
            new Seed("order_shipped", "fr", "Votre commande Dreamy {{order_no}} a été expédiée",
                    "Bonne nouvelle ! La commande {{order_no}} est en route via {{carrier}}. Numéro de suivi : {{tracking_no}}."),
            // refund_resolved（EVT-TRD-004 refund.resolved）
            new Seed("refund_resolved", "en", "Update on your refund for order {{order_no}}",
                    "Your refund request {{refund_no}} for order {{order_no}} has been resolved: {{result}}. Amount: {{currency}} {{amount}}. {{reject_reason}}"),
            new Seed("refund_resolved", "es", "Actualización de tu reembolso del pedido {{order_no}}",
                    "Tu solicitud de reembolso {{refund_no}} del pedido {{order_no}} ha sido resuelta: {{result}}. Importe: {{currency}} {{amount}}. {{reject_reason}}"),
            new Seed("refund_resolved", "fr", "Mise à jour de votre remboursement, commande {{order_no}}",
                    "Votre demande de remboursement {{refund_no}} pour la commande {{order_no}} a été traitée : {{result}}. Montant : {{currency}} {{amount}}. {{reject_reason}}"),
            // showroom_invite（EVT-SHR-001 showroom.invite）
            new Seed("showroom_invite", "en", "{{nickname}}, you're invited to \"{{showroom_name}}\"",
                    "You've been chosen for {{product_name}}! Open the showroom to see your look and RSVP: {{invite_url}}"),
            new Seed("showroom_invite", "es", "{{nickname}}, estás invitada a \"{{showroom_name}}\"",
                    "¡Has sido elegida para {{product_name}}! Abre el showroom para ver tu vestido: {{invite_url}}"),
            new Seed("showroom_invite", "fr", "{{nickname}}, vous êtes invitée à \"{{showroom_name}}\"",
                    "Vous avez été choisie pour {{product_name}} ! Ouvrez le showroom pour découvrir votre tenue : {{invite_url}}"),
            // showroom_assign（EVT-SHR-002 showroom.remind → showroom_assign，定稿映射）
            new Seed("showroom_assign", "en", "Reminder: order your dress for \"{{showroom_name}}\"",
                    "Hi {{nickname}}, a friendly reminder to order {{product_name}} for the big day. Open the showroom: {{invite_url}}"),
            new Seed("showroom_assign", "es", "Recordatorio: pide tu vestido para \"{{showroom_name}}\"",
                    "Hola {{nickname}}, te recordamos pedir {{product_name}} para el gran día. Abre el showroom: {{invite_url}}"),
            new Seed("showroom_assign", "fr", "Rappel : commandez votre robe pour \"{{showroom_name}}\"",
                    "Bonjour {{nickname}}, petit rappel pour commander {{product_name}} avant le grand jour. Ouvrez le showroom : {{invite_url}}"),
            // showroom_remind（决策 20.5 扩展枚举位，模板预置保证可补发）
            new Seed("showroom_remind", "en", "Don't forget your dress for \"{{showroom_name}}\"",
                    "Hi {{nickname}}, {{product_name}} is still waiting for you. Open the showroom: {{invite_url}}"),
            new Seed("showroom_remind", "es", "No olvides tu vestido para \"{{showroom_name}}\"",
                    "Hola {{nickname}}, {{product_name}} todavía te espera. Abre el showroom: {{invite_url}}"),
            new Seed("showroom_remind", "fr", "N'oubliez pas votre robe pour \"{{showroom_name}}\"",
                    "Bonjour {{nickname}}, {{product_name}} vous attend toujours. Ouvrez le showroom : {{invite_url}}"),
            // ===== order-flow-complete §1 C：四类新增交易邮件（en/es/fr/zh） =====
            // order_cancelled（order.cancelled；cancel_reason: timeout|customer|admin）
            new Seed("order_cancelled", "en", "Your Dreamy order {{order_no}} has been cancelled",
                    "Order {{order_no}} has been cancelled ({{cancel_reason}}). If you were charged, the amount will be refunded to your original payment method. Questions? Just reply to this email."),
            new Seed("order_cancelled", "es", "Tu pedido Dreamy {{order_no}} ha sido cancelado",
                    "El pedido {{order_no}} ha sido cancelado ({{cancel_reason}}). Si se realizó algún cargo, se reembolsará a tu método de pago original. ¿Dudas? Responde a este correo."),
            new Seed("order_cancelled", "fr", "Votre commande Dreamy {{order_no}} a été annulée",
                    "La commande {{order_no}} a été annulée ({{cancel_reason}}). Si un paiement a été effectué, il sera remboursé sur votre moyen de paiement d'origine. Des questions ? Répondez à cet e-mail."),
            new Seed("order_cancelled", "zh", "您的 Dreamy 订单 {{order_no}} 已取消",
                    "订单 {{order_no}} 已取消（{{cancel_reason}}）。如已付款，款项将原路退回。如有疑问请直接回复本邮件。"),
            // order_delivered（order.delivered）
            new Seed("order_delivered", "en", "Your Dreamy order {{order_no}} has been delivered",
                    "Order {{order_no}} has been delivered. We hope you love it! If anything isn't right, contact us within 7 days — otherwise the order will be completed automatically."),
            new Seed("order_delivered", "es", "Tu pedido Dreamy {{order_no}} ha sido entregado",
                    "El pedido {{order_no}} ha sido entregado. ¡Esperamos que te encante! Si algo no está bien, contáctanos en un plazo de 7 días; de lo contrario, el pedido se completará automáticamente."),
            new Seed("order_delivered", "fr", "Votre commande Dreamy {{order_no}} a été livrée",
                    "La commande {{order_no}} a été livrée. Nous espérons qu'elle vous plaira ! En cas de problème, contactez-nous sous 7 jours, sinon la commande sera automatiquement clôturée."),
            new Seed("order_delivered", "zh", "您的 Dreamy 订单 {{order_no}} 已签收",
                    "订单 {{order_no}} 已签收，希望您喜欢！如有任何问题请在 7 天内联系我们，否则订单将自动完成。"),
            // order_production（order.production：制作阶段进入 IN_PRODUCTION）
            new Seed("order_production", "en", "Your Dreamy order {{order_no}} is now in production",
                    "Great news — our atelier has started crafting order {{order_no}}. We'll email you again as soon as it ships."),
            new Seed("order_production", "es", "Tu pedido Dreamy {{order_no}} ya está en producción",
                    "¡Buenas noticias! Nuestro taller ha comenzado a confeccionar el pedido {{order_no}}. Te avisaremos por correo en cuanto se envíe."),
            new Seed("order_production", "fr", "Votre commande Dreamy {{order_no}} est en cours de confection",
                    "Bonne nouvelle : notre atelier a commencé la confection de la commande {{order_no}}. Nous vous préviendrons par e-mail dès son expédition."),
            new Seed("order_production", "zh", "您的 Dreamy 订单 {{order_no}} 已开始制作",
                    "好消息——工坊已开始制作订单 {{order_no}}。发货后我们会再次邮件通知您。"),
            // refund_requested（refund.requested：退款申请已受理）
            new Seed("refund_requested", "en", "We've received your refund request for order {{order_no}}",
                    "Refund request {{refund_no}} for order {{order_no}} ({{currency}} {{amount}}) has been received and is under review. We'll let you know the outcome by email."),
            new Seed("refund_requested", "es", "Hemos recibido tu solicitud de reembolso del pedido {{order_no}}",
                    "La solicitud de reembolso {{refund_no}} del pedido {{order_no}} ({{currency}} {{amount}}) ha sido recibida y está en revisión. Te informaremos del resultado por correo."),
            new Seed("refund_requested", "fr", "Nous avons bien reçu votre demande de remboursement, commande {{order_no}}",
                    "La demande de remboursement {{refund_no}} pour la commande {{order_no}} ({{currency}} {{amount}}) a été reçue et est en cours d'examen. Nous vous informerons du résultat par e-mail."),
            new Seed("refund_requested", "zh", "已收到您对订单 {{order_no}} 的退款申请",
                    "订单 {{order_no}} 的退款申请 {{refund_no}}（{{currency}} {{amount}}）已受理，正在审核中。审核结果将通过邮件通知您。"));

    private final EmailTemplateMapper templateMapper;

    public MailTemplateSeedInitializer(EmailTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        int created = 0;
        for (Seed seed : SEEDS) {
            Long count = templateMapper.selectCount(new LambdaQueryWrapper<EmailTemplate>()
                    .eq(EmailTemplate::getCode, seed.code())
                    .eq(EmailTemplate::getLocale, seed.locale()));
            if (count != null && count > 0) {
                continue;
            }
            EmailTemplate template = new EmailTemplate();
            template.setCode(seed.code());
            template.setLocale(seed.locale());
            template.setSubject(seed.subject());
            template.setBody(seed.body());
            templateMapper.insert(template);
            created++;
        }
        if (created > 0) {
            log.info("[MAIL] seeded {} transactional email templates (6 types x en/es/fr + 4 order-flow types x en/es/fr/zh)", created);
        }
    }
}
