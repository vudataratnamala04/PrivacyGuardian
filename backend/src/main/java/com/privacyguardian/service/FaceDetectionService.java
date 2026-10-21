package com.privacyguardian.service;

import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Local face detector used by Privacy Guardian. No cloud API and no Python runtime
 * are required. OpenCV is packaged through Maven and the Haar cascade is bundled
 * inside the application resources.
 */
@Service
public class FaceDetectionService {
    private static final String CASCADE = "haarcascade_frontalface_default.xml";

    private final CascadeClassifier classifier;

    public FaceDetectionService() throws Exception {
        OpenCV.loadShared();
        Path cascadePath = materializeCascade();
        this.classifier = new CascadeClassifier(cascadePath.toString());
        if (classifier.empty()) {
            throw new IllegalStateException("Could not load face detector cascade: " + cascadePath);
        }
    }

    public List<FaceBox> detect(BufferedImage image) throws Exception {
        if (image == null) return List.of();

        byte[] bytes = encodePng(image);
        MatOfByte encoded = new MatOfByte(bytes);
        Mat source = Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_COLOR);
        encoded.release();
        if (source.empty()) {
            source.release();
            return List.of();
        }

        Mat gray = new Mat();
        MatOfRect faces = new MatOfRect();
        try {
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.equalizeHist(gray, gray);
            classifier.detectMultiScale(
                    gray,
                    faces,
                    1.10,
                    5,
                    0,
                    new Size(30, 30),
                    new Size()
            );

            List<FaceBox> result = new ArrayList<>();
            for (Rect r : faces.toArray()) {
                if (r.width <= 0 || r.height <= 0) continue;
                result.add(new FaceBox(r.x, r.y, r.width, r.height));
            }
            return result;
        } finally {
            faces.release();
            gray.release();
            source.release();
        }
    }

    private byte[] encodePng(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IllegalStateException("Could not encode image for face detection");
        }
        return out.toByteArray();
    }

    private Path materializeCascade() throws Exception {
        ClassPathResource resource = new ClassPathResource(CASCADE);
        if (!resource.exists()) throw new IllegalStateException("Missing " + CASCADE);
        Path temp = Files.createTempFile("privacy-guardian-face-", ".xml");
        try (InputStream in = resource.getInputStream()) {
            Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        temp.toFile().deleteOnExit();
        return temp;
    }

    public record FaceBox(int x, int y, int width, int height) {}
}
