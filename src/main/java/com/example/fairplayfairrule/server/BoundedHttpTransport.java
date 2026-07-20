package com.example.fairplayfairrule.server;

import java.net.http.HttpRequest;
import java.util.concurrent.CompletableFuture;

/** HTTP boundary used by Discord delivery so response bodies are always capped. */
public interface BoundedHttpTransport {
    CompletableFuture<HttpResult> send(HttpRequest request, int maximumResponseBytes);

    record HttpResult(int statusCode, byte[] body) {
        public HttpResult {
            body = body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }
}
