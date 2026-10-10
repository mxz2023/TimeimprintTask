package cn.net.mxz.timeimprint.task.boot.it;

import cn.net.mxz.timeimprint.task.identity.account.service.IdentityBootstrap;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.PushPromiseHandler;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/** 测试 HTTP 客户端：访问任务接口时自动带上本地开发账号令牌。已有 Authorization 时不覆盖。 */
public final class ItHttpFixture extends HttpClient {

    private final HttpClient delegate = HttpClient.newHttpClient();

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {
        return delegate.send(authorize(request), responseBodyHandler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request, BodyHandler<T> responseBodyHandler) {
        return delegate.sendAsync(authorize(request), responseBodyHandler);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request, BodyHandler<T> responseBodyHandler, PushPromiseHandler<T> pushPromiseHandler) {
        return delegate.sendAsync(authorize(request), responseBodyHandler, pushPromiseHandler);
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return delegate.cookieHandler();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return delegate.connectTimeout();
    }

    @Override
    public Redirect followRedirects() {
        return delegate.followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return delegate.proxy();
    }

    @Override
    public SSLContext sslContext() {
        return delegate.sslContext();
    }

    @Override
    public SSLParameters sslParameters() {
        return delegate.sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return delegate.authenticator();
    }

    @Override
    public Version version() {
        return delegate.version();
    }

    @Override
    public Optional<Executor> executor() {
        return delegate.executor();
    }

    @Override
    public WebSocket.Builder newWebSocketBuilder() {
        return delegate.newWebSocketBuilder();
    }

    private static HttpRequest authorize(HttpRequest request) {
        String path = request.uri().getPath();
        if (path == null || !path.startsWith("/api/v1/") || path.startsWith("/api/v1/users")) {
            return request;
        }
        if (request.headers().firstValue("Authorization").isPresent()) {
            return request;
        }
        return HttpRequest.newBuilder(request, (name, value) -> true)
                .header("Authorization", "Bearer " + IdentityBootstrap.LOCAL_TOKEN)
                .build();
    }
}
