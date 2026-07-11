package com.demo.productservice.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servlet filter that logs EVERY request and response.
 *
 * Log format (INFO):
 *   [REQ]  GET /api/v1/products/1  thread=http-nio-8080-exec-3
 *   [RES]  GET /api/v1/products/1  status=200  duration=4ms  thread=http-nio-8080-exec-3
 *
 * During load test you'll see:
 *   [RES] ... status=429  → rate limiter firing
 *   [RES] ... status=503  → Tomcat thread pool exhausted
 *   [RES] ... duration=800ms → GC pause or DB pool contention
 *
 * How to grep useful patterns from logs:
 *   grep "\[RES\].*status=429" app.log | wc -l        → count rate-limited requests
 *   grep "\[RES\].*duration=[0-9]\{3,\}ms" app.log    → find slow responses (>100ms)
 *   grep "\[RES\].*status=5" app.log                  → all 5xx errors
 */
@Component
@Order(1)   // Run first in filter chain — before Spring Security, rate limiter, etc.
@Slf4j
public class RequestLoggingFilter implements Filter {

    // Global counters — visible in logs every N requests
    private static final AtomicLong totalRequests  = new AtomicLong(0);
    private static final AtomicLong totalRejected  = new AtomicLong(0);   // 429s
    private static final AtomicLong totalErrors    = new AtomicLong(0);   // 5xx
    private static final AtomicLong totalSucceeded = new AtomicLong(0);   // 2xx

    // Log a summary line every 500 requests
    private static final long LOG_SUMMARY_EVERY = 500;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        String method  = request.getMethod();
        String uri     = request.getRequestURI();
        String thread  = Thread.currentThread().getName();
        long   start   = System.currentTimeMillis();

        // ── Log incoming request ──────────────────────────────────────────────
        log.debug("[REQ]  {} {}  thread={}", method, uri, thread);

        try {
            chain.doFilter(req, res);
        } finally {
            long   duration = System.currentTimeMillis() - start;
            int    status   = response.getStatus();
            long   reqCount = totalRequests.incrementAndGet();

            // Categorise and count
            if (status == 429) {
                totalRejected.incrementAndGet();
            } else if (status >= 500) {
                totalErrors.incrementAndGet();
            } else if (status >= 200 && status < 300) {
                totalSucceeded.incrementAndGet();
            }

            // ── Log every response (INFO for slow/error, DEBUG for fast 2xx) ──
            String logLine = String.format("[RES]  %s %s  status=%d  duration=%dms  thread=%s",
                    method, uri, status, duration, thread);

            if (status == 429) {
                // Rate limited — WARN so it stands out
                log.warn("[RES]  {} {}  status=429  duration={}ms  ← RATE LIMITED (resilience4j)",
                        method, uri, duration);

            } else if (status >= 500) {
                log.error("[RES]  {} {}  status={}  duration={}ms  ← SERVER ERROR",
                        method, uri, status, duration);

            } else if (duration > 500) {
                // Slow response — likely GC pause or DB pool contention
                log.warn("[RES]  {} {}  status={}  duration={}ms  ← SLOW (>{} ms threshold)",
                        method, uri, status, duration, 500);

            } else if (duration > 100) {
                log.info("[RES]  {} {}  status={}  duration={}ms  ← MODERATE",
                        method, uri, status, duration);

            } else {
                // Normal fast response — DEBUG to avoid log spam at 150+ QPS
                log.debug(logLine);
            }

            // ── Summary every 500 requests ────────────────────────────────────
            if (reqCount % LOG_SUMMARY_EVERY == 0) {
                log.info("━━━ REQUEST SUMMARY ━━━  total={}  succeeded={}  " +
                         "rate-limited(429)={}  server-errors(5xx)={}",
                        reqCount,
                        totalSucceeded.get(),
                        totalRejected.get(),
                        totalErrors.get());
            }
        }
    }
}
