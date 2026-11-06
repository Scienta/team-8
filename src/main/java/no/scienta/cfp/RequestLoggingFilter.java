package no.scienta.cfp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Logs one line per HTTP request: method, path with query, status, duration, and the request and response bodies
 * (truncated). This is how we see what the harness's checks send and what we answer. {@code /health} is skipped, as
 * the platform polls it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("http");
    private static final int MAX_BODY_CHARS = 1000;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/health".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);
        long start = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            long millis = (System.nanoTime() - start) / 1_000_000;
            log.info("{} {} -> {} ({} ms) req={} res={}", request.getMethod(), pathWithQuery(request),
                    res.getStatus(), millis, body(req.getContentAsByteArray()), body(res.getContentAsByteArray()));
            res.copyBodyToResponse();
        }
    }

    static String pathWithQuery(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    static String body(byte[] content) {
        if (content.length == 0) {
            return "-";
        }
        String text = new String(content, StandardCharsets.UTF_8).replaceAll("\\s*\\n\\s*", " ");
        return text.length() > MAX_BODY_CHARS ? text.substring(0, MAX_BODY_CHARS) + "…" : text;
    }
}
