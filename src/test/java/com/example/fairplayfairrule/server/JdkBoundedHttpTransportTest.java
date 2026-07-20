package com.example.fairplayfairrule.server;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdkBoundedHttpTransportTest {
    @Test
    void acceptsResponseAtExactLimit() {
        JdkBoundedHttpTransport.LimitedBodySubscriber subscriber =
                new JdkBoundedHttpTransport.LimitedBodySubscriber(4);
        TestSubscription subscription = new TestSubscription();
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2}), ByteBuffer.wrap(new byte[]{3, 4})));
        subscriber.onComplete();

        assertArrayEquals(new byte[]{1, 2, 3, 4}, subscriber.getBody().toCompletableFuture().join());
    }

    @Test
    void cancelsBeforeRetainingResponseBeyondLimit() {
        JdkBoundedHttpTransport.LimitedBodySubscriber subscriber =
                new JdkBoundedHttpTransport.LimitedBodySubscriber(3);
        TestSubscription subscription = new TestSubscription();
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2, 3, 4})));

        assertTrue(subscription.cancelled);
        assertThrows(CompletionException.class,
                () -> subscriber.getBody().toCompletableFuture().join());
    }

    private static final class TestSubscription implements Flow.Subscription {
        private boolean cancelled;

        @Override
        public void request(long n) {
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
