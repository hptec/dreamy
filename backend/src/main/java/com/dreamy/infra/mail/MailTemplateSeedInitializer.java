package com.dreamy.infra.mail;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.identity.domain.authconfig.entity.EmailTemplate;
import com.dreamy.identity.domain.authconfig.repository.EmailTemplateMapper;
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
 * 6 类型 × en/es/fr 简洁正文；幂等策略与 DataInitializer 一致（按 code+locale 查 → 缺则建）。
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
                    "Bonjour {{nickname}}, {{product_name}} vous attend toujours. Ouvrez le showroom : {{invite_url}}"));

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
            log.info("[MAIL] seeded {} transactional email templates (6 types x en/es/fr)", created);
        }
    }
}
