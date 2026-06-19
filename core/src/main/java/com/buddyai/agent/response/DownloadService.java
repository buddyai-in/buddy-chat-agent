package com.buddyai.agent.response;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.UUID;

/**
 * Handles <em>delivery</em> of generated artifacts (CSV exports, files,
 * media) — deliberately separate from the code that decides <em>format</em>.
 *
 * <p>Writes to a local directory and returns a download URL. This is the one
 * seam to swap for S3/GCS later: implement an alternate {@code DownloadService}
 * (or back this with a storage client) without touching any response handler
 * or tool.</p>
 */
@Slf4j
@Service
public class DownloadService {

    @Value("${agent.download.dir:/tmp/buddyai-downloads}")
    private String downloadDir;

    @Value("${agent.download.base-url:/files}")
    private String baseUrl;

    /**
     * Persist text content (e.g. a generated CSV) and return a download URL.
     *
     * @param content   the file contents
     * @param extension file extension without the dot (e.g. {@code "csv"})
     * @return a URL the client can use to download the artifact
     */
    public String storeText(String content, String extension) {
        String fileName = UUID.randomUUID() + "." + extension;
        return write(fileName, content.getBytes());
    }

    /**
     * Persist binary content from base64 and return a download URL.
     */
    public String storeBase64(String base64, String extension) {
        String fileName = UUID.randomUUID() + "." + extension;
        return write(fileName, Base64.getDecoder().decode(base64));
    }

    private String write(String fileName, byte[] bytes) {
        try {
            Path dir = Paths.get(downloadDir);
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            Files.write(target, bytes);
            String url = baseUrl + "/" + fileName;
            log.info("Stored downloadable artifact at {} ({} bytes)", url, bytes.length);
            return url;
        } catch (IOException e) {
            log.error("Failed to store downloadable artifact: {}", e.getMessage(), e);
            throw new RuntimeException("Could not store download artifact", e);
        }
    }
}
