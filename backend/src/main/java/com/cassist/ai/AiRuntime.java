package com.cassist.ai;

import com.cassist.common.BusinessException;
import com.cassist.identity.Actor;
import com.cassist.identity.AiReplyStyle;
import com.cassist.problem.ProblemAccessPort;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Bounded asynchronous requests; no automatic retries of potentially billable inference. */
@Component
public final class AiRuntime {
    private final AuthorizedAiGuidanceService service;
    private final AiProvider provider;
    private final ExecutorService workers;
    private final Semaphore slots;
    private final Set<String> activeOwners = ConcurrentHashMap.newKeySet();

    public AiRuntime(ProblemAccessPort problems, Environment env) {
        String mode = env.getProperty("cassist.ai.mode", "disabled");
        int concurrency = 2;
        Duration connect = Duration.ofSeconds(5), response = Duration.ofSeconds(45);
        AiProviderSettings settings = null;
        if ("mock".equals(mode) && Arrays.stream(env.getActiveProfiles()).anyMatch(p -> p.equals("demo") || p.equals("local"))) {
            provider = new MockAiProvider();
            settings = new AiProviderSettings(provider.mode(), null, null, connect, response, concurrency, 65536, 8000);
        } else if ("deepseek".equals(mode)) {
            String key = env.getProperty("cassist.ai.deepseek.api-key", "");
            String model = env.getProperty("cassist.ai.deepseek.model", "deepseek-flash");
            provider = key.isBlank() ? null : new DeepSeekProvider(key, model, connect, response);
            if (provider != null) settings = new AiProviderSettings(provider.mode(), DeepSeekProvider.ENDPOINT,
                    model, connect, response, concurrency, 65536, 8000);
        } else provider = null;
        service = provider == null ? null : new AuthorizedAiGuidanceService(problems, new AiGuidanceCore(provider, settings));
        slots = new Semaphore(concurrency);
        workers = Executors.newFixedThreadPool(concurrency, Thread.ofPlatform().name("ai-guidance-", 0).factory());
    }

    public String mode() { return provider == null ? "DISABLED" : provider.mode().name(); }
    public AiGuidanceCore.Result analyze(Actor actor, String assignment, String problem, String version, String source)
            throws AiProvider.ProviderException {
        return analyze(actor, assignment, problem, version, source, AiReplyStyle.GUIDED);
    }
    public AiGuidanceCore.Result analyze(Actor actor, String assignment, String problem, String version, String source, AiReplyStyle style)
            throws AiProvider.ProviderException {
        return analyze(actor, assignment, problem, version, source, style, "");
    }
    public AiGuidanceCore.Result analyze(Actor actor, String assignment, String problem, String version, String source, AiReplyStyle style, String addressName)
            throws AiProvider.ProviderException {
        requireConfigured();
        return service.analyze(actor, assignment, problem, version, source, style, addressName);
    }
    private void requireConfigured() {
        if (service == null) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "AI 服务尚未配置，请联系教师或管理员");
    }
    public void submit(String owner, Runnable persistBeforeStart, Runnable work) {
        requireConfigured();
        if (!activeOwners.add(owner)) throw busy();
        if (!slots.tryAcquire()) { activeOwners.remove(owner); throw busy(); }
        try {
            persistBeforeStart.run();
            workers.execute(() -> {
                try { work.run(); }
                finally { release(owner); }
            });
        } catch (RuntimeException e) { release(owner); throw e; }
    }
    private void release(String owner) { activeOwners.remove(owner); slots.release(); }
    private static BusinessException busy() {
        return new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "AI_BUSY", "已有分析正在进行或服务繁忙，请等待后再试");
    }
    @PreDestroy public void close() {
        workers.shutdownNow();
        if (provider instanceof DeepSeekProvider deepSeek) deepSeek.close();
    }
}
