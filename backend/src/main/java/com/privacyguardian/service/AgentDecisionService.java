package com.privacyguardian.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Autonomous privacy decision engine.
 * It turns scan evidence into an action plan and can be executed by FileService.
 * This is a deterministic local agent (no external AI service or API key required).
 */
@Service
public class AgentDecisionService {
    public Decision decide(ScannerService.ScanResult scan) {
        List<String> d = scan.detections() == null ? List.of() : scan.detections();
        boolean criticalEvidence = hasAny(d, "Aadhaar-like number", "PAN-like number", "Card-like number", "Secret/API key");
        boolean strongIdentity = hasAny(d, "Phone number", "Email address", "Address", "Date of birth", "Student/Employee ID", "Sensitive name");
        boolean qr = scan.qrDetected();
        boolean face = scan.faceDetected();

        if ("CRITICAL".equals(scan.level()) || criticalEvidence) {
            return new Decision("AUTO_PROTECT", face ? "BLACKOUT+FACE_BLUR" : "BLACKOUT", 0.97,
                    face
                            ? "Critical identity or credential evidence detected. Agent selected permanent blackout for text and face blur for biometric privacy."
                            : "Critical identity or credential evidence detected. Agent selected permanent blackout to minimize leakage risk.");
        }
        if ("HIGH".equals(scan.level()) || strongIdentity || qr || face) {
            return new Decision("AUTO_PROTECT", face ? "PIXELATE+FACE_BLUR" : "PIXELATE", 0.93,
                    face
                            ? "High-risk personal information and a face were detected. Agent selected pixelation for sensitive text and face blur for biometric privacy."
                            : "High-risk personal information detected. Agent selected pixelation for automatic privacy protection while retaining document context.");
        }
        if ("MEDIUM".equals(scan.level())) {
            return new Decision("REVIEW_RECOMMENDED", face ? "MASK+FACE_BLUR" : "MASK", 0.82,
                    "Moderate privacy exposure detected. Agent recommends masking before sharing the file.");
        }
        return new Decision("NO_ACTION", "NONE", 0.88,
                "No significant privacy leakage indicators were detected. Agent recommends keeping the original unchanged.");
    }

    private boolean hasAny(List<String> values, String... wanted) {
        for (String v : values) {
            String x = v == null ? "" : v.toLowerCase(Locale.ROOT);
            for (String w : wanted) if (x.equals(w.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public record Decision(String action, String recommendedMethod, double confidence, String reason) {}
}
