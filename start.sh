#!/bin/bash
# =============================================================================
# EC2 t3.micro startup script — Product Service
# Run as: sudo bash start.sh
# =============================================================================

set -e

APP_JAR="product-service-1.0.0.jar"
APP_DIR="/opt/product-service"
LOG_DIR="/var/log/product-service"
HEAP_DUMP_DIR="/tmp/heap-dumps"

mkdir -p "$APP_DIR" "$LOG_DIR" "$HEAP_DUMP_DIR"

# ─── JVM flags tuned for t3.micro (1 GB RAM) ─────────────────────────────────
# -Xms256m        : Start heap at 256 MB — don't let JVM grab too much at boot
# -Xmx700m        : Hard cap at 700 MB — leaves ~300 MB for OS, Tomcat NIO,
#                   off-heap (Metaspace, direct buffers, thread stacks)
# -XX:+UseG1GC    : G1 handles concurrent marking better than ParallelGC under load
# -XX:MaxGCPauseMillis=200 : Target <200ms GC pauses (reduces latency spikes)
# -XX:+HeapDumpOnOutOfMemoryError : Dump heap when OOM occurs — CRITICAL for diagnosis
# -XX:HeapDumpPath            : Where to write the .hprof file
# -XX:+ExitOnOutOfMemoryError : Kill the process on OOM so systemd can restart it
#                               (better than hanging in a broken state)
# -Xss512k        : Reduce thread stack from default 1MB → 512KB (50 threads = 25MB saved)

JVM_OPTS=(
  "-Xms256m"
  "-Xmx700m"
  "-XX:+UseG1GC"
  "-XX:MaxGCPauseMillis=200"
  "-XX:+HeapDumpOnOutOfMemoryError"
  "-XX:HeapDumpPath=${HEAP_DUMP_DIR}/heap-dump.hprof"
  "-XX:+ExitOnOutOfMemoryError"
  "-Xss512k"
  "-XX:MetaspaceSize=64m"
  "-XX:MaxMetaspaceSize=128m"
  "-Djava.awt.headless=true"
  "-Dfile.encoding=UTF-8"
  # GC logging (to correlate GC pauses with high-QPS periods)
  "-Xlog:gc*:file=${LOG_DIR}/gc.log:time,uptime:filecount=5,filesize=10m"
)

echo "Starting Product Service with JVM opts: ${JVM_OPTS[*]}"

java "${JVM_OPTS[@]}" \
  -jar "${APP_DIR}/${APP_JAR}" \
  --spring.profiles.active=prod \
  >> "${LOG_DIR}/app.log" 2>&1 &

echo "Started PID=$!"
echo "$!" > /var/run/product-service.pid
echo "Logs: tail -f ${LOG_DIR}/app.log"
