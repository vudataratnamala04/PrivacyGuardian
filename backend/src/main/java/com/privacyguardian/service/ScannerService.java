package com.privacyguardian.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import javax.imageio.ImageIO;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

@Service
public class ScannerService {
    private final String tesseractCommand;
    private final FaceDetectionService faceDetection;

    public ScannerService(@Value("${app.tesseract-command:tesseract}") String command, FaceDetectionService faceDetection) {
        this.tesseractCommand = command;
        this.faceDetection = faceDetection;
    }

    public ScanResult scan(Path p, String originalName) throws Exception {
        String text = extractText(p, originalName);
        List<Detection> detections = detectText(text);
        boolean qr = detectQr(p, originalName);
        boolean face = detectFaces(p, originalName);
        if (qr) detections.add(new Detection("QR code", 40));
        if (face) detections.add(new Detection("Face", 25));
        int score = Math.min(100, detections.stream().mapToInt(Detection::points).sum());
        String level = score >= 76 ? "CRITICAL" : score >= 51 ? "HIGH" : score >= 26 ? "MEDIUM" : "LOW";
        List<String> labels = detections.stream().map(Detection::label).distinct().toList();
        return new ScanResult(score, level, labels, qr, face);
    }

    public String extractText(Path p, String name) throws Exception {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".pdf")) {
            try (var doc = Loader.loadPDF(p.toFile())) {
                String text = new PDFTextStripper().getText(doc);
                // If the PDF has little/no text, OCR rendered pages.
                if (text == null || text.trim().length() < 15) {
                    StringBuilder ocr = new StringBuilder();
                    PDFRenderer renderer = new PDFRenderer(doc);
                    for (int i = 0; i < doc.getNumberOfPages(); i++) {
                        BufferedImage img = renderer.renderImageWithDPI(i, 150);
                        ocr.append(ocrImage(img)).append('\n');
                    }
                    return ocr.toString();
                }
                return text;
            }
        }
        if (n.endsWith(".docx")) {
            try (InputStream in = Files.newInputStream(p); XWPFDocument doc = new XWPFDocument(in)) {
                StringBuilder s = new StringBuilder();
                doc.getParagraphs().forEach(x -> s.append(x.getText()).append('\n'));
                doc.getTables().forEach(t -> t.getRows().forEach(r ->
                    r.getTableCells().forEach(c -> s.append(c.getText()).append('\n'))));
                return s.toString();
            }
        }
        if (n.endsWith(".txt") || n.endsWith(".csv")) return Files.readString(p);
        BufferedImage img = ImageIO.read(p.toFile());
        if (img != null) return ocrImage(img);
        return "";
    }

    public String ocrImage(BufferedImage image) throws Exception {
        Path tmp = Files.createTempFile("pg-ocr-", ".png");
        Path outBase = Files.createTempFile("pg-ocr-out-", "");
        try {
            ImageIO.write(image, "png", tmp.toFile());
            Path outTxt = Paths.get(outBase.toString() + ".txt");
            List<String> cmd = List.of(resolveTesseract(), tmp.toString(), outBase.toString(), "--psm", "6");
            Process pr = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String console = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = pr.waitFor();
            if (code != 0) throw new IOException("Tesseract OCR failed (" + code + "): " + console);
            return Files.exists(outTxt) ? Files.readString(outTxt) : "";
        } finally {
            Files.deleteIfExists(tmp);
            Files.deleteIfExists(outBase);
            Files.deleteIfExists(Paths.get(outBase.toString() + ".txt"));
        }
    }

    private String resolveTesseract() {
        if (!"tesseract".equalsIgnoreCase(tesseractCommand)) return tesseractCommand;
        String[] candidates = {
            "C:\\Program Files\\Tesseract-OCR\\tesseract.exe",
            "C:\\Program Files (x86)\\Tesseract-OCR\\tesseract.exe"
        };
        for (String c : candidates) if (Files.exists(Paths.get(c))) return c;
        return "tesseract";
    }

    public List<Detection> detectText(String text) {
        if (text == null) text = "";
        List<Detection> d = new ArrayList<>();
        detect(text, "Aadhaar-like number", "\\b[2-9][0-9]{3}\\s?[0-9]{4}\\s?[0-9]{4}\\b", 35, d);
        detect(text, "PAN-like number", "\\b[A-Z]{5}[0-9]{4}[A-Z]\\b", 30, d);
        detect(text, "Phone number", "(?<!\\d)(?:\\+91[- ]?)?[6-9]\\d{9}(?!\\d)", 18, d);
        detect(text, "Email address", "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", 8, d);
        detect(text, "Card-like number", "\\b(?:\\d[ -]?){13,19}\\b", 30, d);
        detect(text, "Date of birth", "\\b(?:0?[1-9]|[12]\\d|3[01])[-/.](?:0?[1-9]|1[0-2])[-/.](?:19|20)\\d{2}\\b", 15, d);
        detect(text, "UPI ID", "\\b[A-Za-z0-9._-]{2,}@[A-Za-z]{2,}\\b", 12, d);
        detect(text, "Secret/API key", "(?i)\\b(?:api[_-]?key|secret|password|token)\\s*[:=]\\s*[^\\s]{6,}", 30, d);
        detect(text, "Sensitive name", "(?im)^\\s*(?:full\\s*)?name\\s*[:=-]\\s*[^\\r\\n]{2,80}", 12, d);
        detect(text, "Address", "(?im)^\\s*(?:address|residential address|permanent address)\\s*[:=-]\\s*[^\\r\\n]{5,150}", 18, d);
        detect(text, "Student/Employee ID", "(?im)^\\s*(?:student|employee|staff|candidate)\\s*(?:id|no|number)\\s*[:=-]\\s*[A-Za-z0-9-]{4,40}", 12, d);
        detect(text, "Blood group", "(?im)^\\s*blood\\s*group\\s*[:=-]\\s*(?:A|B|AB|O)[+-]\\s*$", 5, d);
        return d;
    }

    private void detect(String text, String label, String regex, int points, List<Detection> d) {
        if (Pattern.compile(regex).matcher(text).find()) d.add(new Detection(label, points));
    }

    private boolean detectFaces(Path p, String name) {
        try {
            String n = name.toLowerCase(Locale.ROOT);
            if (n.endsWith(".pdf")) {
                try (var doc = Loader.loadPDF(p.toFile())) {
                    PDFRenderer r = new PDFRenderer(doc);
                    for (int i = 0; i < Math.min(doc.getNumberOfPages(), 10); i++) {
                        if (!faceDetection.detect(r.renderImageWithDPI(i, 120)).isEmpty()) return true;
                    }
                    return false;
                }
            }
            BufferedImage img = ImageIO.read(p.toFile());
            return img != null && !faceDetection.detect(img).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean detectQr(Path p, String name) {
        try {
            String n = name.toLowerCase(Locale.ROOT);
            if (n.endsWith(".pdf")) {
                try (var doc = Loader.loadPDF(p.toFile())) {
                    PDFRenderer r = new PDFRenderer(doc);
                    for (int i = 0; i < Math.min(doc.getNumberOfPages(), 10); i++)
                        if (decodeQr(r.renderImageWithDPI(i, 150))) return true;
                    return false;
                }
            }
            BufferedImage img = ImageIO.read(p.toFile());
            return img != null && decodeQr(img);
        } catch (Exception e) { return false; }
    }

    private boolean decodeQr(BufferedImage img) {
        try {
            var src = new BufferedImageLuminanceSource(img);
            var bmp = new BinaryBitmap(new HybridBinarizer(src));
            new MultiFormatReader().decode(bmp);
            return true;
        } catch (Exception e) { return false; }
    }

    public record Detection(String label, int points) {}
    public record ScanResult(int score, String level, List<String> detections, boolean qrDetected, boolean faceDetected) {}
}
