package mc.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mc.server.repository.ServerInstanceRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortManagerService {
    
    private final ServerInstanceRepository serverInstanceRepository;
    
    // Port range for Minecraft servers
    private static final int MIN_PORT = 25565;
    private static final int MAX_PORT = 25665;
    
    // RCON port range
    private static final int MIN_RCON_PORT = 25700;
    private static final int MAX_RCON_PORT = 25800;
    
    public int findAvailablePort() {
        return findAvailablePortInRange(MIN_PORT, MAX_PORT);
    }
    
    public int findAvailableRconPort() {
        return findAvailablePortInRange(MIN_RCON_PORT, MAX_RCON_PORT);
    }
    
    private int findAvailablePortInRange(int minPort, int maxPort) {
        // Get all currently allocated ports from database
        Set<Integer> usedPorts = new HashSet<>(serverInstanceRepository.findAllAllocatedPorts());
        Set<Integer> usedRconPorts = new HashSet<>(serverInstanceRepository.findAllAllocatedRconPorts());
        
        for (int port = minPort; port <= maxPort; port++) {
            if (!usedPorts.contains(port) && !usedRconPorts.contains(port) && isPortAvailable(port)) {
                log.debug("Found available port: {}", port);
                return port;
            }
        }
        
        throw new RuntimeException("No available ports in range " + minPort + "-" + maxPort);
    }
    
    private boolean isPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}