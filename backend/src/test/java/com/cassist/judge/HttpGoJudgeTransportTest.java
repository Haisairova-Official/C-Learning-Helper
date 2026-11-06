package com.cassist.judge;

import com.cassist.judge.http.HttpGoJudgeTransport;
import com.cassist.judge.protocol.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class HttpGoJudgeTransportTest {
    @Test void sendsBearerAndValidJsonAndAcceptsUnknownMetadata() throws Exception {
        var received = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/run", exchange -> {
            try {
                var json = JsonMapper.builder().build().readTree(exchange.getRequestBody().readAllBytes());
                received.set("Bearer test-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))
                        && json.get("cmd").get(0).get("args").get(0).asString().equals("/usr/bin/gcc"));
                byte[] body = "[{\"status\":\"Accepted\",\"exitStatus\":0,\"time\":1,\"memory\":2,\"runTime\":3,\"files\":{\"stdout\":\"\"},\"fileIds\":{\"main\":\"cache1\"},\"newField\":true}]".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body);
            } finally { exchange.close(); }
        });
        server.createContext("/file/cache1", exchange -> { exchange.sendResponseHeaders(404,-1); exchange.close(); });
        server.start();
        try (var transport = client(server,Duration.ofSeconds(3))) {
            var result = transport.execute(GoJudgeRequests.compile("int main(void){return 0;}"));
            assertEquals("cache1",result.fileIds().get("main")); assertTrue(received.get());
            assertDoesNotThrow(()->transport.deleteCachedFile("cache1"));
        } finally { server.stop(0); }
    }
    @Test void upstreamErrorsNeverExposeBodyOrToken() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/run", exchange -> {
            byte[] body = "sensitive upstream body".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401,body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try (var transport = client(server,Duration.ofSeconds(3))) {
            var e=assertThrows(JudgeTransportException.class,()->transport.execute(Map.of()));
            assertEquals("JUDGE_HTTP_ERROR",e.code()); assertFalse(e.getMessage().contains("sensitive"));
        } finally { server.stop(0); }
    }
    @Test void malformedOrMultipleResultsAreRejected() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/run", exchange -> {
            byte[] body = "[{},{}]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try (var transport = client(server,Duration.ofSeconds(3))) {
            assertEquals("JUDGE_INVALID_RESPONSE",assertThrows(JudgeTransportException.class,()->transport.execute(Map.of())).code());
        } finally { server.stop(0); }
    }
    @Test void wholeBodyHasDeadlineEvenAfterHeadersArrive() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var executor = Executors.newSingleThreadExecutor(); server.setExecutor(executor);
        server.createContext("/run", exchange -> {
            try {
                exchange.sendResponseHeaders(200,0); exchange.getResponseBody().write('['); exchange.getResponseBody().flush();
                Thread.sleep(1800);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try (var transport = client(server,Duration.ofSeconds(1))) {
            var e=assertThrows(JudgeTransportException.class,()->transport.execute(Map.of()));
            assertEquals("JUDGE_TRANSPORT_TIMEOUT",e.code());
        } finally { server.stop(0); executor.shutdownNow(); }
    }
    @Test void oversizedResponseIsBounded() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/run", exchange -> {
            try {
                exchange.sendResponseHeaders(200,0);
                byte[] chunk = new byte[65_536];
                for (int i=0;i<257;i++) exchange.getResponseBody().write(chunk);
            } catch (java.io.IOException expectedOnCancellation) { /* Client cancels once its cap is reached. */ }
            finally { exchange.close(); }
        });
        server.start();
        try (var transport = client(server,Duration.ofSeconds(5))) {
            assertEquals("JUDGE_TRANSPORT_FAILURE",assertThrows(JudgeTransportException.class,()->transport.execute(Map.of())).code());
        } finally { server.stop(0); }
    }
    @Test void rejectsRemoteOriginsPathInjectionAndBlankToken() {
        assertThrows(IllegalArgumentException.class,()->new HttpGoJudgeTransport(URI.create("http://example.com:5050"),"token",Duration.ofSeconds(2)));
        assertThrows(IllegalArgumentException.class,()->new HttpGoJudgeTransport(URI.create("http://127.0.0.1:5050/other"),"token",Duration.ofSeconds(2)));
        assertThrows(IllegalArgumentException.class,()->new HttpGoJudgeTransport(URI.create("http://127.0.0.1:5050")," ",Duration.ofSeconds(2)));
    }
    private HttpGoJudgeTransport client(HttpServer server, Duration timeout) {
        return new HttpGoJudgeTransport(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"test-token",timeout);
    }
}
