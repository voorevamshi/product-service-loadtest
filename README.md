# Product Service — Spring Boot on t3.micro

Production-ready REST API demonstrating realistic capacity limits of an AWS `t3.micro`
instance (1 GB RAM, 2 vCPU) and what happens when you exceed them.

---

## 🏗️ Project Structure

```
product-service/
├── src/main/java/com/demo/productservice/
│   ├── controller/   ProductController.java     (REST endpoints + rate limiter)
│   ├── service/      ProductService.java         (business logic + cache)
│   ├── repository/   ProductRepository.java      (JPA queries)
│   ├── model/        Product.java               (JPA entity)
│   ├── dto/          ProductRequest/Response/ApiResponse/PageResponse
│   ├── config/       CacheConfig, DataSeeder
│   └── exception/    GlobalExceptionHandler, custom exceptions
├── k6/
│   ├── overload-test.js   (ramping 50→300 QPS with stage analysis)
│   └── spike-test.js      (sudden burst to 300 QPS)
├── jmeter/
│   └── overload-test-plan.jmx
├── start.sh                (JVM-tuned startup script)
├── product-service.service (systemd unit)
└── pom.xml
```

- [Product Service Load Testing with k6](docs/loadTestingWithK6.md)
- [dropped iterations](docs/dropped_iterations.md)
- [Why Caffeine Breaks with Autoscaling](docs/whyCaffeineBreaksWithAutoscaling.md)
- [The Fix — Redis as Distributed Cache](docs/TheFix_RedisasDistributedCache.md)
---

## 🚀 Build & Run

```bash
# Build
mvn clean package -DskipTests

# Run locally (development)
java -jar target/product-service-1.0.0.jar

# Run with production JVM tuning
bash start.sh
```

---

## 📡 API Endpoints

| Method | Path                        | Description              |
|--------|-----------------------------|--------------------------|
| POST   | `/api/v1/products`          | Create product           |
| GET    | `/api/v1/products/{id}`     | Get by ID (cached)       |
| GET    | `/api/v1/products/sku/{sku}`| Get by SKU (cached)      |
| GET    | `/api/v1/products`          | List with filters + page |
| PUT    | `/api/v1/products/{id}`     | Update                   |
| DELETE | `/api/v1/products/{id}`     | Delete                   |
| GET    | `/actuator/health`          | Health check             |
| GET    | `/actuator/prometheus`      | Metrics for Grafana      |
| GET    | `/actuator/heapdump`        | Heap dump (use during OOM!) |
| GET    | `/actuator/threaddump`      | Thread dump              |

---

## 💥 What Happens at 200+ QPS on t3.micro

The instance comfortably handles ≈150 QPS. Beyond that, a cascade of failures begins:

### Failure 1 — HTTP 429 Too Many Requests (first line of defence)
**When:** Immediately at 151st req/s  
**Why:** Resilience4j `RateLimiter` is configured at 150 req/s cap  
**Observed:** Clients receive `{"success":false,"error":"Too many requests..."}`  
**Protect:** YES — this is the intended behaviour. Without it you'd see failures below.

```
resilience4j.ratelimiter.instances.productApi.limit-for-period=150
```

---

### Failure 2 — Thread Pool Exhaustion → HTTP 503 Service Unavailable
**When:** ~160-180 QPS (if rate limiter disabled)  
**Why:** Tomcat is configured with `max-threads=50`. At 200 req/s each taking ~10ms
to process, you need `200 * 0.01 = 2` threads minimum — but with DB latency
of 5-20ms it becomes `200 * 0.02 = 4` threads. Under GC pressure latency
spikes to 200ms → needs `200 * 0.2 = 40` threads → pool hits 50 → requests queue.  
**Queue depth:** `accept-count=100`. When queue fills → Tomcat rejects with 503.

```log
ERROR o.a.c.c.C.[.[.[.[dispatcherServlet] Servlet.service() threw exception
java.util.concurrent.RejectedExecutionException: ...
```

**Fix:** Increase `max-threads` (but each thread costs ~1 MB stack on heap).

---

### Failure 3 — HikariCP Connection Pool Saturation → Request Timeout
**When:** ~180 QPS sustained (if rate limiter disabled)  
**Why:** Pool is capped at `maximum-pool-size=10`. At 200 QPS with 5ms DB queries,
pool handles `10 / 0.005 = 2000 DB ops/s` — fine in theory. But under GC pressure
DB latency rises to 50ms → pool handles only `10 / 0.05 = 200 DB ops/s` → callers
queue for a connection → after `connection-timeout=3000ms` they get:

```log
com.zaxxer.hikari.pool.HikariPool$PoolInitializationException
Unable to acquire JDBC Connection within 3 seconds
```

**Observed:** HTTP 500 with "Internal server error" in response body.

---

### Failure 4 — OutOfMemoryError: Java heap space
**When:** Sustained 250+ QPS (if rate limiter disabled)  
**Why:** Each in-flight request holds ~50-200 KB of objects (request/response,
Jackson parse buffers, Hibernate session, stack frames). At 250 concurrent requests:
`250 * 150KB = ~37 MB` just for live request objects. With `-Xmx700m`:
- Spring context: ~150 MB
- Cached products: ~5 MB
- Metaspace: ~100 MB
- Thread stacks (50 threads × 512 KB): ~25 MB
- Live requests: ~37 MB per 250 concurrent
- **Available for normal ops: ~383 MB** — looks fine, but GC lag means old
  objects accumulate faster than GC can collect.

```log
java.lang.OutOfMemoryError: Java heap space
    at com.fasterxml.jackson.databind.ObjectMapper.writeValueAsString
```

**Then:** `-XX:+ExitOnOutOfMemoryError` kills the JVM. systemd restarts it in 10s.
Heap dump written to `/tmp/heap-dumps/heap-dump.hprof` for analysis.

