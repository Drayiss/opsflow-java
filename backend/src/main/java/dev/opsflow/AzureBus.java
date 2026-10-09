package dev.opsflow;

import java.time.Duration;
import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="opsflow.notifications.transport",havingValue="azure")
class AzureBus {
    private static final Logger log=LoggerFactory.getLogger(AzureBus.class);
    private final ServiceBusSenderClient sender;
    private final ServiceBusProcessorClient processor;
    AzureBus(NotificationMessageHandler handler,
        @Value("${opsflow.notifications.namespace}") String namespace,
        @Value("${opsflow.notifications.connection-string}") String connection,
        @Value("${opsflow.notifications.queue}") String queue) {
        var builder=new ServiceBusClientBuilder().retryOptions(new AmqpRetryOptions()
            .setMaxRetries(3).setDelay(Duration.ofSeconds(1)).setMaxDelay(Duration.ofSeconds(15)));
        if (!connection.isBlank()) builder.connectionString(connection);
        else {
            if (namespace.isBlank()) throw new IllegalArgumentException("SERVICEBUS_NAMESPACE is required for Azure transport");
            builder.credential(namespace,new DefaultAzureCredentialBuilder().build());
        }
        sender=builder.sender().queueName(queue).buildClient();
        processor=builder.processor().queueName(queue).disableAutoComplete().maxConcurrentCalls(4)
            .maxAutoLockRenewDuration(Duration.ofMinutes(5))
            .processMessage(handler::handle).processError(error -> log.warn("Service Bus processor error: {}",error.getErrorSource(),error.getException()))
            .buildProcessorClient();
    }
    void send(String id,String payload) {
        sender.sendMessage(new ServiceBusMessage(payload).setMessageId(id).setContentType("application/json"));
    }
    @PostConstruct void start() { processor.start(); }
    @PreDestroy void close() { processor.close();sender.close(); }
}
