package az.yolcu.report;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportEventRepository extends JpaRepository<ReportEvent, Long> {
    List<ReportEvent> findByReportIdOrderByCreatedAtAscIdAsc(Long reportId);
}
