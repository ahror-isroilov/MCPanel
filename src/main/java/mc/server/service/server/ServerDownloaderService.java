package mc.server.service.server;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerDownloaderService {
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CompletableFuture<Void> downloadFile(String downloadUrl, Path destination) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenAccept(response -> {
                    try (InputStream is = response.body()) {
                        Files.copy(is, destination, StandardCopyOption.REPLACE_EXISTING);
                        log.info("Successfully downloaded file to {}", destination);
                    } catch (IOException e) {
                        log.error("Failed to download file from {}", downloadUrl, e);
                        throw new RuntimeException("Failed to download file", e);
                    }
                });
    }
}
