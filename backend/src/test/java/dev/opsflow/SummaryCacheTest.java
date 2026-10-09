package dev.opsflow;

import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static dev.opsflow.Models.*;

class SummaryCacheTest {
    @Test void redisFailureFallsBackToAuthoritativeDatabaseRead() {
        var redis=mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("Redis unavailable"));
        var cache=new SummaryCache(redis,new ObjectMapper(),true);
        var authoritative=new Summary(42,10,2,30,1);
        assertThat(cache.get(UUID.randomUUID(),()->authoritative)).isEqualTo(authoritative);
    }
}
