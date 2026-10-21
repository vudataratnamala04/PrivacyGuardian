package com.privacyguardian.repo;

import com.privacyguardian.entity.FileRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FileRecordRepository extends JpaRepository<FileRecord, Long> {
    List<FileRecord> findByOwnerUsernameOrderByCreatedAtDesc(String username);
    long countByOwnerUsername(String username);
    long countByRiskLevel(String riskLevel);
    long countByOwnerUsernameAndRiskLevel(String username, String riskLevel);
    long countByOwnerUsernameAndProtectedFileTrue(String username);
    boolean existsByOwnerUsernameAndSha256(String username, String sha256);
}
