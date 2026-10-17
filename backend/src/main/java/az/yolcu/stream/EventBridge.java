package az.yolcu.stream;

import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

@Component
public class EventBridge {
    private final EventBus bus;

    public EventBridge(EventBus bus) {
        this.bus = bus;
    }

    // after commit, so a client that refetches on the event sees the new state; no tx (simulator) = immediately
    @TransactionalEventListener(fallbackExecution = true)
    public void on(StreamEvent e) {
        if (e.userId() != null) bus.publishToUser(e.userId(), e.name(), e.data());
        else if (e.role() != null) bus.publishToRole(e.role(), e.name(), e.data());
        else bus.publishAll(e.name(), e.data());
    }
}
