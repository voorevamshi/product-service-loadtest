### Why Caffeine Breaks with Autoscaling


Caffeine is an **in-process, in-memory cache** — it lives inside each JVM instance separately.

```
Request 1 → Instance A (cache has product-1 = ₹999)   ✅ correct
Request 2 → Instance B (cache is EMPTY, hits DB)       ✅ correct but slow  
Request 3 → Instance A (someone updated price to ₹899 on Instance B)
                       (cache still says ₹999)         ❌ STALE DATA
```

Concrete problems:

-   **Stale reads** — a product updated on one instance stays cached with old data on all others until TTL expires (60 seconds in your config)
-   **Cache inconsistency** — `@CacheEvict` on PUT/DELETE only evicts from the instance that handled that request, other instances keep the old value
-   **Scale-in data loss** — when an instance is terminated, its entire cache is gone, cold-start hit on all requests to new instances
