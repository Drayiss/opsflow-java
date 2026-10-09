package dev.opsflow;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import static dev.opsflow.Models.*;

@Component
class SummaryCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final boolean enabled;
    SummaryCache(StringRedisTemplate redis,ObjectMapper json,@Value("${opsflow.cache-enabled}") boolean enabled) {
        this.redis=redis; this.json=json; this.enabled=enabled;
    }
    Summary get(UUID tenant,Supplier<Summary> load) {
        if (!enabled) return load.get();
        String key="opsflow:summary:"+tenant;
        try {
            String value=redis.opsForValue().get(key);
            if (value!=null) return json.readValue(value,Summary.class);
        } catch (Exception ignored) { /* Redis is optional; authorization never uses cached data. */ }
        Summary summary=load.get();
        try { redis.opsForValue().set(key,json.writeValueAsString(summary),Duration.ofSeconds(15)); }
        catch (Exception ignored) { }
        return summary;
    }
    void evict(UUID tenant) {
        if (enabled) try { redis.delete("opsflow:summary:"+tenant); } catch (Exception ignored) { }
    }
}
