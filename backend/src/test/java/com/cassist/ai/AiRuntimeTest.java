package com.cassist.ai;

import com.cassist.common.BusinessException;
import com.cassist.problem.ProblemAccessPort;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiRuntimeTest {
    private AiRuntime mockRuntime() {
        var env = new MockEnvironment().withProperty("cassist.ai.mode", "mock"); env.setActiveProfiles("demo");
        return new AiRuntime(mock(ProblemAccessPort.class), env);
    }
    @Test void productionCannotEnableMockAndMissingKeyNeverFallsBack() {
        for (String mode : new String[]{"mock", "deepseek", "disabled"}) {
            var runtime = new AiRuntime(mock(ProblemAccessPort.class), new MockEnvironment().withProperty("cassist.ai.mode", mode));
            try {
                assertEquals("DISABLED", runtime.mode());
                AtomicBoolean persisted = new AtomicBoolean();
                assertThrows(BusinessException.class, () -> runtime.submit("u", () -> persisted.set(true), () -> {}));
                assertFalse(persisted.get());
            } finally { runtime.close(); }
        }
    }
    @Test void returnsImmediatelyAndBoundsGlobalAndPerStudentConcurrency() throws Exception {
        var runtime = mockRuntime(); var release = new CountDownLatch(1); var entered = new CountDownLatch(2);
        Runnable work = () -> { entered.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } };
        try {
            runtime.submit("one", () -> {}, work);
            assertThrows(BusinessException.class, () -> runtime.submit("one", () -> fail("Should not persist duplicate"), work));
            runtime.submit("two", () -> {}, work);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertThrows(BusinessException.class, () -> runtime.submit("three", () -> fail("Should not persist overflow"), work));
        } finally { release.countDown(); runtime.close(); }
    }
    @Test void persistenceFailureReleasesOwnerAndCapacity() throws Exception {
        var runtime = mockRuntime(); var done = new CountDownLatch(1);
        try {
            assertThrows(IllegalStateException.class, () -> runtime.submit("one", () -> { throw new IllegalStateException(); }, () -> {}));
            runtime.submit("one", () -> {}, done::countDown);
            assertTrue(done.await(2, TimeUnit.SECONDS));
        } finally { runtime.close(); }
    }
}
