package com.cassist.judge.http;

import com.cassist.judge.protocol.*;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Local-only adapter. No endpoint, executable, arguments or file paths come from a student request. */
public final class HttpGoJudgeTransport implements GoJudgeTransport, AutoCloseable {
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
    private final URI endpoint;
    private final String token;
    private final Duration timeout;
    private final HttpClient client;
    private final JsonMapper mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    public HttpGoJudgeTransport(URI endpoint, String token, Duration timeout) {
        if (endpoint == null || !"http".equals(endpoint.getScheme())
                || endpoint.getHost() == null || !Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(endpoint.getHost())
                || endpoint.getPort() < 1 || endpoint.getRawUserInfo() != null || endpoint.getRawQuery() != null
                || endpoint.getRawFragment() != null || !(endpoint.getPath().isEmpty() || "/".equals(endpoint.getPath()))) {
            throw new IllegalArgumentException("Judge endpoint must be a local HTTP origin");
        }
        if (token == null || token.isBlank() || token.chars().anyMatch(c -> c <= 32 || c >= 127)) throw new IllegalArgumentException("Judge token required");
        if (timeout == null || timeout.compareTo(Duration.ofSeconds(1)) < 0 || timeout.compareTo(Duration.ofMinutes(3)) > 0) {
            throw new IllegalArgumentException("Invalid judge request timeout");
        }
        this.endpoint = endpoint; this.token = token; this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public GoJudgeResult execute(Map<String, Object> request) throws JudgeTransportException {
        try {
            HttpRequest req = builder("/run").header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(request))).build();
            HttpResponse<byte[]> response = send(req);
            if (response.statusCode() != 200) throw new JudgeTransportException("JUDGE_HTTP_ERROR");
            GoJudgeResult[] results = mapper.readValue(response.body(), GoJudgeResult[].class);
            if (results == null || results.length != 1 || results[0] == null) throw new JudgeTransportException("JUDGE_INVALID_RESPONSE");
            return results[0];
        } catch (JudgeTransportException e) { throw e; }
        catch (RuntimeException e) { throw new JudgeTransportException("JUDGE_INVALID_RESPONSE"); }
    }

    @Override public void deleteCachedFile(String id) throws JudgeTransportException {
        GoJudgeRequests.requireFileId(id);
        HttpResponse<byte[]> response = send(builder("/file/" + id).DELETE().build());
        if (response.statusCode() != 200 && response.statusCode() != 204 && response.statusCode() != 404) {
            throw new JudgeTransportException("JUDGE_CLEANUP_HTTP_ERROR");
        }
    }

    public boolean isReady() {
        try {
            var response = send(builder("/config").timeout(Duration.ofSeconds(3)).GET().build());
            if (response.statusCode()!=200) return false;
            var config=mapper.readTree(response.body()).get("runnerConfig");
            if (config==null || !config.path("seccomp").asBoolean()) return false;
            var controllers=new java.util.HashSet<String>();
            for (var entry:config.path("cgroupControllers")) controllers.add(entry.asString());
            return controllers.containsAll(Set.of("cpu","memory","pids"));
        } catch (Exception e) { return false; }
    }
    @Override public void close() { client.close(); }

    private HttpRequest.Builder builder(String path) {
        return HttpRequest.newBuilder(endpoint.resolve(path)).timeout(timeout).header("Authorization", "Bearer " + token);
    }

    private HttpResponse<byte[]> send(HttpRequest request) throws JudgeTransportException {
        CompletableFuture<HttpResponse<byte[]>> future;
        try { future = client.sendAsync(request, ignored -> new BoundedBody(MAX_RESPONSE_BYTES)); }
        catch (RuntimeException e) { throw new JudgeTransportException("JUDGE_TRANSPORT_FAILURE"); }
        try {
            // Explicit whole-response deadline also applies while reading the response body.
            return future.get(request.timeout().orElse(timeout).toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            future.cancel(true); Thread.currentThread().interrupt(); throw new JudgeTransportException("JUDGE_INTERRUPTED");
        } catch (TimeoutException e) {
            future.cancel(true); throw new JudgeTransportException("JUDGE_TRANSPORT_TIMEOUT");
        } catch (ExecutionException | CancellationException e) {
            future.cancel(true); throw new JudgeTransportException("JUDGE_TRANSPORT_FAILURE");
        }
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream data = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private BoundedBody(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription s) { subscription = s; s.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer b : buffers) {
                if (b.remaining() > limit - data.size()) {
                    subscription.cancel(); result.completeExceptionally(new IllegalStateException("JUDGE_RESPONSE_TOO_LARGE")); return;
                }
                byte[] chunk = new byte[b.remaining()]; b.get(chunk); data.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(data.toByteArray()); }
    }
}
