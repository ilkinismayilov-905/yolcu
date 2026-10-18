package az.yolcu.notification;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByUserId(Long userId, Pageable pageable);

    List<Notification> findByTargetAndIdGreaterThan(String target, Long id);

    @Query("select coalesce(max(n.id), 0) from Notification n")
    long maxId();
}
