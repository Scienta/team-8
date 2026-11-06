package no.scienta.cfp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Logs one line per HTTP request: method, path with query, status and duration, plus the request and response bodies
 * (truncated) of requests we refuse or fail (status 400 and up). This is how we see what the harness's checks send and
 * what we answer; the bodies of successful requests are left out because formatting them costs CPU the container
 * hasn't got to spare under the harness's bursts. {@code /health} is skipped, as the platform polls it, and so are the
 * requests of {@link WarmUpBeforeTraffic}, which still go through the filter to warm it up.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("http");
    private static final int MAX_BODY_CHARS = 1000;
    private static final int MAX_CACHED_BYTES = 64 * 1024;
    private static final int FIRST_STATUS_WITH_BODIES = 400;
    private static final Pattern LINE_BREAK = Pattern.compile("\\s*\\n\\s*");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/health".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request, MAX_CACHED_BYTES);
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);
        long start = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            long millis = (System.nanoTime() - start) / 1_000_000;
            if (request.getHeader(WarmUpBeforeTraffic.HEADER) == null) {
                log(req, res, millis);
            }
            res.copyBodyToResponse();
        }
    }

    private static void log(ContentCachingRequestWrapper req, ContentCachingResponseWrapper res, long millis) {
        if (res.getStatus() >= FIRST_STATUS_WITH_BODIES) {
            log.info("{} {} -> {} ({} ms) req={} res={}", req.getMethod(), pathWithQuery(req), res.getStatus(),
                    millis, body(req.getContentAsByteArray()), body(res.getContentAsByteArray()));
        } else {
            log.info("{} {} -> {} ({} ms)", req.getMethod(), pathWithQuery(req), res.getStatus(), millis);
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
        String text = LINE_BREAK.matcher(new String(content, StandardCharsets.UTF_8)).replaceAll(" ");
        return text.length() > MAX_BODY_CHARS ? text.substring(0, MAX_BODY_CHARS) + "…" : text;
    }
}
