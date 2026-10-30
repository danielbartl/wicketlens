package example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DemoSpringConfig {
    @Bean(name = {"orderService", "orders"})
    public OrderService orderService() {
        return () -> "Example orders";
    }

    @Bean
    public FinalAuditService auditService() {
        return new FinalAuditService();
    }

    @Bean
    public String message() {
        return "Example message";
    }
}
