package mc.server.model;

public enum InstallationStatus {
    PENDING_INSTALLATION, // Initial state after creation
    DOWNLOADING,          // Actively downloading files
    RUNNING_INSTALLER,    // Executing a setup command (e.g., Forge installer)
    CONFIGURING,          // Writing properties, allocating ports
    INSTALLATION_FAILED,  // A critical error occurred
    STOPPED,              // Installation complete, ready to be started
    STARTING,
    RUNNING,
    STOPPING,
    DELETING
}