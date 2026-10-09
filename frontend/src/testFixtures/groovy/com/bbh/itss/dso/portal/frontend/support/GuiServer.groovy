package com.bbh.itss.dso.portal.frontend.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer

import java.nio.file.Path
import java.util.concurrent.ExecutorService

import static java.net.InetAddress.getLoopbackAddress
import static java.nio.charset.StandardCharsets.UTF_8
import static java.nio.file.Files.isRegularFile
import static java.nio.file.Files.readAllBytes
import static java.util.concurrent.Executors.newFixedThreadPool

class GuiServer implements AutoCloseable {

    private static final Map<String, String> TYPES = [
            html : 'text/html; charset=utf-8', js: 'text/javascript; charset=utf-8', css: 'text/css; charset=utf-8',
            json : 'application/json', svg: 'image/svg+xml', png: 'image/png', ico: 'image/x-icon',
            woff2: 'font/woff2', woff: 'font/woff', ttf: 'font/ttf', txt: 'text/plain; charset=utf-8',
            map  : 'application/json'
    ]

    private final HttpServer server
    private final ExecutorService executor
    private final Path dist
    private final StubApi api

    private GuiServer(Path dist, StubApi api) {
        this.dist = dist.toAbsolutePath().normalize()
        this.api = api
        this.executor = newFixedThreadPool(8)
        this.server = HttpServer.create(new InetSocketAddress(getLoopbackAddress(), 0), 0)
        server.executor = executor
        server.createContext('/') { HttpExchange exchange -> serve(exchange) }
    }

    static GuiServer start(Path dist, StubApi api) {
        if (!isRegularFile(dist.resolve('index.html'))) {
            throw new IllegalStateException("No built gui in $dist; run ./gradlew :gui:buildGui")
        }
        def guiServer = new GuiServer(dist, api)
        guiServer.server.start()
        guiServer
    }

    String getUrl() {
        "http://127.0.0.1:${server.address.port}"
    }

    @Override
    void close() {
        server.stop(0)
        executor.shutdownNow()
    }

    private void serve(HttpExchange exchange) {
        try {
            def path = exchange.requestURI.rawPath
            if (path.startsWith('/api/')) {
                def body = exchange.requestBody.getText(UTF_8.name())
                def response = api.handle(exchange.requestMethod, path, exchange.requestURI.rawQuery, body)
                send(exchange, response.status, response.contentType, response.body.getBytes(UTF_8))
            } else {
                def file = resolve(path)
                def bytes = readAllBytes(file)
                send(exchange, 200, type(file), bytes)
            }
        } catch (Exception e) {
            send(exchange, 500, 'text/plain', String.valueOf(e.message).getBytes(UTF_8))
        } finally {
            exchange.close()
        }
    }

    private Path resolve(String rawPath) {
        def relative = URLDecoder.decode(rawPath, UTF_8).replaceFirst('^/+', '')
        def candidate = dist.resolve(relative).normalize()
        if (candidate.startsWith(dist) && isRegularFile(candidate)) {
            return candidate
        }
        dist.resolve('index.html')
    }

    private static String type(Path file) {
        def name = file.fileName.toString()
        def extension = name.contains('.') ? name.substring(name.lastIndexOf('.') + 1) : ''
        TYPES.getOrDefault(extension, 'application/octet-stream')
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) {
        exchange.responseHeaders.set('Content-Type', contentType)
        exchange.responseHeaders.set('Cache-Control', 'no-store')
        if (status == 204 || body.length == 0) {
            exchange.sendResponseHeaders(status, -1)
        } else {
            exchange.sendResponseHeaders(status, body.length)
            exchange.responseBody.withStream { it.write(body) }
        }
    }
}
