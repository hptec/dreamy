package com.dreamy.aspect;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogAdminWriteTransactionOrderTest {

    @Test
    void commitsBeforeReleasingCatalogLock() {
        try (AnnotationConfigApplicationContext context = context()) {
            LockedTransactionalService service = context.getBean(LockedTransactionalService.class);
            EventLog events = context.getBean(EventLog.class);

            service.write(false);

            assertThat(events.values()).containsExactly("lock", "tx-begin", "write", "tx-commit", "unlock");
        }
    }

    @Test
    void rollsBackBeforeReleasingCatalogLock() {
        try (AnnotationConfigApplicationContext context = context()) {
            LockedTransactionalService service = context.getBean(LockedTransactionalService.class);
            EventLog events = context.getBean(EventLog.class);

            assertThatThrownBy(() -> service.write(true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("failed");

            assertThat(events.values()).containsExactly("lock", "tx-begin", "write", "tx-rollback", "unlock");
        }
    }

    private static AnnotationConfigApplicationContext context() {
        return new AnnotationConfigApplicationContext(TestConfig.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        EventLog eventLog() {
            return new EventLog();
        }

        @Bean
        RLock catalogLock(EventLog events) throws InterruptedException {
            RLock lock = mock(RLock.class);
            doAnswer(invocation -> {
                events.add("lock");
                return true;
            }).when(lock).tryLock(CatalogAdminWriteAspect.LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            doAnswer(invocation -> {
                events.add("unlock");
                return null;
            }).when(lock).unlock();
            return lock;
        }

        @Bean
        RedissonClient redissonClient(RLock catalogLock) {
            RedissonClient client = mock(RedissonClient.class);
            when(client.getLock(CatalogAdminWriteAspect.LOCK_KEY)).thenReturn(catalogLock);
            return client;
        }

        @Bean
        CatalogAdminWriteAspect catalogAdminWriteAspect(RedissonClient redissonClient) {
            return new CatalogAdminWriteAspect(redissonClient);
        }

        @Bean
        PlatformTransactionManager transactionManager(EventLog events) {
            return new RecordingTransactionManager(events);
        }

        @Bean
        LockedTransactionalService lockedTransactionalService(EventLog events) {
            return new LockedTransactionalService(events);
        }
    }

    static class LockedTransactionalService {

        private final EventLog events;

        LockedTransactionalService(EventLog events) {
            this.events = events;
        }

        @CatalogAdminWrite
        @Transactional
        public void write(boolean fail) {
            events.add("write");
            if (fail) {
                throw new IllegalStateException("failed");
            }
        }
    }

    static class RecordingTransactionManager implements PlatformTransactionManager {

        private final EventLog events;

        RecordingTransactionManager(EventLog events) {
            this.events = events;
        }

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            events.add("tx-begin");
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            events.add("tx-commit");
        }

        @Override
        public void rollback(TransactionStatus status) {
            events.add("tx-rollback");
        }
    }

    static class EventLog {

        private final List<String> values = new ArrayList<>();

        void add(String value) {
            values.add(value);
        }

        List<String> values() {
            return List.copyOf(values);
        }
    }
}
