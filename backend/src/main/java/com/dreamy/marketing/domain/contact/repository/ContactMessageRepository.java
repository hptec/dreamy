package com.dreamy.marketing.domain.contact.repository;

import com.dreamy.marketing.domain.contact.entity.ContactMessage;
import org.springframework.stereotype.Repository;

/**
 * 联系表单仓储（RM-MKT-141）。
 * L2 TRACE: marketing-data-detail §2 ContactMessageRepository / TX-MKT-028。
 */
@Repository
public class ContactMessageRepository {

    private final ContactMessageMapper mapper;

    public ContactMessageRepository(ContactMessageMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-MKT-141 insert —— 单语句 INSERT（TX-MKT-028） */
    public void insert(ContactMessage message) {
        mapper.insert(message);
    }
}
