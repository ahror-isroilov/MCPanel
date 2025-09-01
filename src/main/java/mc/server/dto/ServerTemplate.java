package mc.server.dto;

import java.util.List;

public record ServerTemplate(
    String id,
    String name,
    List<String> tags,
    String description,
    String systemRequirements,
    String hardwareRequirements,
    String imageUrl,
    String downloadUrl,
    String type,
    List<InstallationStep> installationSteps
) {}
