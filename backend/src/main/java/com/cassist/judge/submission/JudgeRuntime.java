package com.cassist.judge.submission;

import com.cassist.common.BusinessException;
import com.cassist.judge.core.JudgeEngine;
import com.cassist.judge.http.HttpGoJudgeTransport;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.time.Duration;

@Component
public final class JudgeRuntime {
    private final HttpGoJudgeTransport transport;
    private final JudgeEngine engine;
    private volatile long checkedAt;
    private volatile boolean available;
    public JudgeRuntime(@Value("${JUDGE_ENABLED:false}") boolean enabled,
            @Value("${JUDGE_BASE_URL:http://127.0.0.1:5050}") String base,
            @Value("${JUDGE_TOKEN:}") String token) {
        transport=enabled?new HttpGoJudgeTransport(URI.create(base),token,Duration.ofSeconds(130)):null;
        engine=transport==null?null:new JudgeEngine(transport);
    }
    public boolean enabled(){return engine!=null;}
    public JudgeEngine engine(){if(engine==null)throw new IllegalStateException("Judge disabled");return engine;}
    public synchronized void requireAvailable() {
        long now=System.nanoTime();
        if(transport==null)throw unavailable();
        if(checkedAt==0 || now-checkedAt>5_000_000_000L){available=transport.isReady();checkedAt=System.nanoTime();}
        if(!available)throw unavailable();
    }
    private static BusinessException unavailable(){return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"JUDGE_UNAVAILABLE","代码评测服务暂不可用，请稍后再试");}
    @PreDestroy public void close(){if(transport!=null)transport.close();}
}
