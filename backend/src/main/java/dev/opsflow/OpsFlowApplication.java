package dev.opsflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableScheduling
public class OpsFlowApplication {
    public static void main(String[] args) { SpringApplication.run(OpsFlowApplication.class, args); }
    @Bean InitializingBean validateTransport(@Value("${opsflow.notifications.transport}") String transport) {
        return () -> {
            if (!transport.equals("local") && !transport.equals("azure"))
                throw new IllegalArgumentException("NOTIFICATION_TRANSPORT must be local or azure");
        };
    }
}