---

### Failure 5 — GC Thrashing (high CPU, slow responses)
**When:** Heap > 80% full under load  
**Why:** G1GC triggers concurrent marking cycles. On 2 vCPU, GC competes with
request threads for CPU. You'll see:

```
[GC pause (G1 Evacuation Pause) 580ms]  ← 580ms STOP-THE-WORLD
```

All 50 Tomcat threads are FROZEN for 580ms. Incoming requests time out.
Latency goes from 5ms to 600ms+ instantly.

**Observed in logs:** `gc.log` shows pause times; Prometheus shows
`jvm_gc_pause_seconds_max` > 0.5.

---

### Failure 6 — CPU Throttle (t3 credit exhaustion)
**When:** Sustained high CPU for 30-60 minutes  
**Why:** t3.micro earns 6 CPU credits/hour and uses 5% baseline. At 200 QPS
the 2 vCPUs run at 70-80%, burning credits at ~14× the earn rate. After ~4 minutes
of sustained 200 QPS, credits exhaust and CPU is throttled to **10% of one core**.

**Observed:** Response times jump from 10ms to 800ms+ with no code changes.
Check in CloudWatch: `CPUCreditBalance` dropping to 0.

---

## 📊 Load Testing — k6 vs JMeter

### Recommendation: Use k6 ✅

| Feature              | k6                          | JMeter               |
|----------------------|-----------------------------|----------------------|
| Install              | Single binary               | Java + GUI           |
| RAM usage (runner)   | ~50 MB at 300 VU            | ~300-500 MB          |
| Scripting            | JavaScript (clean)          | XML (verbose)        |
| CI/CD integration    | Native                      | Plugin needed        |
| Metrics export       | InfluxDB/Prometheus built-in | Extra config         |
| Real-time dashboard  | `k6 cloud` or Grafana       | GUI only             |
| **Verdict**          | **Use this**                | Use if team knows it |

---

## 🔧 k6 Setup

### Install k6
```bash
# macOS
brew install k6

# Ubuntu/Debian
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
     --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
     | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6

# Windows (Chocolatey)
choco install k6
```

### Run overload test (ramp 50→300 QPS)
```bash
BASE_URL=http://<your-ec2-ip>:8080 k6 run k6/overload-test.js
```

### Run spike test (instant 300 QPS)
```bash
BASE_URL=http://<your-ec2-ip>:8080 k6 run k6/spike-test.js
```

### With Grafana dashboard (recommended)
```bash
# Start InfluxDB + Grafana locally
docker run -d -p 8086:8086 influxdb:1.8
docker run -d -p 3000:3000 grafana/grafana

# Run k6 streaming metrics to InfluxDB
k6 run --out influxdb=http://localhost:8086/k6 k6/overload-test.js

# Import k6 dashboard in Grafana: https://grafana.com/grafana/dashboards/2587
```

---

## 🔧 JMeter Setup (alternative)

### Install JMeter
```bash
# Download from https://jmeter.apache.org/download_jmeter.cgi
wget https://downloads.apache.org/jmeter/binaries/apache-jmeter-5.6.3.tgz
tar -xzf apache-jmeter-5.6.3.tgz

# Run the included test plan (headless)
./apache-jmeter-5.6.3/bin/jmeter \
  -n \
  -t jmeter/overload-test-plan.jmx \
  -l jmeter/results/results.jtl \
  -Jhost=<your-ec2-ip> \
  -e -o jmeter/results/html-report
```

Open `jmeter/results/html-report/index.html` for the full HTML report.

---

## 🩺 Monitoring During Overload

### Watch threads in real-time
```bash
# On the EC2 instance
watch -n 1 'curl -s http://localhost:8080/actuator/metrics/tomcat.threads.busy | python3 -c "import sys,json; d=json.load(sys.stdin); print(\"Busy threads:\", d[\"measurements\"][0][\"value\"])"'
```

### Watch heap usage
```bash
watch -n 1 'curl -s http://localhost:8080/actuator/metrics/jvm.memory.used | python3 -c "import sys,json; d=json.load(sys.stdin); print(\"Heap used:\", round(d[\"measurements\"][0][\"value\"]/1024/1024, 1), \"MB\")"'
```

### Capture heap dump during OOM (before the process dies)
```bash
jmap -dump:format=b,file=/tmp/heap-dumps/manual-dump.hprof $(cat /var/run/product-service.pid)
```

### GC log analysis
```bash
tail -f /var/log/product-service/gc.log | grep -E "pause|GC\)"
```

---

## 🛡️ How to Fix the Overload Issues

| Issue                    | Fix                                                                 |
|--------------------------|---------------------------------------------------------------------|
| 429 at 150 QPS           | Increase `limit-for-period` OR scale horizontally                  |
| Thread exhaustion        | Put ALB in front + run 2× t3.micro instances                      |
| DB pool saturation       | Use RDS with read replicas; increase `maximum-pool-size` carefully |
| OOM at 250+ QPS          | Move to t3.small (2 GB) or t3.medium (4 GB)                       |
| GC pauses                | Use `-XX:+UseZGC` (Java 17+) for sub-ms pauses                    |
| CPU credit exhaustion    | Use `t3a.small` or switch to `c6g.medium` for sustained load       |

---

## 🔗 Key Metrics to Watch

- `tomcat.threads.busy` — threads in use (alert at >40 of 50)
- `jvm.memory.used` — heap (alert at >600 MB)
- `resilience4j.ratelimiter.available.permissions` — permits left
- `hikaricp.connections.active` — DB connections in use
- `http.server.requests` — by status code (429, 500, 503)
- `jvm.gc.pause` — GC stop-the-world duration
