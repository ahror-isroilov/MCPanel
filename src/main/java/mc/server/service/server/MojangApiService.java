package mc.server.service.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MojangApiService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public Optional<String> getPlayerUuid(String playerName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + playerName))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("id")) {
                    return Optional.of(formatUuid(root.get("id").asText()));
                }
            }
        } catch (Exception e) {
            log.error("Unable to get player uuid from Mojang", e);
        }
        return Optional.empty();
    }

    private String formatUuid(String uuidWithoutDashes) {
        return String.format("%s-%s-%s-%s-%s",
                uuidWithoutDashes.substring(0, 8),
                uuidWithoutDashes.substring(8, 12),
                uuidWithoutDashes.substring(12, 16),
                uuidWithoutDashes.substring(16, 20),
                uuidWithoutDashes.substring(20, 32)
        );
    }
}
