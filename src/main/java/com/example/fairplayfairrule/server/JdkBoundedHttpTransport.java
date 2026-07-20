package com.example.fairplayfairrule.server;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** JDK HTTP transport that cancels response consumption at a caller-specified byte cap. */
public final class JdkBoundedHttpTransport implements BoundedHttpTransport {
    private static final int MAXIMUM_CALLER_LIMIT = 1024 * 1024;

    private final HttpClient client;

    public JdkBoundedHttpTransport(HttpClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public CompletableFuture<HttpResult> send(HttpRequest request, int maximumResponseBytes) {
        Objects.requireNonNull(request, "request");
        if (maximumResponseBytes <= 0 || maximumResponseBytes > MAXIMUM_CALLER_LIMIT) {
            throw new IllegalArgumentException("Invalid HTTP response limit");
        }
        return client.sendAsync(request,
                        responseInfo -> new LimitedBodySubscriber(maximumResponseBytes))
                .thenApply(response -> new HttpResult(response.statusCode(), response.body()));
    }

    static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int maximumBytes;
        private final ByteArrayOutputStream output;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private int received;
        private boolean finished;

        LimitedBodySubscriber(int maximumBytes) {
            if (maximumBytes <= 0) {
                throw new IllegalArgumentException("Response limit must be positive");
            }
            this.maximumBytes = maximumBytes;
            this.output = new ByteArrayOutputStream(Math.min(maximumBytes, 8192));
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = Objects.requireNonNull(subscription, "subscription");
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (finished) {
                return;
            }
            long incoming = 0;
            for (ByteBuffer buffer : buffers) {
                incoming += buffer.remaining();
            }
            if (incoming > maximumBytes - received) {
                finished = true;
                subscription.cancel();
                body.completeExceptionally(new ResponseLimitExceededException());
                return;
            }
            for (ByteBuffer buffer : buffers) {
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                output.writeBytes(bytes);
                received += bytes.length;
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (!finished) {
                finished = true;
                body.completeExceptionally(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (!finished) {
                finished = true;
                body.complete(output.toByteArray());
            }
        }
    }

    private static final class ResponseLimitExceededException extends RuntimeException {
        private ResponseLimitExceededException() {
            super("HTTP response exceeded its configured byte limit");
        }
    }
}
