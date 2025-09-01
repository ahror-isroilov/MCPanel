/**
 * System Monitor Module
 * Encapsulates state and logic for the system monitoring page.
 */
const SystemMonitor = {
    ws: null,
    refreshInterval: null,
    dom: {},

    init() {
        this.cacheDomElements();
        this.addEventListeners();
        this.connectWebSocket();
        this.loadInitialStats();
        this.startAutoRefresh();
    },

    cacheDomElements() {
        this.dom.alertSection = document.getElementById('alert-section');
        this.dom.lastUpdateTime = document.getElementById('last-update-time');

        this.dom.cpuUsageValue = document.getElementById('cpu-usage-value');
        this.dom.cpuProgress = document.getElementById('cpu-progress');
        this.dom.ramUsageValue = document.getElementById('ram-usage-value');
        this.dom.ramProgress = document.getElementById('ram-progress');
        this.dom.diskUsageValue = document.getElementById('disk-usage-value');
        this.dom.diskProgress = document.getElementById('disk-progress');
        this.dom.jvmMemoryValue = document.getElementById('jvm-memory-value');
        this.dom.jvmProgress = document.getElementById('jvm-progress');

        // System Info
        this.dom.cpuCores = document.getElementById('cpu-cores');
        this.dom.loadAverage = document.getElementById('load-average');
        this.dom.systemUptime = document.getElementById('system-uptime');
        this.dom.jvmProcessors = document.getElementById('jvm-processors');

        // Service Status
        this.dom.totalServers = document.getElementById('total-servers');
        this.dom.runningServers = document.getElementById('running-servers');
        this.dom.minecraftStatus = document.getElementById('minecraft-status');
        this.dom.websocketSessions = document.getElementById('websocket-sessions');
    },

    addEventListeners() {
        const refreshBtn = document.getElementById('refresh-all');
        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => this.loadInitialStats());
        }
        window.addEventListener('beforeunload', () => this.cleanup());
    },

    connectWebSocket() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws/system`;
        this.ws = new WebSocket(wsUrl);

        this.ws.onmessage = event => {
            const message = JSON.parse(event.data);
            if (message.type === 'system-stats') {
                this.updateAllStats(message.data);
            }
        };
        this.ws.onclose = () => setTimeout(() => this.connectWebSocket(), 5000);
        this.ws.onerror = error => console.error('WebSocket error:', error);
    },

    async loadInitialStats() {
        try {
            const response = await fetch('/api/system/system-stats');
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const result = await response.json();
            if (result.success) {
                this.updateAllStats(result.data);
                // Fetch server status separately if needed, though ideally it's in the main payload
                this.updateMinecraftStatus();
            } else {
                console.error('Failed to load system stats:', result.error);
            }
        } catch (error) {
            console.error('Error fetching system stats:', error);
        }
    },

    updateAllStats(stats) {
        const systemData = stats.system || stats;
        const jvmData = stats.jvm || {};

        const cpuPercent = Math.round(systemData.cpuUsage || 0);
        this.updateProgress(this.dom.cpuUsageValue, this.dom.cpuProgress, cpuPercent, 60, 80, `${cpuPercent}%`);

        const ramPercent = Math.round(systemData.ramUsagePercent || 0);
        const ramText = `${(systemData.ramUsage || 0).toFixed(1)} GB / ${(systemData.totalRam || 0).toFixed(1)} GB`;
        this.updateProgress(this.dom.ramUsageValue, this.dom.ramProgress, ramPercent, 70, 85, ramText);

        const diskPercent = Math.round(systemData.diskUsagePercent || 0);
        const diskText = `${(systemData.diskUsage || 0).toFixed(1)} GB / ${(systemData.totalDisk || 0).toFixed(1)} GB`;
        this.updateProgress(this.dom.diskUsageValue, this.dom.diskProgress, diskPercent, 75, 90, diskText);

        if (jvmData && Object.keys(jvmData).length > 0) {
            const jvmPercent = Math.round(jvmData.usedPercent || 0);
            const jvmText = `${(jvmData.usedMemory || 0).toFixed(0)} MB / ${(jvmData.maxMemory || 0).toFixed(0)} MB`;
            this.updateProgress(this.dom.jvmMemoryValue, this.dom.jvmProgress, jvmPercent, 70, 85, jvmText);
            this.dom.jvmProcessors.textContent = jvmData.availableProcessors || 'N/A';
        }

        this.dom.cpuCores.textContent = systemData.cpuCores || 'N/A';
        this.dom.loadAverage.textContent = (systemData.loadAverage !== undefined ? systemData.loadAverage.toFixed(2) : 'N/A');
        this.dom.systemUptime.textContent = systemData.systemUptime || 'N/A';

        this.dom.websocketSessions.textContent = systemData.activeWebSocketSessions || 0;

        this.updateAlerts(systemData);
        this.dom.lastUpdateTime.textContent = new Date().toLocaleString();
    },

    updateProgress(valueEl, progressEl, percent, warn, danger, text) {
        const usageClass = percent >= danger ? 'danger' : (percent >= warn ? 'warning' : '');
        valueEl.textContent = text;
        valueEl.className = `stat-value ${usageClass}`;
        progressEl.style.width = `${percent}%`;
        progressEl.className = `progress-fill ${usageClass}`;
    },

    updateAlerts(stats) {
        const alerts = [];
        const ramPercent = stats.ramUsagePercent || 0;
        const diskPercent = stats.diskUsagePercent || 0;

        if (stats.cpuUsage > 80) alerts.push({ type: 'danger', message: `High CPU usage: ${Math.round(stats.cpuUsage)}%` });
        else if (stats.cpuUsage > 60) alerts.push({ type: 'warning', message: `CPU usage is elevated: ${Math.round(stats.cpuUsage)}%` });

        if (ramPercent > 85) alerts.push({ type: 'danger', message: `High memory usage: ${Math.round(ramPercent)}%` });
        else if (ramPercent > 70) alerts.push({ type: 'warning', message: `Memory usage is elevated: ${Math.round(ramPercent)}%` });

        if (diskPercent > 90) alerts.push({ type: 'danger', message: `Disk space critical: ${Math.round(diskPercent)}% used` });
        else if (diskPercent > 75) alerts.push({ type: 'warning', message: `Disk space is running low: ${Math.round(diskPercent)}% used` });

        this.dom.alertSection.innerHTML = ''; // Clear previous alerts
        const fragment = document.createDocumentFragment();

        if (alerts.length === 0) {
            fragment.appendChild(this.createAlertElement('info', 'System resources are operating within normal parameters.'));
        } else {
            alerts.forEach(alert => fragment.appendChild(this.createAlertElement(alert.type, alert.message)));
        }
        this.dom.alertSection.appendChild(fragment);
    },

    createAlertElement(type, message) {
        const el = document.createElement('div');
        el.className = `alert alert-${type}`;
        el.innerHTML = `<div class="alert-icon"></div><span>${message}</span>`;
        return el;
    },

    async updateMinecraftStatus() {
        try {
            const response = await fetch('/api/system/status');
            const result = await response.json();
            if(result.success) {
                const data = result.data;
                const isOnline = data.online;
                const totalServers = data.totalServers || 0;
                const runningServers = data.runningServers || 0;
                
                this.dom.totalServers.textContent = totalServers;
                this.dom.runningServers.textContent = runningServers;
                this.dom.runningServers.className = `service-value ${runningServers > 0 ? '' : 'danger'}`;
                
                this.dom.minecraftStatus.textContent = isOnline ? 'Online' : 'Offline';
                this.dom.minecraftStatus.className = `service-value ${isOnline ? '' : 'danger'}`;
            }
        } catch (error) {
            this.dom.totalServers.textContent = 'Error';
            this.dom.runningServers.textContent = 'Error';
            this.dom.minecraftStatus.textContent = 'Error';
            this.dom.totalServers.className = 'service-value danger';
            this.dom.runningServers.className = 'service-value danger';
            this.dom.minecraftStatus.className = 'service-value danger';
        }
    },

    startAutoRefresh() {
        if (this.refreshInterval) clearInterval(this.refreshInterval);
        this.refreshInterval = setInterval(() => this.loadInitialStats(), 15000);
    },

    cleanup() {
        if (this.refreshInterval) clearInterval(this.refreshInterval);
        if (this.ws) this.ws.close();
    }
};

document.addEventListener('DOMContentLoaded', () => SystemMonitor.init());