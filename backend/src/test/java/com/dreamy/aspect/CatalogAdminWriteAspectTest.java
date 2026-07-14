package com.dreamy.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogAdminWriteAspectTest {

    @Mock
    RedissonClient redissonClient;
    @Mock
    RLock lock;
    @Mock
    ProceedingJoinPoint joinPoint;

    CatalogAdminWriteAspect aspect;

    @BeforeEach
    void setUp() throws InterruptedException {
        aspect = new CatalogAdminWriteAspect(redissonClient);
        lenient().when(redissonClient.getLock(CatalogAdminWriteAspect.LOCK_KEY)).thenReturn(lock);
        lenient().when(lock.tryLock(CatalogAdminWriteAspect.LOCK_WAIT_SECONDS, TimeUnit.SECONDS)).thenReturn(true);
    }

    @Test
    void holdsGlobalLockUntilInvocationCompletes() throws Throwable {
        when(joinPoint.proceed()).thenReturn("done");

        assertThat(aspect.serialize(joinPoint)).isEqualTo("done");

        InOrder order = inOrder(redissonClient, lock, joinPoint);
        order.verify(redissonClient).getLock(CatalogAdminWriteAspect.LOCK_KEY);
        order.verify(lock).tryLock(CatalogAdminWriteAspect.LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        order.verify(joinPoint).proceed();
        order.verify(lock).unlock();
        verify(lock, never()).isHeldByCurrentThread();
    }

    @Test
    void releasesGlobalLockWhenInvocationFails() throws Throwable {
        IllegalStateException failure = new IllegalStateException("write failed");
        when(joinPoint.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> aspect.serialize(joinPoint)).isSameAs(failure);

        verify(lock).unlock();
    }

    @Test
    void runsOutsideTransactionAdviceSoUnlockHappensAfterCommitOrRollback() {
        Order order = CatalogAdminWriteAspect.class.getAnnotation(Order.class);

        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    void finiteWaitTimeoutDoesNotEnterWrite() throws Throwable {
        when(lock.tryLock(CatalogAdminWriteAspect.LOCK_WAIT_SECONDS, TimeUnit.SECONDS)).thenReturn(false);

        assertThatThrownBy(() -> aspect.serialize(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Timed out");

        verify(joinPoint, org.mockito.Mockito.never()).proceed();
        verify(lock, org.mockito.Mockito.never()).unlock();
    }

    @Test
    void unlockFailureDoesNotHideCommittedResult() throws Throwable {
        when(joinPoint.proceed()).thenReturn("committed");
        doThrow(new IllegalStateException("redis unavailable")).when(lock).unlock();

        assertThat(aspect.serialize(joinPoint)).isEqualTo("committed");
        verify(lock, never()).isHeldByCurrentThread();
    }

    @Test
    void serializesConcurrentCatalogWrites() throws Throwable {
        ReentrantLock localLock = new ReentrantLock();
        AtomicInteger lockAttempts = new AtomicInteger();
        CountDownLatch secondLockAttempt = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (lockAttempts.incrementAndGet() == 2) {
                secondLockAttempt.countDown();
            }
            localLock.lock();
            return true;
        }).when(lock).tryLock(CatalogAdminWriteAspect.LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        doAnswer(invocation -> {
            localLock.unlock();
            return null;
        }).when(lock).unlock();

        AtomicInteger activeWrites = new AtomicInteger();
        AtomicInteger maxActiveWrites = new AtomicInteger();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        ProceedingJoinPoint firstWrite = mock(ProceedingJoinPoint.class);
        ProceedingJoinPoint secondWrite = mock(ProceedingJoinPoint.class);
        when(firstWrite.proceed()).thenAnswer(invocation -> {
            enterWrite(activeWrites, maxActiveWrites);
            firstEntered.countDown();
            if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("first write was not released");
            }
            activeWrites.decrementAndGet();
            return "first";
        });
        when(secondWrite.proceed()).thenAnswer(invocation -> {
            enterWrite(activeWrites, maxActiveWrites);
            secondEntered.countDown();
            activeWrites.decrementAndGet();
            return "second";
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> invoke(firstWrite));
            assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Object> second = executor.submit(() -> invoke(secondWrite));
            assertThat(secondLockAttempt.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondEntered.await(100, TimeUnit.MILLISECONDS)).isFalse();

            releaseFirst.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("second");
            assertThat(maxActiveWrites).hasValue(1);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private Object invoke(ProceedingJoinPoint write) {
        try {
            return aspect.serialize(write);
        } catch (Throwable throwable) {
            throw new CompletionException(throwable);
        }
    }

    private static void enterWrite(AtomicInteger activeWrites, AtomicInteger maxActiveWrites) {
        int active = activeWrites.incrementAndGet();
        maxActiveWrites.accumulateAndGet(active, Math::max);
    }
}
