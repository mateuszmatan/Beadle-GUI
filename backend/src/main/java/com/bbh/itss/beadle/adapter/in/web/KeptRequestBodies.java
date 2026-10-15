package com.bbh.itss.beadle.adapter.in.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import static org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST;

/**
 * Keeps the body of a request to an {@link AuditedRequest} endpoint as it was sent, so that the audit trail can show
 * the request even when the body cannot be read into its record or fails validation ({@link RefusedRequests}).
 */
@ControllerAdvice
public class KeptRequestBodies extends RequestBodyAdviceAdapter {

    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return parameter.hasMethodAnnotation(AuditedRequest.class);
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType)
            throws IOException {
        byte[] body;
        try (InputStream read = inputMessage.getBody()) {
            body = read.readAllBytes();
        }
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null) {
            request.setAttribute(RefusedRequests.KEPT_BODY, body, SCOPE_REQUEST);
        }
        return new KeptBody(inputMessage.getHeaders(), body);
    }

    private static final class KeptBody implements HttpInputMessage {

        private final HttpHeaders headers;
        private final byte[] body;

        private KeptBody(HttpHeaders headers, byte[] body) {
            this.headers = headers;
            this.body = body;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}
