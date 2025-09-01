package mc.server.controller;

import lombok.RequiredArgsConstructor;
import mc.server.model.ServerInstance;
import mc.server.service.MinecraftServerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/servers")
@RequiredArgsConstructor
public class ServerInstallController {

    private final MinecraftServerService minecraftServerService;

    @PostMapping("/install")
    public ResponseEntity<Map<String, Object>> installServer(@RequestBody Map<String, String> payload) {
        String instanceName = payload.get("instanceName");
        String templateId = payload.get("templateId");

        if (instanceName == null || instanceName.isBlank() || templateId == null || templateId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "instanceName and templateId are required"));
        }

        // Create the server instance synchronously to get an ID
        ServerInstance newInstance = minecraftServerService.createServerInstance(instanceName, templateId);

        // Start the installation asynchronously
        minecraftServerService.downloadAndInstallServer(newInstance.getId(), templateId);

        return ResponseEntity.ok(Map.of(
            "message", "Installation started for " + instanceName,
            "instanceId", newInstance.getId()
        ));
    }
}
