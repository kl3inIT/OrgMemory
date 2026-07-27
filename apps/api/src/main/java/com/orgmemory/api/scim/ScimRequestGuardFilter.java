package com.orgmemory.api.scim;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;

final class ScimRequestGuardFilter extends OncePerRequestFilter {

    private final ScimSecurityProperties properties;

    ScimRequestGuardFilter(ScimSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = requestId(request.getHeader("X-Request-ID"));
        response.setHeader("X-Request-ID", requestId);

        if (properties.requireTls() && !request.isSecure()) {
            ScimErrorWriter.write(response, 403, "TLS is required");
            return;
        }
        long maximumBytes = properties.maximumRequestSize().toBytes();
        if (request.getContentLengthLong() > maximumBytes) {
            ScimErrorWriter.write(response, 413, "Request body exceeds the configured limit");
            return;
        }

        HttpServletRequest guardedRequest = request;
        if (mayCarryBody(request)) {
            if (maximumBytes >= Integer.MAX_VALUE) {
                throw new IllegalStateException(
                        "SCIM maximum request size must be less than 2 GiB");
            }
            int maximumBodySize;
            try {
                maximumBodySize = Math.toIntExact(maximumBytes);
            } catch (ArithmeticException tooLarge) {
                throw new IllegalStateException(
                        "SCIM maximum request size must fit in a signed 32-bit integer",
                        tooLarge);
            }
            byte[] body = request.getInputStream().readNBytes(maximumBodySize + 1);
            if (body.length > maximumBodySize) {
                ScimErrorWriter.write(
                        response, 413, "Request body exceeds the configured limit");
                return;
            }
            guardedRequest = new CachedBodyRequest(request, body);
        }
        filterChain.doFilter(guardedRequest, response);
    }

    private static boolean mayCarryBody(HttpServletRequest request) {
        return request.getContentLengthLong() > 0
                || request.getHeader("Transfer-Encoding") != null
                || switch (request.getMethod()) {
                    case "POST", "PUT", "PATCH" -> true;
                    default -> false;
                };
    }

    private static String requestId(String candidate) {
        if (candidate != null && candidate.matches("[A-Za-z0-9._-]{1,128}")) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body.clone();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public int read(byte[] bytes, int offset, int length) {
                    return input.read(bytes, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // OrgMemory handles bounded SCIM requests synchronously.
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null
                    ? StandardCharsets.UTF_8
                    : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
