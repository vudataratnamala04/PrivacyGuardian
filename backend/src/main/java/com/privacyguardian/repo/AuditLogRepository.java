package com.privacyguardian.repo;

import com.privacyguardian.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop100ByUsernameOrderByCreatedAtDesc(String username);
    List<AuditLog> findTop100ByOrderByCreatedAtDesc();
}
