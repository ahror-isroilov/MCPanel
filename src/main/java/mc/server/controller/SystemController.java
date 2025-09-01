package mc.server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mc.server.dto.ApiResponse;
import mc.server.service.server.MinecraftServerService;
import mc.server.service.SystemMonitoringService;
import mc.server.repository.ServerInstanceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SystemController {
    private final SystemMonitoringService systemMonitoringService;
    private final MinecraftServerService minecraftServerService;
    private final ServerInstanceRepository serverInstanceRepository;

    @GetMapping("/system-stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemStats() {
        try {
            Map<String, Object> systemStats = systemMonitoringService.getSystemStats();
            Map<String, Object> jvmStats = systemMonitoringService.getJvmStats();
            
            // Combine both stats into a single response
            Map<String, Object> allStats = Map.of(
                "system", systemStats,
                "jvm", jvmStats
            );
            
            return ResponseEntity.ok(ApiResponse.success(allStats));
        } catch (Exception e) {
            log.error("Error getting system stats", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get system statistics"));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOverallServerStatus() {
        try {
            // Get status of all server instances
            var serverInstances = serverInstanceRepository.findAll();
            int totalServers = serverInstances.size();
            int runningServers = 0;
            
            for (var instance : serverInstances) {
                if (minecraftServerService.isServerRunning(instance.getId())) {
                    runningServers++;
                }
            }
            
            boolean anyServerOnline = runningServers > 0;
            
            Map<String, Object> status = Map.of(
                "online", anyServerOnline,
                "totalServers", totalServers,
                "runningServers", runningServers
            );
            
            return ResponseEntity.ok(ApiResponse.success(status));
        } catch (Exception e) {
            log.error("Error getting overall server status", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get server status"));
        }
    }
}