package com.cassist.ai;

import com.cassist.ai.AiProvider.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Credentials and raw provider replies never leave this adapter or enter logs. */
public final class DeepSeekProvider implements AiProvider, AutoCloseable {
    public static final URI ENDPOINT = URI.create("https://api.deepseek.com/chat/completions");
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final HttpClient client;
    private final JsonMapper json = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public DeepSeekProvider(String apiKey, String model, Duration connectTimeout, Duration responseTimeout) {
        this(ENDPOINT, apiKey, model, connectTimeout, responseTimeout);
    }

    // The only alternative destination is loopback, used by deterministic HTTP tests.
    DeepSeekProvider(URI endpoint, String apiKey, String model, Duration connectTimeout, Duration responseTimeout) {
        if (!ENDPOINT.equals(endpoint) && !("http".equals(endpoint.getScheme())
                && "127.0.0.1".equals(endpoint.getHost()) && endpoint.getUserInfo() == null
                && endpoint.getQuery() == null && endpoint.getFragment() == null))
            throw new IllegalArgumentException("Invalid DeepSeek endpoint");
        if (apiKey == null || apiKey.isBlank() || apiKey.chars().anyMatch(c -> c <= 32 || c >= 127))
            throw new IllegalArgumentException("Missing or invalid DeepSeek credential");
        if (model == null || !model.matches("[a-zA-Z0-9._-]{1,80}"))
            throw new IllegalArgumentException("Invalid DeepSeek model");
        this.endpoint = endpoint; this.apiKey = apiKey; this.model = model; this.timeout = responseTimeout;
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override public ProviderMode mode() { return ProviderMode.DEEPSEEK; }

    @Override public Guidance analyze(AnalysisRequest input) throws ProviderException {
        var data = Map.of("problemStatement", input.problem().statement(),
                "privateReferenceAnswer", input.problem().referenceAnswer(),
                "studentCode", input.code().sourceCode(), "studentCodeLineCount", input.code().lineCount(),
                "studentProfile", Map.of("addressName", input.addressName()));
        var payload = Map.of("model", model, "stream", false, "thinking", Map.of("type", "disabled"),
                "temperature", 0.2, "max_tokens", 2048, "response_format", Map.of("type", "json_object"),
                "messages", List.of(Map.of("role", "system", "content", input.systemInstruction()),
                        Map.of("role", "user", "content", json.writeValueAsString(data))));
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(payload))).build();
        HttpResponse<byte[]> response = send(request);
        if (response.statusCode() != 200) throw new ProviderException(switch (response.statusCode()) {
            case 401, 403 -> Failure.AUTHENTICATION;
            case 402 -> Failure.QUOTA_EXCEEDED;
            case 429 -> Failure.RATE_LIMITED;
            default -> Failure.UNAVAILABLE;
        });
        try {
            JsonNode choices = json.readTree(response.body()).path("choices");
            if (!choices.isArray() || choices.size() != 1) throw new ProviderException(Failure.INVALID_RESPONSE);
            JsonNode choice = choices.get(0);
            JsonNode content = choice.path("message").path("content");
            if (!"stop".equals(choice.path("finish_reason").asString()) || !content.isString()
                    || content.asString().isBlank()) throw new ProviderException(Failure.INVALID_RESPONSE);
            JsonNode guidance = json.readTree(content.asString());
            if (!fields(guidance, Set.of("summary", "findings")) || !guidance.path("summary").isString()
                    || !guidance.path("findings").isArray() || guidance.path("findings").size() > 12)
                throw new ProviderException(Failure.INVALID_RESPONSE);
            List<Finding> findings = new ArrayList<>();
            for (JsonNode finding : guidance.path("findings")) {
                if (!fields(finding, Set.of("startLine", "endLine", "category", "reason", "hint"))
                        || !finding.path("category").isString() || !finding.path("reason").isString()
                        || !finding.path("hint").isString()) throw new ProviderException(Failure.INVALID_RESPONSE);
                findings.add(new Finding(line(finding.get("startLine")), line(finding.get("endLine")),
                        finding.get("category").asString(), finding.get("reason").asString(), finding.get("hint").asString()));
            }
            return new Guidance(guidance.get("summary").asString(), List.copyOf(findings));
        } catch (ProviderException e) { throw e; }
        catch (RuntimeException e) { throw new ProviderException(Failure.INVALID_RESPONSE); }
    }

    private static boolean fields(JsonNode node, Set<String> expected) {
        return node.isObject() && node.propertyNames().equals(expected);
    }
    private static Integer line(JsonNode value) throws ProviderException {
        if (value != null && value.isNull()) return null;
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt())
            throw new ProviderException(Failure.INVALID_RESPONSE);
        return value.intValue();
    }
    private HttpResponse<byte[]> send(HttpRequest request) throws ProviderException {
        CompletableFuture<HttpResponse<byte[]>> future;
        try { future = client.sendAsync(request, ignored -> new BoundedBody()); }
        catch (RuntimeException e) { throw new ProviderException(Failure.UNAVAILABLE); }
        try { return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) {
            future.cancel(true); Thread.currentThread().interrupt(); throw new ProviderException(Failure.INTERRUPTED);
        } catch (TimeoutException e) {
            future.cancel(true); throw new ProviderException(Failure.TIMEOUT);
        } catch (ExecutionException e) {
            future.cancel(true);
            throw new ProviderException(hasCause(e, HttpTimeoutException.class) ? Failure.TIMEOUT
                    : hasCause(e, ResponseTooLarge.class) ? Failure.INVALID_RESPONSE : Failure.UNAVAILABLE);
        } catch (CancellationException e) { throw new ProviderException(Failure.UNAVAILABLE); }
    }
    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (int depth = 0; error != null && depth < 16; depth++, error = error.getCause())
            if (type.isInstance(error)) return true;
        return false;
    }
    @Override public void close() { client.shutdownNow(); }
    @Override public String toString() { return "DeepSeekProvider[model=" + model + "]"; }

    private static final class ResponseTooLarge extends RuntimeException { }
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final ByteArrayOutputStream data = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_BODY_BYTES - data.size()) {
                    subscription.cancel(); result.completeExceptionally(new ResponseTooLarge()); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); data.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(data.toByteArray()); }
    }
}
