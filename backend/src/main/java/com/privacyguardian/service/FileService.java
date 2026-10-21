package com.privacyguardian.service;

import com.privacyguardian.entity.FileRecord;
import com.privacyguardian.entity.User;
import com.privacyguardian.repo.FileRecordRepository;
import com.privacyguardian.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

@Service
public class FileService {
    private final FileRecordRepository files;
    private final UserRepository users;
    private final ScannerService scanner;
    private final ProtectionService protection;
    private final AuditService audit;
    private final AgentDecisionService agent;
    private final Path root;

    public FileService(FileRecordRepository files, UserRepository users, ScannerService scanner,
                       ProtectionService protection, AuditService audit, AgentDecisionService agent,
                       @Value("${app.storage-dir}") String storage) {
        this.files = files; this.users = users; this.scanner = scanner;
        this.protection = protection; this.audit = audit; this.agent = agent;
        this.root = Paths.get(storage).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root.resolve("originals"));
            Files.createDirectories(root.resolve("backups"));
            Files.createDirectories(root.resolve("protected"));
        } catch (IOException e) { throw new IllegalStateException("Cannot initialize storage", e); }
    }

    public FileRecord upload(String username, MultipartFile multipart) throws Exception {
        if (multipart == null || multipart.isEmpty()) throw new IllegalArgumentException("Empty file");
        if (multipart.getSize() > 25L * 1024 * 1024) throw new IllegalArgumentException("File too large (25MB max)");

        User owner = users.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found"));
        String originalName = sanitizeOriginalName(multipart.getOriginalFilename());
        validateExtension(originalName);
        String incomingHash = hashMultipart(multipart);
        if (files.existsByOwnerUsernameAndSha256(username, incomingHash)) {
            throw new IllegalArgumentException("Duplicate file: this file was already uploaded");
        }
        String safeName = UUID.randomUUID() + "_" + originalName;
        Path original = safeResolve("originals", safeName);
        Path backup = safeResolve("backups", safeName);
        Files.createDirectories(original.getParent());
        multipart.transferTo(original.toFile());
        Files.copy(original, backup, StandardCopyOption.REPLACE_EXISTING);

        ScannerService.ScanResult scan = scanner.scan(original, originalName);
        AgentDecisionService.Decision decision = agent.decide(scan);

        FileRecord record = new FileRecord();
        record.setOriginalName(originalName);
        record.setStoredOriginal(original.toString());
        record.setBackupPath(backup.toString());
        record.setSha256(incomingHash);
        record.setRiskScore(scan.score());
        record.setRiskLevel(scan.level());
        record.setDetections(String.join(", ", scan.detections()));
        record.setQrDetected(scan.qrDetected());
        record.setFaceDetected(scan.faceDetected());
        record.setProtectedFile(false);
        record.setOwner(owner);
        record.setAgentAction(decision.action());
        record.setAgentRecommendedMethod(decision.recommendedMethod());
        record.setAgentReason(decision.reason());
        record.setAgentConfidence(decision.confidence());
        record.setAgentExecuted(false);
        files.save(record);

        audit.log(username, "FILE_SCAN", originalName + " -> " + scan.level() + " (" + scan.score() + ") | Agent: " +
                decision.action() + " / " + decision.recommendedMethod(), "LOW".equals(scan.level()) ? "INFO" : "HIGH");

        if ("AUTO_PROTECT".equals(decision.action())) {
            try {
                Path protectedPath = protectedPathFor(safeName, originalName, "AGENT_PROTECTED_");
                protection.protect(original, protectedPath, originalName, decision.recommendedMethod());
                markProtected(record, protectedPath, decision.recommendedMethod(), true);
                audit.log(username, "AGENT_AUTO_PROTECT", originalName + " protected automatically using " + decision.recommendedMethod(), "HIGH");
            } catch (Exception ex) {
                record.setAgentAction("AUTO_PROTECT_FAILED");
                record.setAgentReason(decision.reason() + " Protection attempt failed: " + safeMessage(ex));
                record.setAgentExecuted(false);
                files.save(record);
                audit.log(username, "AGENT_PROTECT_ERROR", originalName + " -> " + safeMessage(ex), "CRITICAL");
            }
        }
        return record;
    }

    public Path protect(String username, Long id, String method) throws Exception {
        FileRecord record = owned(username, id);
        Path source = Paths.get(record.getStoredOriginal()).toAbsolutePath().normalize();
        if (!Files.exists(source)) throw new IllegalArgumentException("Original file is missing");

        boolean auto = method == null || method.isBlank() || "AUTO".equalsIgnoreCase(method);
        String actual;
        if (auto) {
            boolean highRisk = "HIGH".equals(record.getRiskLevel()) || "CRITICAL".equals(record.getRiskLevel());
            if (record.isFaceDetected()) {
                actual = highRisk ? "BLACKOUT+FACE_BLUR" : "MASK+FACE_BLUR";
            } else {
                actual = highRisk ? "BLACKOUT" : "MASK";
            }
        } else {
            actual = method.trim().toUpperCase(Locale.ROOT);
        }

        Path output = protectedPathFor(source.getFileName().toString(), record.getOriginalName(), "PROTECTED_");
        protection.protect(source, output, record.getOriginalName(), actual);
        markProtected(record, output, actual, false);
        audit.log(username, "PROTECT_FILE", record.getOriginalName() + " using " + actual, "HIGH");
        return output;
    }

    private void markProtected(FileRecord record, Path output, String method, boolean agentExecution) throws Exception {
        if (!Files.exists(output) || Files.size(output) == 0) throw new IOException("Protected output was not created");
        record.setStoredProtected(output.toString());
        record.setProtectedFile(true);
        record.setProtectionMethod(method);
        record.setProtectedSha256(hash(output));
        if (agentExecution) record.setAgentExecuted(true);
        files.save(record);
    }

    public FileRecord recover(String username, Long id) throws Exception {
        FileRecord record = owned(username, id);
        Path backup = Paths.get(record.getBackupPath()).toAbsolutePath().normalize();
        Path original = Paths.get(record.getStoredOriginal()).toAbsolutePath().normalize();
        if (!Files.exists(backup)) throw new IllegalArgumentException("Backup file not found");
        String backupHash = hash(backup);
        if (record.getSha256() == null || !backupHash.equalsIgnoreCase(record.getSha256())) {
            audit.log(username, "RECOVER_INTEGRITY_FAILED", record.getOriginalName(), "CRITICAL");
            throw new IllegalStateException("Recovery backup integrity verification failed");
        }
        Files.copy(backup, original, StandardCopyOption.REPLACE_EXISTING);
        record.setSha256(hash(original));
        audit.log(username, "RECOVER_FILE", record.getOriginalName(), "INFO");
        return files.save(record);
    }

    public Map<String,Object> integrity(String username, Long id) throws Exception {
        FileRecord record = owned(username, id);
        Path original = Paths.get(record.getStoredOriginal());
        String current = hash(original);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("fileId", id);
        result.put("storedHash", record.getSha256());
        result.put("currentHash", current);
        result.put("integrityVerified", current.equalsIgnoreCase(record.getSha256()));
        if (record.getStoredProtected() != null) {
            Path protectedPath = Paths.get(record.getStoredProtected());
            String currentProtected = hash(protectedPath);
            result.put("protectedHash", record.getProtectedSha256());
            result.put("protectedCurrentHash", currentProtected);
            result.put("protectedIntegrityVerified", currentProtected.equalsIgnoreCase(record.getProtectedSha256()));
        }
        return result;
    }

    public List<FileRecord> mine(String username) { return files.findByOwnerUsernameOrderByCreatedAtDesc(username); }
    public FileRecord getOwned(String username, Long id) { return owned(username, id); }
    public String protectedDownloadName(FileRecord file) { return "PROTECTED_" + file.getOriginalName(); }

    public String recoveredDownloadName(FileRecord file) { return "RECOVERED_" + file.getOriginalName(); }

    public Path recoveredPath(FileRecord file) throws Exception {
        Path backup = Paths.get(file.getBackupPath()).toAbsolutePath().normalize();
        if (!Files.exists(backup) || !Files.isRegularFile(backup)) {
            throw new IllegalArgumentException("Recovery backup file is not available");
        }
        String backupHash = hash(backup);
        if (file.getSha256() == null || !backupHash.equalsIgnoreCase(file.getSha256())) {
            throw new IllegalStateException("Recovery backup integrity verification failed");
        }
        return backup;
    }

    public MediaType mediaTypeFor(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (n.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (n.endsWith(".txt")) return MediaType.TEXT_PLAIN;
        if (n.endsWith(".csv")) return MediaType.parseMediaType("text/csv");
        if (n.endsWith(".docx")) return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private FileRecord owned(String username, Long id) {
        FileRecord record = files.findById(id).orElseThrow(() -> new IllegalArgumentException("File not found"));
        if (record.getOwner() == null || !record.getOwner().getUsername().equals(username)) {
            throw new IllegalArgumentException("Access denied");
        }
        return record;
    }

    private Path protectedPathFor(String safeName, String originalName, String prefix) {
        String ext = extension(originalName);
        String base = safeName;
        int dot = base.lastIndexOf('.');
        if (dot >= 0) base = base.substring(0, dot);
        String outputName = prefix + base + ext;
        return safeResolve("protected", outputName);
    }

    private Path safeResolve(String folder, String filename) {
        Path base = root.resolve(folder).toAbsolutePath().normalize();
        Path result = base.resolve(filename).normalize();
        if (!result.startsWith(base)) throw new IllegalArgumentException("Invalid file path");
        return result;
    }

    private void validateExtension(String name) {
        String ext = extension(name).toLowerCase(Locale.ROOT);
        Set<String> allowed = Set.of(".jpg", ".jpeg", ".png", ".bmp", ".pdf", ".docx", ".txt", ".csv");
        if (!allowed.contains(ext)) {
            throw new IllegalArgumentException("Unsupported file type. Allowed: JPG, JPEG, PNG, BMP, PDF, DOCX, TXT, CSV");
        }
    }

    private String sanitizeOriginalName(String name) {
        String value = name == null || name.isBlank() ? "upload.bin" : Paths.get(name).getFileName().toString();
        value = value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (value.length() > 160) value = value.substring(value.length() - 160);
        return value;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? ".png" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private String safeMessage(Exception ex) { return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(); }

    private String hashMultipart(MultipartFile multipart) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = multipart.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
        }
        StringBuilder out = new StringBuilder();
        for (byte b : digest.digest()) out.append(String.format("%02x", b));
        return out.toString();
    }

    public static String hash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
        }
        StringBuilder out = new StringBuilder();
        for (byte b : digest.digest()) out.append(String.format("%02x", b));
        return out.toString();
    }
}
