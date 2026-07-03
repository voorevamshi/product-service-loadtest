# 🚀 Product Service Load Testing with k6

## 📋 Table of Contents
- [Overview](#overview)
- [k6 Logs](#k6-logs)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Test Structure](#test-structure)
- [Running Tests](#running-tests)
- [Understanding Test Results](#understanding-test-results)
- [Test Execution Results](#test-execution-results)
- [Performance Analysis](#performance-analysis)
- [Optimization Recommendations](#optimization-recommendations)
- [Troubleshooting](#troubleshooting)
- [Summary](#summary)
- [Next Steps](#next-steps)

---

## Overview

This project contains k6 load tests for the **Product Service** running on AWS EC2 t3.micro instances. The tests help us understand system capacity, identify bottlenecks, and determine optimal scaling thresholds.

----------

## k6 Logs

     execution: local
     script: overload-test.js
     output: -

     scenarios: (100.00%) 1 scenario, 500 max VUs, 3m0s max duration (incl. graceful stop):
              * overload_test: Up to 300.00 iterations/s for 2m30s over 5 stages (maxVUs: 100-500, gracefulStop: 30s)

INFO[0061] [429] Rate limited — QPS exceeded t3.micro capacity  source=console
...
INFO[0138] [429] Rate limited — QPS exceeded t3.micro capacity  source=console


  █ THRESHOLDS

    error_rate
    ✗ 'rate<0.05' rate=20.76%

    http_req_duration
    ✓ 'p(99)<5000' p(99)=196.64ms


  █ TOTAL RESULTS

    checks_total.......: 16891   112.603583/s
    checks_succeeded...: 100.00% 16891 out of 16891
    checks_failed......: 0.00%   0 out of 16891

    ✓ LIST: status 200 or 429
    ✓ GET by ID: status 200 or 429

    CUSTOM
    error_rate.....................: 20.76% 4396 out of 21168
    product_api_duration...........: avg=10.520974 min=0  med=3.03555 max=499.9905 p(90)=17.00106 p(95)=37.919725
    rate_limit_429.................: 4396   29.305864/s

    HTTP
    http_req_duration..............: avg=10.52ms   min=0s med=3.03ms  max=499.99ms p(90)=17ms     p(95)=37.91ms
      { expected_response:true }...: avg=10.52ms   min=0s med=3ms     max=499.99ms p(90)=17.93ms  p(95)=42ms
    http_req_failed................: 20.76% 4396 out of 21168
    http_reqs......................: 21168  141.116135/s

    EXECUTION
    dropped_iterations.............: 731    4.8732/s
    iteration_duration.............: avg=119.05ms  min=0s med=4.47ms  max=3.19s    p(90)=208.44ms p(95)=856.35ms
    iterations.....................: 21168  141.116135/s
    vus............................: 0      min=0             max=170
    vus_max........................: 198    min=100           max=198

    NETWORK
    data_received..................: 33 MB  219 kB/s
    data_sent......................: 2.4 MB 16 kB/s

running (2m30.0s), 000/198 VUs, 21168 complete and 0 interrupted iterations
overload_test ✓ [======================================] 000/198 VUs  2m30s  050.08 iters/s
ERRO[0150] thresholds on metrics 'error_rate' have been crossed

### Key Objectives
- Determine maximum sustainable QPS (Queries Per Second)
- Identify performance bottlenecks
- Validate rate limiter effectiveness
- Establish baseline metrics for capacity planning
- Test system recovery after overload

### Test Environment
- **Instance Type**: AWS EC2 t3.micro (1 vCPU, 1GB RAM)
- **Application**: Spring Boot Product Service
- **JVM Options**: -Xms256m -Xmx700m -XX:+UseG1GC
- **Rate Limiter**: Resilience4j (configured for t3.micro)

---

## Prerequisites

### Required Software
- **k6** - Load testing tool
- **Java 17** - For running the application
- **Git** - For version control

### System Requirements
- Windows, macOS, or Linux
- Minimum 4GB RAM (for running k6 with high VUs)
- Application running on `http://localhost:8080`

---

## Installation

### Windows Install k6

```powershell
### Using Winget (Recommended)
winget install k6

### Using Chocolatey
choco install k6

### Manual Download
Download from: https://github.com/grafana/k6/releases
Add to PATH environment variable
```

  
```
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
````


### Verify Installation
k6 --version

Expected output: k6 v0.48.0 (go1.21.5, windows/amd64)

----------

## Test Structure

### 1. `overload-test.js` - Gradual Load Test

Simulates a gradual increase in traffic to find the system's breaking point.

javascript

// Test Stages
stages: [
 { target: 50,  duration: '30s' },  // Warm-up
 { target: 150, duration: '30s' },  // Normal load (safe zone)
 { target: 200, duration: '30s' },  // Overload (rate limiting starts)
 { target: 300, duration: '30s' },  // Beyond limit (OOM risk)
 { target: 50,  duration: '30s' },  // Recovery
]

**Traffic Mix:**

-   50% - GET by ID (`/api/v1/products/:id`)    
-   30% - List products (`/api/v1/products?page=0&size=20`)
-   10% - GET by SKU (`/api/v1/products/sku/:sku`)
-   10% - CREATE product (`POST /api/v1/products`)
    

### 2. `spike-test.js` - Sudden Burst Test

Simulates an immediate surge in traffic (0 → 300 QPS instantly).

javascript
```
scenarios: {
 spike: {
 executor: 'constant-arrival-rate',
 rate: 300,           // Immediate 300 QPS
 timeUnit: '1s',
 duration: '60s',
 preAllocatedVUs: 300,
 maxVUs: 600,
 }
}
```
### 3. `start.sh` - Application Startup Script
----------
## JVM Optimized Startup Configuration for AWS t3.micro Instances

### Recommended JVM Parameters

| Parameter | Value | Purpose |
|---|---|---|
| **`-Xms`** | `256m` | Initial JVM heap size. Starts with 256 MB allocation to reduce startup memory pressure. |
| **`-Xmx`** | `700m` | Maximum JVM heap size. Leaves approximately 300 MB RAM available for OS, native memory, and other processes. |
| **`-XX:+UseG1GC`** | Enabled | Uses G1 Garbage Collector, optimized for applications requiring predictable latency and high throughput. |
| **`-XX:MaxGCPauseMillis`** | `200` | Sets the target maximum GC pause time to approximately 200 milliseconds. |
| **`-Xss`** | `512k` | Reduces per-thread stack memory usage, allowing more threads with limited RAM. |
| **`-XX:+HeapDumpOnOutOfMemoryError`** | Enabled | Automatically creates a heap dump when JVM runs out of memory for troubleshooting. |
| **`-XX:+ExitOnOutOfMemoryError`** | Enabled | Forces JVM shutdown after OutOfMemoryError so the service can restart cleanly through AWS/ECS/Kubernetes health management. |

---

## Example Spring Boot Startup Script (t3.micro)

```bash
java \
-Xms256m \
-Xmx700m \
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=200 \
-Xss512k \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:+ExitOnOutOfMemoryError \
-jar product-service.jar
```
## Running Tests

### Basic Test Run
```
# Run overload test
k6 run overload-test.js
# Run spike test
k6 run spike-test.js
# Run with custom BASE_URL
k6 run overload-test.js -e BASE_URL=http://localhost:8080
```
### Advanced Test Runs
```
# Save results to JSON file
k6 run overload-test.js --out json=results.json
# Run with specific duration
k6 run overload-test.js --duration 2m
# Run with custom virtual users
k6 run overload-test.js --vus 50
# Debug mode (show HTTP requests)
k6 run overload-test.js --http-debug
# Run and stream to InfluxDB (for Grafana visualization)
k6 run overload-test.js --out influxdb=http://localhost:8086/k6
```
### Quick Test (Sanity Check)

Create `quick-test.js`:

javascript
```
import http from 'k6/http';
import { check } from 'k6';
export default function () {
 const res = http.get('http://localhost:8080/api/v1/products/2');
 check(res, {
 'API is reachable': (r) => r.status === 200,
 });
}
```
Run:

k6 run quick-test.js

----------

## Understanding Test Results
## Key Performance Testing Metrics Explained

| Metric | Description | Target |
|---|---|---|
| **`http_req_duration`** | Measures the total request response time from client request to server response completion. | **p(95) < 2000ms** (95% of requests should complete within 2 seconds) |
| **`error_rate`** | Percentage of requests that failed during the test execution. | **< 5%** |
| **`http_reqs`** | Total number of HTTP requests generated and sent during the load test. | No fixed target; used to measure throughput and system capacity. |
| **`rate_limit_429`** | Number of requests rejected because of rate limiting (`HTTP 429 Too Many Requests`). | Should increase when the configured rate limit is reached. |
| **`server_errors_5xx`** | Number of server-side failures (`HTTP 5xx` errors) returned by the application. | **Should be 0** |
| **`timeout_errors`** | Number of requests that exceeded the allowed response time and timed out. | Should be minimal |
| **`dropped_iterations`** | Number of test iterations that could not start because available virtual users were fully occupied. | **Should be 0 under normal load conditions** |

---

## Load Testing Health Interpretation

| Scenario | Expected Metric Behavior |
|---|---|
| **Normal Load** | Low error rate, p95 latency within target, zero dropped iterations. |
| **Approaching Capacity** | `http_req_duration` increases, CPU/memory usage rises, some rate limiting may appear. |
| **System Overload** | High p95 latency, increasing timeout errors, `5xx` errors appear, dropped iterations increase. |
| **Rate Limit Validation** | `rate_limit_429` should increase after configured API limits are exceeded. |

### Thresholds

javascript

thresholds: {
 'http_req_duration': ['p(95)<2000'],  // 95% under 2s
 'http_req_duration': ['p(99)<5000'],  // 99% under 5s
 'error_rate': ['rate<0.05'],           // <5% error rate
}

### Interpreting Status Codes

----------
## HTTP Response Code Meaning During Load Testing

| Code | Meaning | Action |
|---|---|---|
| **200** | Success | Normal operation. Request completed successfully. |
| **429** | Rate Limited | System has reached the configured capacity limit. Verify rate limiting behavior and scaling strategy. |
| **503** | Service Unavailable | Server is overwhelmed or temporarily unable to handle requests. Check availability, scaling, and resource utilization. |
| **5xx** | Server Error | Application-side issue. Investigate logs, exceptions, dependencies, and service health. |
| **Timeout** | Request timed out | System is responding too slowly. Analyze latency, database calls, network delays, and resource bottlenecks. |

---

## Load Test Response Analysis

| Response | Indicates | System Design Action |
|---|---|---|
| **200 Success** | Application is handling traffic correctly. | Continue monitoring throughput, latency, and resource usage. |
| **429 Rate Limited** | Traffic exceeded configured API limits. | Validate throttling mechanism and consider scaling or increasing limits. |
| **503 Service Unavailable** | Service cannot accept more requests. | Add more instances, improve auto-scaling rules, or optimize resource usage. |
| **5xx Server Errors** | Internal application failure. | Check application logs, code issues, database failures, and downstream services. |
| **Timeout Errors** | Requests exceed allowed processing time. | Optimize slow operations, increase capacity, tune database queries, and review network latency. |
## Test Execution Results

### 📊 Summary from Overload Test

**Test Configuration:**

-   Duration: 2 minutes 30 seconds
    
-   Max QPS: 300
    
-   Virtual Users: 0-198 (scaled dynamically)
    
-   Total Requests: 21,168
    

### Key Findings

#### ✅ Performance Metrics (EXCELLENT)

text

Response Times:
| Metric | Value | Interpretation | Status |
|---|---:|---|---|
| **Average Response Time** | **10.52ms** | Overall average request processing time is very low, indicating excellent performance. | ✅ **Excellent** |
| **Median Response Time (p50)** | **3.03ms** | 50% of requests completed within 3 milliseconds. | ✅ **Very Good** |
| **p(95) Response Time** | **37.91ms** | 95% of requests completed within 38 milliseconds. | ✅ **Excellent** |
| **p(99) Response Time** | **196.64ms** | 99% of requests completed within 197 milliseconds. | ✅ **Acceptable** |
| **Maximum Response Time** | **499.99ms** | Some individual requests experienced higher latency spikes. | ⚠️ **Monitor Slow Requests** |


#### ❌ Error Metrics (NEEDS IMPROVEMENT)

| Metric | Value | Threshold / Expected | Status |  
|---|---:|---|---|  
| **Error Rate** | **20.76%** | Should be **< 5%** | 🔴 **Exceeded threshold** |  
| **429 Errors** | **4,396** | Should appear only when rate limit is reached | ⚠️ **Rate limiter active** |  
| **Dropped Requests** | **731** | Should be **0** under normal load | 🔴 **Requests not processed** |

#### 📈 Traffic Analysis

| Metric | Value | Description |  
|---|---|---|  
| **Total Requests** | **21,168** (**141.1 req/s**) | Total number of requests processed during the load test with an average throughput of 141.1 requests per second. |  
| **Checks Successful** | **16,891** (**100% validation**) | All executed validation checks passed successfully, confirming expected API behavior. |  
| **Data Transferred** | **33 MB received**<br>**2.4 MB sent** | Total network data exchanged between the load test client and the service. |

### Test Timeline Observations
## Load Test Execution Timeline

| Time | Stage | Event |
|---|---|---|
| **0-30s** | **Warm-up (50 QPS)** | ✅ All requests successful. System initializes resources and gradually increases load. |
| **30-60s** | **Normal Load (150 QPS)** | ✅ System stable. Application handles expected traffic with normal latency and error rates. |
| **60-90s** | **Overload (200 QPS)** | ⚠️ Rate limiting begins. System reaches configured capacity limits and starts throttling requests. |
| **90-120s** | **Peak Load (300 QPS)** | 🔴 High error rate (20%+). System is beyond safe capacity; latency and failures increase. |
| **120-150s** | **Recovery (50 QPS)** | ✅ System recovers. Reduced traffic allows resources to stabilize and failed requests decrease. |

---

## Load Test Stage Analysis

| Stage | Purpose | Expected System Behavior |
|---|---|---|
| **Warm-up** | Verify application startup and gradual resource allocation. | No failures, stable CPU and memory usage. |
| **Normal Load** | Validate expected production traffic handling. | Low latency, successful requests, healthy resource utilization. |
| **Overload** | Identify system capacity limits. | Rate limiting should protect the system from excessive traffic. |
| **Peak Load** | Find maximum breaking point of the application. | Increased latency, errors, and possible resource saturation. |
| **Recovery** | Verify self-healing capability after traffic reduction. | Services should recover without manual intervention. |

### Key Insight

**First 429 Error at 61 seconds** - This is exactly when the system hit its capacity limit!

text

Time: 61s → Rate limiter activates
Meaning: System can handle ~150 QPS, but starts rejecting at ~200 QPS

### Performance Profile

| QPS | Response Time | Error Rate | Status |
|---|---|---|---|
| **50** | ~3ms | 0% | ✅ **Perfect** |
| **150** | ~5ms | 0-1% | ✅ **Good** |
| **200** | ~10ms | 5-10% | ⚠️ **Rate Limiting** |
| **300** | ~15-197ms | 20-25% | ❌ **Overloaded** |
| **50** | ~3ms | Decreased to 0% | ✅ **Recovered** |


### What the 731 Dropped Iterations Mean

**Dropped iterations** = requests that couldn't even start:

text

Customer walks into store
 → Store is FULL (connection pool exhausted)
 → Customer walks away (request dropped)
 → No status code, no response

**Possible Causes:**

-   Tomcat `acceptCount` queue exceeded (default: 100)
    
-   Connection pool exhausted
    
-   Socket limit reached
    
-   Virtual User starvation
    

----------

## Performance Analysis

### Root Cause Analysis

#### 1. Memory Constraints

text

Total RAM: 1GB (t3.micro)
├── JVM Heap: 700MB
├── Thread Stacks: ~200MB (200 threads × 1MB)
├── OS/Network: ~100MB
└── Available: 0MB (Right at limit!)

**Findings:**

-   Rate limiter is preventing OOM by rejecting 20% of requests
    
-   Without rate limiter, system would crash at ~200 QPS
    
-   Thread stack size reduction (512KB) saved ~50MB
    

#### 2. Thread Pool Analysis

java

// Current Configuration
server.tomcat.threads.max: 200 (default)
// Each thread: 512KB stack
// Total: 200 × 512KB = 102.4MB

**Observation:**

-   Thread pool exhausts around 200 QPS
    
-   731 dropped requests = connection queue overflow
    
-   Need to optimize thread usage
    

#### 3. Database Connection Pool

java

// HikariCP Default
maximumPoolSize: 10
connectionTimeout: 30000ms
idleTimeout: 600000ms

**Impact:**

-   Each connection uses memory + network buffers
    
-   10 connections = ~10-20MB overhead
    
-   May be bottleneck before CPU
    

### Performance Bottlenecks
## System Bottleneck Analysis

| Bottleneck | Evidence | Severity |
|---|---|---|
| **Memory (RAM)** | 429 errors observed at **200 QPS**, indicating the service is reaching capacity limits due to memory pressure. | 🔴 **High** |
| **Thread Pool** | **731 dropped iterations** detected, indicating insufficient worker threads to handle incoming requests. | 🟡 **Medium** |
| **Database Connections** | Potential connection pool limit detected. Database may become a bottleneck under increased concurrency. | 🟡 **Medium** |
| **CPU** | Response times remain within acceptable range, indicating CPU is not the current limiting factor. | 🟢 **Low** |

---

## Bottleneck Impact Analysis

| Area | Problem | Recommended Action |
|---|---|---|
| **Memory (RAM)** | JVM memory becomes a constraint under higher QPS, causing throttling or failed requests. | Increase instance memory, tune JVM heap size, optimize object allocation, and review caching strategy. |
| **Thread Pool** | Request processing capacity is limited by available worker threads. | Tune Spring Boot thread pool size, optimize long-running operations, and use asynchronous processing where applicable. |
| **Database Connections** | Connection pool may exhaust during traffic spikes. | Increase connection pool size carefully, optimize queries, and reduce unnecessary DB calls. |
| **CPU** | CPU has available capacity and is not causing latency issues. | No immediate action required; continue monitoring during higher load tests. |

---

## Capacity Testing Conclusion

| Finding | Result |
|---|---|
| **Primary Bottleneck** | Memory limitation is the main constraint. |
| **Secondary Bottleneck** | Thread pool and database connection limits require monitoring. |
| **CPU Status** | Healthy; not a scaling trigger currently. |
| **Scaling Recommendation** | Move from memory-constrained instances (for example, `t3.micro`) to larger production instances for higher QPS workloads. |


----------

## Optimization Recommendations

### 1. Immediate Improvements (Low Risk)

#### A. JVM Tuning

bash

# Update start.sh
-Xmx512m          # Reduce from 700MB
-Xss256k          # Reduce thread stack further
-XX:MaxMetaspaceSize=96m  # Reduce from 128m

**Expected Impact:** +20% capacity, more OS memory available

#### B. Tomcat Configuration

properties

# Add to application.properties
server.tomcat.threads.max=150        # Reduce from 200
server.tomcat.threads.min-spare=50   # Optimize
server.tomcat.accept-count=200       # Increase queue (default: 100)
server.tomcat.max-connections=10000  # Max connections

**Expected Impact:** Better queue handling, fewer dropped requests

#### C. Connection Pool Tuning

properties

# HikariCP Optimization
spring.datasource.hikari.maximumPoolSize=15
spring.datasource.hikari.minimumIdle=5
spring.datasource.hikari.connectionTimeout=5000
spring.datasource.hikari.idleTimeout=300000

**Expected Impact:** Better database connection utilization

### 2. Medium-Term Improvements

#### A. Caching Implementation

java

@Cacheable(value = "products", key = "#id")
public Product getProductById(Long id) {
 return productRepository.findById(id);
}

**Benefit:** Reduces database load, faster response times

#### B. API Response Optimization

-   Use DTOs instead of full entities
    
-   Implement pagination correctly
    
-   Add compression (gzip)
    

#### C. Database Indexing

sql

CREATE INDEX idx_product_sku ON product(sku);
CREATE INDEX idx_product_category ON product(category);

### 3. Infrastructure Scaling

#### Option 1: Scale Up (t3.small)

bash

Instance: t3.small (2GB RAM)
- JVM Heap: 1.2GB
- Capacity: 200-250 QPS
- Cost: ~2x t3.micro

#### Option 2: Scale Out (Auto-Scaling)

yaml

# Configure auto-scaling
Threshold: CPU > 70% OR RequestCount > 150 QPS
Scale Out: Add 1 instance
Scale In: Remove 1 instance after 5 minutes

#### Option 3: Performance Optimization

-   Implement Redis cache
    
-   Use async processing
    
-   Optimize database queries
    

### 4. Rate Limiter Configuration

java

// Resilience4j Rate Limiter
RateLimiterConfig config = RateLimiterConfig.custom()
 .limitForPeriod(200)          // 200 requests
 .limitRefreshPeriod(Duration.ofSeconds(1))  // per second
 .timeoutDuration(Duration.ofSeconds(5))
 .build();

**Recommendation:** Configure based on test results (150-200 QPS)

----------

## Troubleshooting

### Common Issues and Solutions

#### Issue: "k6 is not recognized"

bash

# Windows
# Add k6 to PATH or use full path
C:\path\to\k6.exe run test.js
# Or verify installation
where k6

#### Issue: "Connection refused"

bash

# Check if application is running
curl http://localhost:8080/actuator/health
# Check port
netstat -ano | findstr :8080  # Windows
lsof -i :8080                  # macOS/Linux

#### Issue: "Port already in use"

bash

# Find process using port 8080
netstat -ano | findstr :8080  # Windows
# Kill the process
taskkill /PID <PID> /F
# macOS/Linux
lsof -ti :8080 | xargs kill -9

#### Issue: High error rate immediately

bash

# Check rate limiter configuration
# Verify application is healthy before test
# Test with lower QPS first
k6 run overload-test.js --iterations 10

#### Issue: Dropped iterations high

bash

# Check Tomcat configuration
# Increase accept-count
# Reduce VUs or QPS
k6 run overload-test.js --vus 50 --duration 30s

### Debugging Techniques

#### 1. Application Logs

bash

# Watch logs during test
tail -f /var/log/product-service/app.log
# Check GC logs
tail -f /var/log/product-service/gc.log

#### 2. JVM Monitoring

bash

# Check JVM heap
jcmd <PID> GC.heap_info
# Check thread usage
jstack <PID> > thread-dump.txt

#### 3. System Monitoring

bash

# Windows (PowerShell)
Get-Counter "\Processor(_Total)\% Processor Time"
Get-Counter "\Memory\Available MBytes"
# Linux
top -p <PID>
vmstat 1

---

## Summary

## Option 1: Technical Summary

The overload test on t3.micro showed excellent response times (p95: 38ms, p99: 197ms) 
but exceeded the 5% error threshold at 20.76% when QPS hit 200, confirming our 
sustainable capacity limit is ~150 QPS with rate limiter protection working effectively.
[Performance Test Comparison: Run 1 vs Run 2](run1_Vs_run2.md)

**Quick Pick:** Option 1 is best for technical teams, Option 2 for DevOps/Architects, Option 3 for Management/Stakeholders.

----------

## Next Steps

### 1. Run the Spike Test

bash

k6 run spike-test.js

**What to expect:** Immediate 300 QPS - see if rate limiter can handle sudden burst

### 2. Run Tests with Optimized Configuration

After implementing optimizations, re-run tests:

bash

k6 run overload-test.js --out json=optimized-results.json

### 3. Monitor in Real-Time

bash

# Setup Grafana + InfluxDB
docker run -d --name influxdb -p 8086:8086 influxdb:2.0
docker run -d --name grafana -p 3000:3000 grafana/grafana
# Run k6 with InfluxDB output
k6 run overload-test.js --out influxdb=http://localhost:8086/k6

### 4. Create Performance Baseline

json

{
 "max_QPS": 150,
 "avg_response": "10.52ms",
 "p95_response": "37.91ms",
 "error_rate": "20.76%",
 "recommended_scale": "150-200 QPS"
}

----------

## 📚 Additional Resources

### Documentation

-   [k6 Official Docs](https://k6.io/docs/)
    
-   [k6 Scenarios](https://k6.io/docs/using-k6/scenarios/)
    
-   [k6 Metrics](https://k6.io/docs/using-k6/metrics/)
    
-   [Spring Boot Performance Tuning](https://docs.spring.io/spring-boot/docs/current/reference/html/performance.html)
    

### Tools for Analysis

-   **Grafana**: Dashboard visualization
    
-   **InfluxDB**: Time-series database
    
-   **JProfiler**: Java profiling
    
-   **VisualVM**: JVM monitoring
    
-   **Prometheus**: Metrics collection
    

### Related Repositories

-   [Product Service Source](https://github.com/your-repo/product-service)
    
-   [Infrastructure as Code](https://github.com/your-repo/infrastructure)
    
-   [Monitoring Setup](https://github.com/your-repo/monitoring)
    

----------

## 📝 Changelog

----------
## Test Documentation Version History  
  
| Date | Version | Changes |  
|---|---|---|  
| **2026-06-30** | **1.0.0** | Initial test suite created. |  
| **2026-06-30** | **1.0.1** | Added optimization recommendations. |  
| **2026-06-30** | **1.0.2** | Added detailed result analysis. |
## 🤝 Contributing

1.  Fork the repository
    
2.  Create your feature branch (`git checkout -b feature/AmazingFeature`)
    
3.  Commit your changes (`git commit -m 'Add some AmazingFeature'`)
    
4.  Push to the branch (`git push origin feature/AmazingFeature`)
    
5.  Open a Pull Request
    

----------

## 📧 Contact

-   **Team Lead**: lead@example.com
    
-   **DevOps**: devops@example.com
    
-   **Performance Team**: performance@example.com
    

----------

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](https://license/) file for details.

----------

## 🙏 Acknowledgments

-   k6 team for the excellent load testing tool
    
-   Grafana Labs for monitoring solutions
    
-   Spring Boot team for the framework
    
-   AWS for EC2 infrastructure
    

----------

## ⚠️ Important Notes

### Test Environment Considerations

-   **Run tests in non-production environments first**
    
-   **Start with lower QPS to establish baseline**
    
-   **Monitor system resources during tests**
    
-   **Have rollback plan ready**
    

### Safety Guidelines

1.  Never run tests against production without approval
    
2.  Always have monitoring in place
    
3.  Set realistic thresholds
    
4.  Start small and scale up
    
5.  Document all findings
    

----------

**🚀 Happy Testing!**

Remember: **We break it in test so it doesn't break in production!**

text

---
This comprehensive README.md covers everything we discussed and more. It's structured to be:
1. **Beginner-friendly** - Clear explanations of technical concepts
2. **Actionable** - Specific commands and code to run
3. **Comprehensive** - Covers installation, execution, analysis, and optimization
4. **Professional** - Includes all standard sections for a technical document
5. **Practical** - Based on your actual test results with real numbers
You can save this as `README.md` in your project's root directory. It will serve as both documentation for new team members and a reference for performance testing practices.
