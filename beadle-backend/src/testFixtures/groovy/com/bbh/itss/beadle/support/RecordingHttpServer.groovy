package com.bbh.itss.beadle.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import groovy.transform.Canonical

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

import static java.net.InetAddress.getLoopbackAddress
import static java.nio.charset.StandardCharsets.UTF_8

/**
 * A local HTTP server that stands for Beadle-Internal or Beadle-ProTech in the specs of their REST adapters: it
 * records every request with its headers and body and answers what the spec set for its method and path.
 */
class RecordingHttpServer implements AutoCloseable {

    final List<Recorded> requests = new CopyOnWriteArrayList<>()
    private final Map<String, Answer> answers = new ConcurrentHashMap<>()
    private final HttpServer server = HttpServer.create(new InetSocketAddress(getLoopbackAddress(), 0), 0)

    RecordingHttpServer() {
        server.createContext('/') { HttpExchange exchange -> serve(exchange) }
        server.start()
    }

    String getUrl() {
        "http://127.0.0.1:${server.address.port}"
    }

    RecordingHttpServer answer(String method, String path, int status, Object body = null,
                               String contentType = 'application/json') {
        answers["$method $path".toString()] = new Answer(status, body instanceof String ? body as String
                : body == null ? '' : Json.toJson(body), contentType)
        this
    }

    Recorded getLast() {
        requests ? requests.last() : null
    }

    @Override
    void close() {
        server.stop(0)
    }

    private void serve(HttpExchange exchange) {
        String text = exchange.requestBody.getText(UTF_8.name())
        Recorded request = new Recorded(exchange.requestMethod, exchange.requestURI.path,
                exchange.requestURI.rawQuery, exchange.requestHeaders.collectEntries { name, values ->
            [name.toLowerCase(Locale.ROOT), values.first()]
        } as Map<String, String>, text)
        requests << request
        Answer answer = answers["$request.method $request.path".toString()] ?:
                new Answer(404, '{"title":"Not Found","status":404,"detail":"No answer set"}',
                        'application/problem+json')
        byte[] bytes = answer.body.getBytes(UTF_8)
        if (answer.body) {
            exchange.responseHeaders.add('Content-Type', answer.contentType)
        }
        exchange.sendResponseHeaders(answer.status, bytes.length ?: -1)
        if (bytes.length) {
            exchange.responseBody.withStream { it.write(bytes) }
        }
        exchange.close()
    }

    @Canonical
    static class Answer {
        int status
        String body
        String contentType
    }

    @Canonical
    static class Recorded {
        String method
        String path
        String query
        Map<String, String> headers
        String body

        String header(String name) {
            headers[name.toLowerCase(Locale.ROOT)]
        }

        Object getJson() {
            body ? Json.parse(body) : null
        }

        String getDecodedQuery() {
            query == null ? null : URLDecoder.decode(query, UTF_8)
        }

        /** The query parameters as a server reads them: split on {@code &} first, then each name and value decoded. */
        Map<String, String> getParams() {
            query ? query.split('&').collectEntries { pair ->
                def parts = pair.split('=', 2)
                [(URLDecoder.decode(parts[0], UTF_8)): parts.length > 1 ? URLDecoder.decode(parts[1], UTF_8) : '']
            } as Map<String, String> : [:]
        }
    }
}
