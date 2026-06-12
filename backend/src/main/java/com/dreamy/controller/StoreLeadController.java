package com.dreamy.controller;

import com.dreamy.domain.contact.service.ContactService;
import com.dreamy.domain.subscriber.service.NewsletterService;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 消费端落表控制器（E-MKT-11 newsletter + E-MKT-12 contact；FLOW-P19②③）。
 * 公开 POST（白名单 `/api/store/newsletter`、`/api/store/contact`），WAF 限流（决策 11），
 * 不缓存、不发 MQ、不写 OperationLog。
 */
@RestController
public class StoreLeadController {

    private final NewsletterService newsletterService;
    private final ContactService contactService;

    public StoreLeadController(NewsletterService newsletterService, ContactService contactService) {
        this.newsletterService = newsletterService;
        this.contactService = contactService;
    }

    /** E-MKT-11 请求体（V-MKT-009~011） */
    public record NewsletterRequest(String email, Integer source, String locale) {
    }

    /** E-MKT-11 subscribeNewsletter：无论新增或重复一律 200 {subscribed:true}（不泄露存在性） */
    @PostMapping("/api/store/newsletter")
    public ResponseEntity<R<Map<String, Boolean>>> subscribe(@RequestBody NewsletterRequest req) {
        newsletterService.subscribe(req.email(), req.source(), req.locale());
        return ResponseEntity.ok(R.ok(Map.of("subscribed", true)));
    }

    /** E-MKT-12 请求体（V-MKT-012~015） */
    public record ContactRequest(String name, String email, String subject, String message) {
    }

    /** E-MKT-12 submitContactMessage：201 {submitted:true}（管理端本期不做查看页，运营直查库） */
    @PostMapping("/api/store/contact")
    public ResponseEntity<R<Map<String, Boolean>>> submitContact(@RequestBody ContactRequest req) {
        contactService.submit(req.name(), req.email(), req.subject(), req.message());
        return ResponseEntity.status(201).body(R.ok(Map.of("submitted", true)));
    }
}
