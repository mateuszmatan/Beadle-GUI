package com.bbh.itss.beadle.support

import groovy.transform.Canonical

import java.net.CookieManager
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import static com.bbh.itss.beadle.support.Json.toJson
import static java.net.http.HttpClient.Builder.NO_PROXY
import static java.net.http.HttpRequest.BodyPublishers.noBody
import static java.net.http.HttpRequest.BodyPublishers.ofString
import static java.time.Duration.ofSeconds

/**
 * Calls the API like the browser of one person: the clients share their cookies, so the calls of a suite run in one
 * session (Beadle records {@code USER_SIGNED_IN} once per session); pass a {@link CookieManager} of its own for a
 * session of its own.
 */
class PortalClient {

    static final CookieManager SHARED_COOKIES = new CookieManager()

    final String baseUrl
    final CookieManager cookies
    private final HttpClient http

    PortalClient(String baseUrl, CookieManager cookies = SHARED_COOKIES) {
        this.baseUrl = baseUrl.replaceAll('/+$', '')
        this.cookies = cookies
        this.http = HttpClient.newBuilder()
                .connectTimeout(ofSeconds(5))
                .proxy(NO_PROXY)
                .cookieHandler(cookies)
                .build()
    }

    /** A client in the session whose cookies ({@code JSESSIONID=...; XSRF-TOKEN=...}) a browser holds. */
    static PortalClient withCookies(String baseUrl, String cookieHeader) {
        def cookies = new CookieManager()
        cookieHeader?.split(';')*.trim()?.findAll()?.each { String pair ->
            def parts = pair.split('=', 2)
            def cookie = new HttpCookie(parts[0], parts.length > 1 ? parts[1] : '')
            cookie.path = '/'
            cookie.version = 0
            cookies.cookieStore.add(URI.create(baseUrl), cookie)
        }
        new PortalClient(baseUrl, cookies)
    }

    Response get(String path) {
        send(request(path).GET())
    }

    Response post(String path, Object body = null) {
        send(request(path).POST(body == null ? noBody() : json(body)))
    }

    Response post(String path, Object body, Map<String, String> headers) {
        HttpRequest.Builder request = request(path)
        headers.each { name, value -> request.header(name, value) }
        send(request.POST(json(body)))
    }

    Response postRaw(String path, String contentType, String body) {
        send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(ofSeconds(30))
                .header('Content-Type', contentType)
                .POST(ofString(body)))
    }

    Response put(String path, Object body) {
        send(request(path).PUT(json(body)))
    }

    Response delete(String path) {
        send(request(path).DELETE())
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(ofSeconds(30))
                .header('Content-Type', 'application/json')
    }

    private static HttpRequest.BodyPublisher json(Object body) {
        ofString(toJson(body))
    }

    private Response send(HttpRequest.Builder request) {
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString())
        new Response(response.statusCode(), response.headers().map(), response.body())
    }

    @Canonical
    static class Response {
        int status
        Map<String, List<String>> headers
        String body

        Object getJson() {
            Json.parse(body)
        }

        String header(String name) {
            headers.find { it.key.equalsIgnoreCase(name) }?.value?.first()
        }

        @Override
        String toString() {
            "HTTP $status: $body"
        }
    }
}
