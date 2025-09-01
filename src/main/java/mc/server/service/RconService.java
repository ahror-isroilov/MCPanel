package mc.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mc.server.model.ServerInstance;
import mc.server.repository.ServerInstanceRepository;
import nl.vv32.rcon.Rcon;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class RconService {
    private final ServerInstanceRepository serverInstanceRepository;

    public CompletableFuture<String> executeCommand(Long instanceId, String command) {
        return CompletableFuture.supplyAsync(() -> {
            ServerInstance instance = getInstance(instanceId);
            if (!isConfigured(instance)) {
                log.warn("RCON is not properly configured for instance {}", instanceId);
                return null;
            }
            return executeWithRetry(instance, command, 3); // 3 retries
        });
    }

    public String executeCommandSync(Long instanceId, String command) {
        ServerInstance instance = getInstance(instanceId);
        if (!isConfigured(instance)) {
            log.warn("RCON is not properly configured for instance {}", instanceId);
            return null;
        }
        return executeWithRetry(instance, command, 3);
    }

    private String executeWithRetry(ServerInstance instance, String command, int attemptsLeft) {
        try (Rcon rcon = Rcon.open(instance.getIp(), instance.getRconPort())) {
            if (rcon.authenticate(instance.getRconPassword())) {
                String response = rcon.sendCommand(command);
                log.debug("RCON command '{}' executed successfully on instance {}. Response: {}", command, instance.getId(), response);
                return response;
            } else {
                log.error("RCON authentication failed for command: {} on instance {}", command, instance.getId());
                return null;
            }
        } catch (Exception e) {
            log.debug("RCON command '{}' failed on instance {} (attempts left: {}): {}",
                    command, instance.getId(), attemptsLeft - 1, e.getMessage());

            if (attemptsLeft > 1) {
                try {
                    TimeUnit.MILLISECONDS.sleep(1000);
                    return executeWithRetry(instance, command, attemptsLeft - 1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("RCON retry interrupted for instance {}", instance.getId(), ie);
                    return null;
                }
            } else {
                log.error("RCON command '{}' failed after all retry attempts on instance {}", command, instance.getId());
                return null;
            }
        }
    }

    public boolean testConnection(Long instanceId) {
        ServerInstance instance = getInstance(instanceId);
        if (!isConfigured(instance)) {
            return false;
        }

        try (Rcon rcon = Rcon.open(instance.getIp(), instance.getRconPort())) {
            return rcon.authenticate(instance.getRconPassword());
        } catch (Exception e) {
            log.debug("RCON connection test failed for instance {}: {}", instanceId, e.getMessage());
            return false;
        }
    }

    public <T> CompletableFuture<T> executeCommand(Long instanceId, String command, Function<String, T> responseParser) {
        return executeCommand(instanceId, command).thenApply(response -> {
            if (response == null) {
                return null;
            }
            try {
                return responseParser.apply(response);
            } catch (Exception e) {
                log.error("Error parsing RCON response for command '{}' on instance {}: {}", command, instanceId, e.getMessage());
                return null;
            }
        });
    }

    public boolean isConfigured(Long instanceId) {
        ServerInstance instance = getInstance(instanceId);
        return isConfigured(instance);
    }
    
    private boolean isConfigured(ServerInstance instance) {
        return instance.isRconEnabled() && instance.getRconPort() > 0 && instance.getRconPassword() != null && !instance.getRconPassword().isEmpty();
    }

    public String getConnectionInfo(Long instanceId) {
        ServerInstance instance = getInstance(instanceId);
        if (!isConfigured(instance)) {
            return "RCON not configured for instance " + instanceId;
        }

        return String.format("RCON: %s:%d", instance.getIp(), instance.getRconPort());
    }

    private ServerInstance getInstance(Long instanceId) {
        return serverInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid instanceId: " + instanceId));
    }
}
