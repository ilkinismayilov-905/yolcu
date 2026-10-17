package az.yolcu.report;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ReportRepository extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {
    Optional<Report> findByCode(String code);

    Optional<Report> findByCodeAndUserId(String code, Long userId);

    Page<Report> findByUserId(Long userId, Pageable pageable);

    long countByUserIdAndCreatedAtAfter(Long userId, OffsetDateTime after);

    long countByCreatedAtGreaterThanEqual(OffsetDateTime from);

    long countByStatusIn(Collection<ReportStatus> statuses);

    long countByStatusAndForwardedAtBefore(ReportStatus status, OffsetDateTime before);

    @Query(value = "select coalesce(max(cast(substring(code, 3) as int)), 2000) from reports where code like 'A-%'",
            nativeQuery = true)
    int maxCodeNumber();
}
