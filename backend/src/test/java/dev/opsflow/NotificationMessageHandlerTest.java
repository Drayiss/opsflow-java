package dev.opsflow;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.*;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class NotificationMessageHandlerTest {
    NotificationConsumer consumer=mock(NotificationConsumer.class);
    ServiceBusReceivedMessageContext context=mock(ServiceBusReceivedMessageContext.class);
    NotificationMessageHandler handler=new NotificationMessageHandler(consumer);
    @BeforeEach void setup() {
        ServiceBusReceivedMessage message=mock(ServiceBusReceivedMessage.class);
        when(message.getBody()).thenReturn(BinaryData.fromString("payload"));
        when(context.getMessage()).thenReturn(message);
    }
    @Test void acknowledgesOnlyAfterConsumerReturns() {
        handler.handle(context);
        var order=inOrder(consumer,context);
        order.verify(consumer).consume("payload");order.verify(context).complete();
        verify(context,never()).abandon();verify(context,never()).deadLetter(any(DeadLetterOptions.class));
    }
    @Test void transientFailureIsAbandonedForRedelivery() {
        doThrow(new IllegalStateException("Database unavailable")).when(consumer).consume("payload");
        handler.handle(context);
        verify(context).abandon();verify(context,never()).complete();verify(context,never()).deadLetter(any(DeadLetterOptions.class));
    }
    @Test void malformedMessageGoesDirectlyToDeadLetterQueue() {
        doThrow(new IllegalArgumentException("Malformed event")).when(consumer).consume("payload");
        handler.handle(context);
        verify(context).deadLetter(any(DeadLetterOptions.class));verify(context,never()).complete();verify(context,never()).abandon();
    }
}
