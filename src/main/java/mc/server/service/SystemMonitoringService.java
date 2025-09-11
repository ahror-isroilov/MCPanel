package mc.server.service;

import lombok.extern.slf4j.Slf4j;
import mc.server.model.ServerInstance;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class SystemMonitoringService {
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

    @Cacheable("systemStats")
    public Map<String, Object> getSystemStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            double cpuUsage = getCpuUsage();
            stats.put("cpuUsage", Math.round(cpuUsage * 100.0) / 100.0);

            Map<String, Double> memoryInfo = getMemoryInfo();
            stats.put("ramUsage", memoryInfo.get("used"));
            stats.put("totalRam", memoryInfo.get("total"));
            stats.put("ramUsagePercent", memoryInfo.get("usedPercent"));

            Map<String, Double> diskInfo = getDiskInfo();
            stats.put("diskUsage", diskInfo.get("used"));
            stats.put("totalDisk", diskInfo.get("total"));
            stats.put("diskUsagePercent", diskInfo.get("usedPercent"));

            double loadAverage = osBean.getSystemLoadAverage();
            stats.put("loadAverage", loadAverage);

            int processors = osBean.getAvailableProcessors();
            stats.put("cpuCores", processors);

            long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
            stats.put("systemUptime", formatUptime(uptime));

            log.debug("System stats collected: CPU={}%, RAM={}GB, Disk={}GB",
                    cpuUsage, memoryInfo.get("used"), diskInfo.get("used"));

        } catch (Exception e) {
            log.error("Error collecting system stats", e);
            stats.put("error", "Failed to collect system statistics");
        }

        return stats;
    }

    public double getInstanceRamUsage(ServerInstance instance) {
        if (instance.getPid() == null) {
            return 0.0;
        }

        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;

            if (os.contains("win")) {
                pb = new ProcessBuilder("tasklist", "/fi", "pid eq " + instance.getPid(), "/fo", "csv", "/nh");
            } else {
                pb = new ProcessBuilder("ps", "-p", String.valueOf(instance.getPid()), "-o", "rss=");
            }

            Process process = pb.start();
            process.waitFor(5, TimeUnit.SECONDS);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();

            if (line != null) {
                if (os.contains("win")) {
                    String[] values = line.split("\",\"");
                    if (values.length > 4) {
                        String memUsage = values[4].replaceAll("[^\\d]", "");
                        return Double.parseDouble(memUsage) / 1024.0; // Convert KB to MB
                    }
                } else {
                    return Double.parseDouble(line.trim()) / 1024.0; // Convert KB to MB
                }
            }
        } catch (Exception e) {
            log.error("Could not get RAM usage for instance {}: {}", instance.getName(), e.getMessage());
        }
        return 0.0;
    }

    public double getInstanceDiskUsage(ServerInstance instance) {
        try {
            Path path = Paths.get(instance.getInstancePath());
            if (Files.exists(path)) {
                long size = Files.walk(path)
                                 .mapToLong(p -> p.toFile().length())
                                 .sum();
                return size / (1024.0 * 1024.0); // Convert bytes to MB
            }
        } catch (IOException e) {
            log.error("Could not get disk usage for instance {}: {}", instance.getName(), e.getMessage());
        }
        return 0.0;
    }

    public double parseMemoryToMb(String memory) {
        if (memory == null || memory.isEmpty()) {
            return 0.0;
        }
        Pattern p = Pattern.compile("(\\d+)([GgMmKk])");
        Matcher m = p.matcher(memory);
        if (m.find()) {
            double value = Double.parseDouble(m.group(1));
            String unit = m.group(2).toUpperCase();
            switch (unit) {
                case "G":
                    return value * 1024;
                case "M":
                    return value;
                case "K":
                    return value / 1024;
            }
        }
        return 0.0;
    }

    private double getCpuUsage() {
        try {
            Process process = Runtime.getRuntime().exec("top -bn1 | grep 'Cpu(s)'");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();

            if (line != null) {
                String[] parts = line.split(",");
                for (String part : parts) {
                    if (part.contains("id")) {
                        String idleStr = part.replaceAll("[^0-9.]", "");
                        double idle = Double.parseDouble(idleStr);
                        return 100.0 - idle;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not get CPU usage from top command, falling back to JMX");
        }

        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsBean =
                    (com.sun.management.OperatingSystemMXBean) osBean;
            return sunOsBean.getCpuLoad() * 100.0;
        }

        return 0.0;
    }

    private Map<String, Double> getMemoryInfo() {
        Map<String, Double> info = new HashMap<>();

        try {
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                long totalMemory = sunOsBean.getTotalMemorySize();
                long freeMemory = sunOsBean.getFreeMemorySize();
                getMemoryStorageInfo(info, totalMemory, freeMemory);
            }
        } catch (Exception e) {
            log.error("Error getting memory info", e);
        }

        return info;
    }

    private void getMemoryStorageInfo(Map<String, Double> info, long totalMemory, long freeMemory) {
        long usedMemory = totalMemory - freeMemory;

        double totalGB = totalMemory / (1024.0 * 1024.0 * 1024.0);
        double usedGB = usedMemory / (1024.0 * 1024.0 * 1024.0);
        double usedPercent = (double) usedMemory / totalMemory * 100.0;

        info.put("total", Math.round(totalGB * 100.0) / 100.0);
        info.put("used", Math.round(usedGB * 100.0) / 100.0);
        info.put("usedPercent", Math.round(usedPercent * 100.0) / 100.0);
    }

    private Map<String, Double> getDiskInfo() {
        Map<String, Double> info = new HashMap<>();

        try {
            Path rootPath = FileSystems.getDefault().getRootDirectories().iterator().next();
            FileStore store = Files.getFileStore(rootPath);

            long total = store.getTotalSpace();
            long usable = store.getUsableSpace();
            getMemoryStorageInfo(info, total, usable);

        } catch (Exception e) {
            log.error("Error getting disk info", e);
            info.put("total", 0.0);
            info.put("used", 0.0);
            info.put("usedPercent", 0.0);
        }

        return info;
    }

    public Map<String, Object> getJvmStats() {
        Map<String, Object> stats = new HashMap<>();

        Runtime runtime = Runtime.getRuntime();

        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        double maxMB = maxMemory / (1024.0 * 1024.0);
        double usedMB = usedMemory / (1024.0 * 1024.0);
        double usedPercent = (double) usedMemory / maxMemory * 100.0;

        stats.put("maxMemory", Math.round(maxMB * 100.0) / 100.0);
        stats.put("usedMemory", Math.round(usedMB * 100.0) / 100.0);
        stats.put("usedPercent", Math.round(usedPercent * 100.0) / 100.0);
        stats.put("availableProcessors", runtime.availableProcessors());

        return stats;
    }

    private String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else {
            return String.format("%dm %ds", minutes, seconds % 60);
        }
    }

    public boolean isHighCpuUsage() {
        return getCpuUsage() > 80.0;
    }

    public boolean isHighMemoryUsage() {
        Map<String, Double> memInfo = getMemoryInfo();
        return memInfo.get("usedPercent") > 85.0;
    }

    public boolean isHighDiskUsage() {
        Map<String, Double> diskInfo = getDiskInfo();
        return diskInfo.get("usedPercent") > 90.0;
    }
}