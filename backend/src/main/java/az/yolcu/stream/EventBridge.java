package az.yolcu.stream;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class EventBridge {
    private final EventBus bus;

    public EventBridge(EventBus bus) {
        this.bus = bus;
    }

    @EventListener
    public void on(StreamEvent e) {
        if (e.userId() != null) bus.publishToUser(e.userId(), e.name(), e.data());
        else if (e.role() != null) bus.publishToRole(e.role(), e.name(), e.data());
        else bus.publishAll(e.name(), e.data());
    }
}
