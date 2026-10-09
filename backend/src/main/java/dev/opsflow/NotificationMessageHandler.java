package dev.opsflow;

import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class NotificationMessageHandler {
    private static final Logger log=LoggerFactory.getLogger(NotificationMessageHandler.class);
    private final NotificationConsumer consumer;
    NotificationMessageHandler(NotificationConsumer consumer) { this.consumer=consumer; }
    void handle(ServiceBusReceivedMessageContext context) {
        try {
            consumer.consume(context.getMessage().getBody().toString());
        } catch (IllegalArgumentException invalid) {
            context.deadLetter(new DeadLetterOptions().setDeadLetterReason("InvalidEvent")
                .setDeadLetterErrorDescription("Invalid incident event payload"));
            return;
        } catch (Exception transientFailure) {
            log.warn("Notification processing failed; abandoning for broker retry",transientFailure);
            context.abandon();
            return;
        }
        context.complete(); // The transactional consumer returned only after the database committed.
    }
}
