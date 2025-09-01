package mc.server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mc.server.model.ServerInstance;
import mc.server.service.server.MinecraftServerService;
import mc.server.service.server.ServerInstallationService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ServerInstallController {

    private final MinecraftServerService minecraftServerService;
    private final ServerInstallationService serverInstallationService;

    @PostMapping("/api/servers/install")
    public String installServer(@RequestBody Map<String, String> payload) {
        String instanceName = payload.get("instanceName");
        String templateId = payload.get("templateId");

        if (instanceName == null || instanceName.isBlank() || templateId == null || templateId.isBlank()) {
            return "redirect:/servers/create?error=missing_parameters";
        }

        try {
            ServerInstance newInstance = minecraftServerService.createServerInstance(instanceName, templateId);

            serverInstallationService.installServer(newInstance.getId(), templateId);

            return "redirect:/servers/" + newInstance.getId() + "/console";
            
        } catch (Exception e) {
            return "redirect:/servers/create?error=installation_failed";
        }
    }

    @PostMapping("/api/servers/install-ajax")
    public ResponseEntity<Map<String, Object>> installServerAjax(@RequestBody Map<String, String> payload) {
        String instanceName = payload.get("instanceName");
        String templateId = payload.get("templateId");

        if (instanceName == null || instanceName.isBlank() || templateId == null || templateId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "instanceName and templateId are required"));
        }

        try {
            ServerInstance newInstance = minecraftServerService.createServerInstance(instanceName, templateId);

            serverInstallationService.installServer(newInstance.getId(), templateId);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Installation started for " + instanceName,
                "instanceId", newInstance.getId(),
                "consoleUrl", "/servers/" + newInstance.getId() + "/console"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "Failed to start installation: " + e.getMessage()
            ));
        }
    }
    
    @PostMapping("/api/servers/create-and-install")
    public ResponseEntity<Map<String, Object>> createAndInstall(@RequestBody Map<String, String> payload) {
        log.info("=== CREATE AND INSTALL ENDPOINT === Payload: {}", payload);
        
        String instanceName = payload.get("instanceName");
        String templateId = payload.get("templateId");

        if (instanceName == null || instanceName.isBlank() || templateId == null || templateId.isBlank()) {
            log.warn("Missing parameters: instanceName={}, templateId={}", instanceName, templateId);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "instanceName and templateId are required"));
        }

        try {
            log.info("Creating server instance: {} with template: {}", instanceName, templateId);
            ServerInstance newInstance = minecraftServerService.createServerInstance(instanceName, templateId);
            log.info("Server instance created with ID: {}, starting async installation...", newInstance.getId());
            serverInstallationService.installServer(newInstance.getId(), templateId);
            
            log.info("Async installation triggered, returning response");
            return ResponseEntity.ok(Map.of(
                "success", true,
                "instanceId", newInstance.getId(),
                "message", "Server instance created, installation started"
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "Failed to create server instance: " + e.getMessage()
            ));
        }
    }
}
