package dev.opsflow;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name="opsflow.notifications.dispatch-enabled",havingValue="true",matchIfMissing=true)
class OutboxDispatcher {
    private static final Logger log=LoggerFactory.getLogger(OutboxDispatcher.class);
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final NotificationConsumer consumer;
    private final ObjectProvider<AzureBus> azure;
    OutboxDispatcher(JdbcTemplate db,TransactionTemplate tx,NotificationConsumer consumer,ObjectProvider<AzureBus> azure) {
        this.db=db;this.tx=tx;this.consumer=consumer;this.azure=azure;
    }
    // Spring's scheduling thread is independent of HTTP requests. Row locks support multiple replicas.
    @Scheduled(fixedDelay=1000)
    void dispatch() {
        for (int i=0;i<50;i++) {
            UUID[] attempted={null};
            try {
                Boolean processed=tx.execute(status -> {
                    var rows=db.queryForList("SELECT id,payload::text FROM outbox WHERE published_at IS NULL AND dead_at IS NULL AND available_at<=now() ORDER BY available_at LIMIT 1 FOR UPDATE SKIP LOCKED");
                    if (rows.isEmpty()) return false;
                    UUID id=(UUID)rows.getFirst().get("id"); attempted[0]=id;
                    String payload=(String)rows.getFirst().get("payload");
                    AzureBus bus=azure.getIfAvailable();
                    if (bus==null) consumer.consume(payload); else bus.send(id.toString(),payload);
                    db.update("UPDATE outbox SET published_at=now(),last_error=NULL WHERE id=?",id);
                    return true;
                });
                if (!Boolean.TRUE.equals(processed)) break;
            } catch (Exception failure) {
                if (attempted[0]!=null) {
                    // Persist failures outside the rolled-back delivery transaction; capped exponential backoff.
                    db.update("UPDATE outbox SET attempts=attempts+1,last_error=?,available_at=now()+make_interval(secs=>LEAST(300,power(2,attempts+1)::int)),dead_at=CASE WHEN attempts+1>=8 THEN now() ELSE NULL END WHERE id=? AND published_at IS NULL AND dead_at IS NULL",
                        failure.getClass().getSimpleName(),attempted[0]);
                    log.warn("Outbox delivery {} failed; persisted retry state",attempted[0]);
                } else log.warn("Unable to read outbox",failure);
                break;
            }
        }
    }
}
