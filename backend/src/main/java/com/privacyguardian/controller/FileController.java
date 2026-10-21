package com.privacyguardian.controller;

import com.privacyguardian.entity.FileRecord;
import com.privacyguardian.service.FileService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService service;
    public FileController(FileService service) { this.service = service; }

    @PostMapping("/scan")
    public FileRecord scan(@RequestParam("file") MultipartFile file, Authentication auth) throws Exception {
        return service.upload(auth.getName(), file);
    }

    @GetMapping
    public List<FileRecord> mine(Authentication auth) { return service.mine(auth.getName()); }

    @PostMapping("/{id}/protect")
    public ResponseEntity<?> protect(@PathVariable Long id, Authentication auth,
                                     @RequestParam(value="method", defaultValue="AUTO") String method) throws Exception {
        service.protect(auth.getName(), id, method);
        return ResponseEntity.ok(Map.of("message", "File protected successfully", "download", "/api/files/" + id + "/protected"));
    }

    @PostMapping("/{id}/recover")
    public ResponseEntity<?> recover(@PathVariable Long id, Authentication auth) throws Exception {
        return ResponseEntity.ok(service.recover(auth.getName(), id));
    }

    @GetMapping("/{id}/integrity")
    public ResponseEntity<?> integrity(@PathVariable Long id, Authentication auth) throws Exception {
        return ResponseEntity.ok(service.integrity(auth.getName(), id));
    }

    @GetMapping("/{id}/recover/download")
    public ResponseEntity<byte[]> recoveredFile(@PathVariable Long id, Authentication auth) throws Exception {
        FileRecord file = service.getOwned(auth.getName(), id);
        Path path = service.recoveredPath(file);
        if (!Files.exists(path) || !Files.isRegularFile(path)) return ResponseEntity.notFound().build();
        String filename = service.recoveredDownloadName(file);
        MediaType type = service.mediaTypeFor(filename);
        byte[] bytes = Files.readAllBytes(path);
        return ResponseEntity.ok().contentType(type)
                .contentLength(bytes.length)
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(bytes);
    }

    @GetMapping("/{id}/protected")
    public ResponseEntity<byte[]> protectedFile(@PathVariable Long id, Authentication auth) throws Exception {
        FileRecord file = service.getOwned(auth.getName(), id);
        if (file.getStoredProtected() == null) return ResponseEntity.notFound().build();
        Path path = Paths.get(file.getStoredProtected()).normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path)) return ResponseEntity.notFound().build();
        String filename = service.protectedDownloadName(file);
        MediaType type = service.mediaTypeFor(filename);
        byte[] bytes = Files.readAllBytes(path);
        return ResponseEntity.ok().contentType(type)
                .contentLength(bytes.length)
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(bytes);
    }
}
