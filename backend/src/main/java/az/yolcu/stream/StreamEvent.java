package az.yolcu.stream;

import az.yolcu.user.Role;

/** Domain event for SSE. Publish via ApplicationEventPublisher; role/userId null = everyone. */
public record StreamEvent(String name, Object data, Role role, Long userId) {
    public static StreamEvent all(String name, Object data) { return new StreamEvent(name, data, null, null); }

    public static StreamEvent toRole(Role role, String name, Object data) { return new StreamEvent(name, data, role, null); }

    public static StreamEvent toUser(long userId, String name, Object data) { return new StreamEvent(name, data, null, userId); }
}
