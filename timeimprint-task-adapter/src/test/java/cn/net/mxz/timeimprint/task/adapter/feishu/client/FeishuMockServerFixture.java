package cn.net.mxz.timeimprint.task.adapter.feishu.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试用本地飞书 Mock（JDK HttpServer，无外网、无额外依赖）：按「路径」排队响应并记录请求。
 *
 * <p>路径不含 query；未排队时返回 404。{@code delayMillis > 0} 的响应在发送前休眠，用于超时用例。
 */
public final class FeishuMockServerFixture implements AutoCloseable {

    public record Recorded(String method, String path, String query, Map<String, String> headers, String body) {}

    public record Reply(int status, String body, long delayMillis) {}

    public static final String TOKEN_PATH = "/open-apis/auth/v3/tenant_access_token/internal";
    public static final String MESSAGES_PATH = "/open-apis/im/v1/messages";

    private final HttpServer server;
    private final Map<String, Deque<Reply>> replies = new HashMap<>();
    private final List<Recorded> requests = new ArrayList<>();

    public FeishuMockServerFixture() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public synchronized FeishuMockServerFixture enqueue(String path, int status, String body) {
        return enqueue(path, new Reply(status, body, 0));
    }

    public synchronized FeishuMockServerFixture enqueue(String path, Reply reply) {
        replies.computeIfAbsent(path, k -> new ArrayDeque<>()).add(reply);
        return this;
    }

    public synchronized List<Recorded> requests() {
        return List.copyOf(requests);
    }

    public synchronized List<Recorded> requestsTo(String path) {
        return requests.stream().filter(r -> r.path().equals(path)).toList();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> headers = new HashMap<>();
        exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), String.join(",", v)));
        String path = exchange.getRequestURI().getPath();
        Reply reply;
        synchronized (this) {
            requests.add(new Recorded(
                    exchange.getRequestMethod(), path, exchange.getRequestURI().getQuery(), headers, body));
            Deque<Reply> q = replies.get(path);
            reply = q == null || q.isEmpty() ? new Reply(404, "{\"code\":404}", 0) : q.poll();
        }
        if (reply.delayMillis() > 0) {
            try {
                Thread.sleep(reply.delayMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        try {
            exchange.sendResponseHeaders(reply.status(), out.length == 0 ? -1 : out.length);
            if (out.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(out);
                }
            }
        } catch (IOException ignored) {
            // 客户端已因超时断开
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
