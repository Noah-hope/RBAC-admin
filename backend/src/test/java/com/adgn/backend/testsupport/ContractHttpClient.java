package com.adgn.backend.testsupport;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** Opt-in validation of actual responses; delegates transport without changing request/auth behavior. */
public final class ContractHttpClient extends HttpClient {
    private final HttpClient delegate=HttpClient.newHttpClient();
    public static HttpClient create(){return "true".equals(System.getenv("RBAC_CONTRACT_CHECK"))?new ContractHttpClient():HttpClient.newHttpClient();}
    public Optional<CookieHandler> cookieHandler(){return delegate.cookieHandler();}
    public Optional<Duration> connectTimeout(){return delegate.connectTimeout();}
    public Redirect followRedirects(){return delegate.followRedirects();}
    public Optional<ProxySelector> proxy(){return delegate.proxy();}
    public SSLContext sslContext(){return delegate.sslContext();}
    public SSLParameters sslParameters(){return delegate.sslParameters();}
    public Optional<Authenticator> authenticator(){return delegate.authenticator();}
    public Version version(){return delegate.version();}
    public Optional<Executor> executor(){return delegate.executor();}
    public <T> HttpResponse<T> send(HttpRequest request,HttpResponse.BodyHandler<T> handler)throws IOException,InterruptedException{return OpenApiResponses.check(delegate.send(request,handler));}
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,HttpResponse.BodyHandler<T> handler){return delegate.sendAsync(request,handler).thenApply(OpenApiResponses::check);}
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,HttpResponse.BodyHandler<T> handler,HttpResponse.PushPromiseHandler<T> push){return delegate.sendAsync(request,handler,push).thenApply(OpenApiResponses::check);}
}
