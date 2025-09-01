package mc.server.service.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final String PAPER_API_URL = "https://api.papermc.io/v2/projects/paper";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;

    public CompletableFuture<String> getLatestBuildForVersion(String version) {
        String url = String.format("%s/versions/%s/builds", PAPER_API_URL, version);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(body -> {
                    try {
                        JsonNode builds = objectMapper.readTree(body).get("builds");
                        if (builds.isArray() && !builds.isEmpty()) {
                            return builds.get(builds.size() - 1).get("build").asText();
                        }
                    } catch (IOException e) {
                        log.error("Failed to parse PaperMC API response for version {}", version, e);
                    }
                    return null;
                });
    }

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

    @Deprecated
    public CompletableFuture<Void> downloadJar(String version, String build, Path destination) {
        String downloadUrl = String.format("%s/versions/%s/builds/%s/downloads/paper-%s-%s.jar",
                PAPER_API_URL, version, build, version, build);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenAccept(response -> {
                    try (InputStream is = response.body()) {
                        Files.copy(is, destination, StandardCopyOption.REPLACE_EXISTING);
                        log.info("Successfully downloaded server jar to {}", destination);
                    } catch (IOException e) {
                        log.error("Failed to download server jar from {}", downloadUrl, e);
                    }
                });
    }
}
