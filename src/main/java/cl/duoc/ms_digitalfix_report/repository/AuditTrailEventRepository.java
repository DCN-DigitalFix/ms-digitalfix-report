package cl.duoc.ms_digitalfix_report.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.duoc.ms_digitalfix_report.model.AuditTrailEventEntity;

public interface AuditTrailEventRepository extends JpaRepository<AuditTrailEventEntity, String> {
}
