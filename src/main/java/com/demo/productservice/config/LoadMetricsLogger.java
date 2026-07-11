package com.demo.productservice.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Scheduled logger that prints a live metrics snapshot every 10 seconds.
 * During a k6 load test, watch this in your Spring Boot terminal to see:
 *
 *   ┌─ LOAD METRICS @ 2024-01-01T12:00:10 ──────────────────────────────
 *   │  Heap:         245 MB used / 700 MB max  (35%)
 *   │  Threads:      active=42  peak=50  daemon=18
 *   │  GC (G1Young): collections=12  totalPause=180ms  avgPause=15ms
 *   │  RateLimiter:  available_permits=0  rejected_calls=1240
 *   │  Throughput:   requests_last_10s=1489  (~148 req/s)
 *   └────────────────────────────────────────────────────────────────────
 *
 * This tells you at a glance:
 *   - Heap climbing?     → OOM risk, GC pressure
 *   - Threads at peak?   → Tomcat thread pool exhaustion
 *   - GC pause high?     → Latency spikes explained
 *   - Permits = 0?       → Rate limiter is active, 429s being sent
 *   - req/s dropping?    → Server struggling under load
 */
@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class LoadMetricsLogger {

    private final RateLimiterRegistry rateLimiterRegistry;

    private final MemoryMXBean          memoryMX    = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean          threadMX    = ManagementFactory.getThreadMXBean();

    // Track requests between snapshots
    private static final AtomicLong requestCounter = new AtomicLong(0);
    private long lastSnapshotRequests = 0;

    // Called by RequestLoggingFilter to increment the counter
    public static void recordRequest() {
        requestCounter.incrementAndGet();
    }

    @Scheduled(fixedDelay = 10_000)   // every 10 seconds
    public void logMetrics() {
        // ── Memory ────────────────────────────────────────────────────────────
        MemoryUsage heap    = memoryMX.getHeapMemoryUsage();
        long usedMB         = heap.getUsed()      / (1024 * 1024);
        long maxMB          = heap.getMax()       / (1024 * 1024);
        int  heapPct        = maxMB > 0 ? (int)((usedMB * 100) / maxMB) : 0;
        String heapWarning  = heapPct > 80 ? "  ⚠️  HIGH HEAP — GC pressure / OOM risk!" : "";

        // ── Threads ───────────────────────────────────────────────────────────
        int activeThreads = threadMX.getThreadCount();
        int peakThreads   = threadMX.getPeakThreadCount();
        int daemonThreads = threadMX.getDaemonThreadCount();
        threadMX.resetPeakThreadCount();  // reset peak after each snapshot

        // ── GC ────────────────────────────────────────────────────────────────
        StringBuilder gcStats = new StringBuilder();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count  = gc.getCollectionCount();
            long timeMs = gc.getCollectionTime();
            long avgMs  = count > 0 ? timeMs / count : 0;
            gcStats.append(String.format("\n  │  GC (%-12s): collections=%d  totalPause=%dms  avgPause=%dms%s",
                    gc.getName(), count, timeMs, avgMs,
                    avgMs > 200 ? "  ⚠️  LONG GC PAUSE!" : ""));
        }

        // ── Rate limiter ──────────────────────────────────────────────────────
        String rateLimiterInfo = "not configured";
        try {
            RateLimiter rl          = rateLimiterRegistry.rateLimiter("productApi");
            RateLimiter.Metrics m   = rl.getMetrics();
            int available           = m.getAvailablePermissions();
            long rejected           = m.getNumberOfWaitingThreads();
            rateLimiterInfo = String.format("available_permits=%d  waiting_threads=%d%s",
                    available, rejected,
                    available == 0 ? "  ← LIMITER ACTIVE — 429s being sent" : "  ← OK");
        } catch (Exception ignored) {}

        // ── Throughput ────────────────────────────────────────────────────────
        long totalNow     = requestCounter.get();
        long delta        = totalNow - lastSnapshotRequests;
        lastSnapshotRequests = totalNow;
        long rps          = delta / 10;  // requests per second over last 10s

        String throughputWarning = rps > 150
                ? "  ⚠️  OVER CAPACITY — expect 429s"
                : rps > 120 ? "  ← NEAR LIMIT" : "  ← OK";

        // ── Print snapshot ────────────────────────────────────────────────────
        log.info("\n  ┌─ LOAD METRICS ─────────────────────────────────────────" +
                 "\n  │  Heap:        {} MB used / {} MB max  ({}%)  {}" +
                 "\n  │  Threads:     active={}  peakSinceLast={}  daemon={}" +
                 "{}" +
                 "\n  │  RateLimiter: {}" +
                 "\n  │  Throughput:  requests_last_10s={}  (~{} req/s){}" +
                 "\n  └────────────────────────────────────────────────────────",
                usedMB, maxMB, heapPct, heapWarning,
                activeThreads, peakThreads, daemonThreads,
                gcStats,
                rateLimiterInfo,
                delta, rps, throughputWarning);
    }
}
