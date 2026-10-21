package com.privacyguardian.controller;

import com.privacyguardian.repo.AuditLogRepository;
import com.privacyguardian.repo.FileRecordRepository;
import com.privacyguardian.repo.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DashboardController {
    private final FileRecordRepository files;
    private final UserRepository users;
    private final AuditLogRepository audits;
    public DashboardController(FileRecordRepository files, UserRepository users, AuditLogRepository audits) {
        this.files = files; this.users = users; this.audits = audits;
    }
    @GetMapping("/dashboard")
    public Map<String,Object> dashboard(Authentication auth) {
        String user = auth.getName();
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("totalFiles", files.countByOwnerUsername(user));
        out.put("highRisk", files.countByOwnerUsernameAndRiskLevel(user, "HIGH") + files.countByOwnerUsernameAndRiskLevel(user, "CRITICAL"));
        out.put("protected", files.countByOwnerUsernameAndProtectedFileTrue(user));
        return out;
    }
    @GetMapping("/logs")
    public Object logs(Authentication auth) { return audits.findTop100ByUsernameOrderByCreatedAtDesc(auth.getName()); }
    @GetMapping("/health")
    public Map<String,String> health() { return Map.of("status", "UP", "service", "Privacy Guardian"); }
    @GetMapping("/admin/overview")
    public Map<String,Object> adminOverview() {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("users", users.count()); out.put("files", files.count());
        out.put("highRisk", files.countByRiskLevel("HIGH") + files.countByRiskLevel("CRITICAL"));
        out.put("auditEvents", audits.count()); return out;
    }
    @GetMapping("/admin/logs")
    public Object adminLogs() { return audits.findTop100ByOrderByCreatedAtDesc(); }
}
