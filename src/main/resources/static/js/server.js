let serverStatus = null;
let instanceId = null;
let serverProperties = {};
let propertyValidations = {};
let instance = null;

async function loadInstanceData() {
    try {
        const response = await fetch(`/api/servers/${instanceId}`);
        const result = await response.json();
        if (result.success) {
            instance = result.data;
        }
    } catch (error) {
        console.error('Error loading instance data:', error);
    }
}

function setButtonLoading(button, loading) {
    if (!button) return;
    button.disabled = loading;
    if (loading) {
        button.classList.add('loading');
        const icon = button.querySelector('i');
        if (icon) {
            icon.className = 'fas fa-spinner fa-spin';
        }
    } else {
        button.classList.remove('loading');
        const action = button.id.split('-')[0];
        const icon = button.querySelector('i');
        if (icon) {
            if (action === 'start') icon.className = 'fas fa-play';
            if (action === 'restart') icon.className = 'fas fa-sync-alt';
            if (action === 'stop') icon.className = 'fas fa-stop';
        }
    }
}

async function executeServerAction(action, button) {
    setButtonLoading(button, true);
    try {
        const response = await fetch(`/api/servers/${instanceId}/${action}`, { method: 'POST' });
        const result = await response.json();
        if (result.success) {
            showNotification(`Server ${action} command issued.`, 'success');
            setTimeout(loadServerStatus, 2000);
            if (action === 'backup') {
                setButtonLoading(button, false);
            }
        } else {
            showNotification(`Failed to ${action} server: ${result.error}`, 'error');
            setButtonLoading(button, false);
        }
    } catch (error) {
        showNotification(`Error during ${action}: ${error.message}`, 'error');
        setButtonLoading(button, false);
    }
}

function updateServerStatusUI(status) {
    serverStatus = status;
    const isOnline = status.online;

    const statusTag = document.getElementById('server-status-tag');
    statusTag.textContent = isOnline ? 'Running' : 'Stopped';
    statusTag.className = `status-tag ${isOnline ? 'status-running' : 'status-stopped'}`;

    document.getElementById('start-server-btn').style.display = isOnline ? 'none' : 'flex';
    document.getElementById('restart-server-btn').style.display = isOnline ? 'flex' : 'none';
    document.getElementById('stop-server-btn').style.display = isOnline ? 'flex' : 'none';

    document.getElementById('server-status-indicator').className = `status-dot ${isOnline ? 'online' : ''}`;
    document.getElementById('server-online').textContent = isOnline ? 'Online' : 'Offline';
    document.getElementById('players-count').textContent = `${status.playersOnline}/${status.maxPlayers}`;
    document.getElementById('server-tps').textContent = status.tps ? status.tps.toFixed(1) : '0.0';
    document.getElementById('server-uptime').textContent = status.uptime || 'N/A';
    document.getElementById('server-version').textContent = status.version || 'N/A';
    document.getElementById('server-world').textContent = status.worldName || 'N/A';
    document.getElementById('cpu-usage').textContent = `${Math.round(status.cpuUsage || 0)}%`;
    if (status.allocatedRam >= 1024) {
        const instanceRamUsageGB = (status.instanceRamUsage / 1024).toFixed(2);
        const allocatedRamGB = (status.allocatedRam / 1024).toFixed(2);
        document.getElementById('ram-usage').textContent = status.online ? `${instanceRamUsageGB} GB / ${allocatedRamGB} GB` : 'Offline';
    } else {
        document.getElementById('ram-usage').textContent = status.online ? `${status.instanceRamUsage.toFixed(2)} MB / ${status.allocatedRam.toFixed(2)} MB` : 'Offline';
    }
    document.getElementById('disk-usage').textContent = `${status.diskUsage.toFixed(2)} MB`;
    updatePlayerList(status.onlinePlayers || []);
}

function updatePlayerList(players) {
    const playerList = document.getElementById('player-list');
    if (!players || players.length === 0) {
        playerList.innerHTML = `<div class="empty-state">No players online</div>`;
        return;
    }
    playerList.innerHTML = players.map(player => `
        <div class="player-item">
            <div class="player-info">
                <div class="player-avatar">${player.charAt(0).toUpperCase()}</div>
                <span class="player-name">${player}</span>
            </div>
        </div>
    `).join('');
}

async function loadServerStatus() {
    try {
        const response = await fetch(`/api/servers/${instanceId}/status`);
        const result = await response.json();
        if (result.success) {
            updateServerStatusUI(result.data);
        }
    } catch (error) {
        console.error('Error loading server status:', error);
    }
}

async function loadServerProperties() {
    try {
        const [propertiesResponse, validationsResponse] = await Promise.all([
            fetch(`/api/servers/${instanceId}/properties`),
            fetch(`/api/servers/${instanceId}/properties/validations`)
        ]);

        const propertiesResult = await propertiesResponse.json();
        const validationsResult = await validationsResponse.json();

        if (propertiesResult.success && validationsResult.success) {
            serverProperties = propertiesResult.data;
            propertyValidations = validationsResult.data;
            renderPropertiesEditor();
        } else {
            showNotification('Failed to load server properties', 'error');
        }
    } catch (error) {
        console.error('Error loading server properties:', error);
        showNotification('Error loading server properties', 'error');
    }
}

function renderPropertiesEditor() {
    const worldEditor = document.getElementById('world-properties-editor');
    const serverEditor = document.getElementById('server-properties-editor');
    if (!worldEditor || !serverEditor) return;

    const worldKeys = ['level-name', 'level-seed', 'level-type', 'generator-settings', 'difficulty', 'gamemode', 'pvp', 'allow-flight', 'spawn-animals', 'spawn-monsters', 'spawn-npcs', 'view-distance', 'simulation-distance'];
    
    const propertyKeys = Object.keys(propertyValidations);
    
    if (propertyKeys.length === 0) {
        worldEditor.innerHTML = '<div class="empty-state">No world properties found</div>';
        serverEditor.innerHTML = '<div class="empty-state">No server properties found</div>';
        return;
    }

    const worldPropertiesHtml = [];
    const serverPropertiesHtml = [];

    // Add memory allocation to server properties
    const allocatedMemory = instance ? instance.allocatedMemory : '';
    const allocatedMemoryInGb = allocatedMemory.toUpperCase().endsWith('G') ? allocatedMemory : (parseInt(allocatedMemory) / 1024).toFixed(2) + 'G';
    serverPropertiesHtml.push(`
        <div class="property-item-editor">
            <div class="property-info">
                <div class="property-name">allocated-memory</div>
                <div class="property-description">Allocated Memory (e.g: 2G, 512M)</div>
            </div>
            <input type="text" id="allocated-memory" name="memory" value="${allocatedMemoryInGb}" class="property-input" data-property="allocated-memory">
        </div>
    `);

    propertyKeys.forEach(key => {
        const validation = propertyValidations[key];
        const currentValue = serverProperties[key] || '';
        
        let inputHtml = '';
        
        if (validation.type === 'boolean') {
            const isChecked = currentValue.toLowerCase() === 'true';
            inputHtml = `
                <div class="toggle-switch-container">
                    <div class="toggle-switch ${isChecked ? 'active' : ''}" data-property="${key}">
                        <div class="toggle-slider"></div>
                    </div>
                </div>
            `;
        } else if (validation.type === 'enum' && validation.min) {
            const options = validation.min;
            inputHtml = `
                <select class="property-select-enhanced" data-property="${key}">
                    ${options.map(option => 
                        `<option value="${option}" ${currentValue === option ? 'selected' : ''}>${option}</option>`
                    ).join('')}
                </select>
            `;
        } else {
            inputHtml = `
                <input type="${validation.type === 'integer' ? 'number' : 'text'}" 
                       class="property-input" 
                       data-property="${key}"
                       value="${currentValue}"
                       ${validation.min !== null && validation.type === 'integer' ? `min="${validation.min}"` : ''}
                       ${validation.max !== null && validation.type === 'integer' ? `max="${validation.max}"` : ''}
                />
            `;
        }

        if (key === 'white-list' && currentValue.toLowerCase() === 'true') {
            inputHtml += `
                <button class="action-btn btn-secondary compact" id="edit-whitelist-btn" style="margin-left: 8px;">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                    </svg>
                    <span>Edit</span>
                </button>
            `;
        }

        const requiresRestart = ['server-port', 'server-ip', 'online-mode',
                                'enable-rcon', 'rcon.port', 'rcon.password',
                                'level-name', 'level-seed', 'level-type', 'generator-settings'].includes(key);

        const propertyHtml = `
            <div class="property-item-editor">
                <div class="property-info">
                    <div class="property-name">${key}</div>
                    <div class="property-description">${validation.description}</div>
                    ${requiresRestart ? '<div class="property-restart-warning"><i class="fas fa-exclamation-triangle"></i> Requires server restart</div>' : ''}
                </div>
                ${inputHtml}
            </div>
        `;

        if (worldKeys.includes(key)) {
            worldPropertiesHtml.push(propertyHtml);
        } else {
            serverPropertiesHtml.push(propertyHtml);
        }
    });

    worldEditor.innerHTML = worldPropertiesHtml.join('');
    serverEditor.innerHTML = serverPropertiesHtml.join('');

    attachPropertyEventListeners();
}

function attachPropertyEventListeners() {
    document.querySelectorAll('.property-input, .property-select-enhanced').forEach(input => {
        input.addEventListener('change', (e) => {
            e.target.classList.add('changed');
        });
    });

    document.querySelectorAll('.toggle-switch').forEach(toggle => {
        toggle.addEventListener('click', (e) => {
            toggle.classList.toggle('active');
            toggle.classList.add('changed');
        });
    });

    const editWhitelistBtn = document.getElementById('edit-whitelist-btn');
    if (editWhitelistBtn) {
        editWhitelistBtn.addEventListener('click', openWhitelistModal);
    }
}

async function saveProperties(propertyType) {
    const editor = document.getElementById(`${propertyType}-properties-editor`);
    const changedInputs = editor.querySelectorAll('.changed');
    const properties = {};
    let memory = null;

    changedInputs.forEach(input => {
        const propertyKey = input.dataset.property;
        let value;

        if (input.classList.contains('toggle-switch')) {
            value = input.classList.contains('active') ? 'true' : 'false';
        } else {
            value = input.value;
        }

        if (propertyKey === 'allocated-memory') {
            memory = value;
        } else {
            properties[propertyKey] = value;
        }
    });

    const button = document.getElementById(`save-${propertyType}-properties`);
    setButtonLoading(button, true);

    try {
        if (Object.keys(properties).length > 0) {
            const response = await fetch(`/api/servers/${instanceId}/properties`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(properties)
            });

            const result = await response.json();
            if (!result.success) {
                showNotification(`Failed to update properties: ${result.error}`, 'error');
                loadServerProperties(); // Reload to show original values
                return;
            }
        }

        if (memory) {
            const memoryResponse = await fetch(`/api/servers/${instanceId}/memory`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: `memory=${encodeURIComponent(memory)}`
            });

            const memoryResult = await memoryResponse.json();
            if (!memoryResult.success) {
                showNotification(`Failed to update memory: ${memoryResult.error}`, 'error');
                loadServerProperties(); // Reload to show original values
                return;
            }
            if (instance) {
                instance.allocatedMemory = memory;
            }
        }

        showNotification('Properties saved successfully.', 'success');
        changedInputs.forEach(input => input.classList.remove('changed'));
        loadServerProperties();
        loadServerStatus();

    } catch (error) {
        showNotification(`Error saving properties: ${error.message}`, 'error');
        loadServerProperties();
    } finally {
        setButtonLoading(button, false);
    }
}

async function refreshPlayerList() {
    try {
        const response = await fetch(`/api/servers/${instanceId}/players`);
        const result = await response.json();
        if (result.success) {
            updatePlayerList(result.data.map(player => player.name || player));
        }
    } catch (error) {
        console.error('Error refreshing player list:', error);
    }
}

async function openWhitelistModal() {
    const modal = document.getElementById('whitelist-modal');
    if (modal) {
        modal.style.display = 'flex';
        await loadWhitelistInModal();
    }
}

function closeWhitelistModal() {
    const modal = document.getElementById('whitelist-modal');
    if (modal) {
        modal.style.display = 'none';
        const input = document.getElementById('whitelist-player-input');
        if (input) input.value = '';
    }
}

async function loadWhitelistInModal() {
    try {
        const response = await fetch(`/api/servers/${instanceId}/whitelist`);
        const result = await response.json();
        
        if (result.success) {
            renderWhitelistInModal(result.data);
        } else {
            showNotification('Failed to load whitelist', 'error');
        }
    } catch (error) {
        console.error('Error loading whitelist:', error);
        showNotification('Error loading whitelist', 'error');
    }
}

function renderWhitelistInModal(whitelistEntries) {
    const container = document.getElementById('whitelist-container-modal');
    if (!container) return;
    
    if (!whitelistEntries || whitelistEntries.length === 0) {
        container.innerHTML = '<div class="empty-state">No players in whitelist</div>';
        return;
    }
    
    container.innerHTML = whitelistEntries.map(entry => `
        <div class="whitelist-item">
            <div class="whitelist-player-info">
                <div class="whitelist-player-avatar">${entry.name.charAt(0).toUpperCase()}</div>
                <div>
                    <div class="whitelist-player-name">${entry.name}</div>
                    ${entry.uuid ? `<div class="whitelist-player-uuid">${entry.uuid}</div>` : ''}
                </div>
            </div>
            <button class="whitelist-remove-btn" data-player="${entry.name}" title="Delete player">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <polyline points="3,6 5,6 21,6"></polyline>
                                <path d="m19,6v14a2,2 0,0 1,-2,2H7a2,2 0,0 1,-2,-2V6m3,0V4a2,2 0,0 1,2,-2h4a2,2 0,0 1,2,2v2"></path>
                                <line x1="10" y1="11" x2="10" y2="17"></line>
                                <line x1="14" y1="11" x2="14" y2="17"></line>
                            </svg>
            </button>
        </div>
    `).join('');
    
    container.querySelectorAll('.whitelist-remove-btn').forEach(btn => {
        btn.addEventListener('click', async () => {
            await removeFromWhitelist(btn.dataset.player);
        });
    });
}

async function addToWhitelist(playerName) {
    if (!playerName || !playerName.trim()) {
        showNotification('Player name is required', 'error');
        return;
    }
    
    try {
        const response = await fetch(`/api/servers/${instanceId}/whitelist`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: `playerName=${encodeURIComponent(playerName.trim())}`
        });
        
        const result = await response.json();
        
        if (result.success) {
            showNotification(`${playerName} added to whitelist`, 'success');
            await loadWhitelistInModal();
            const input = document.getElementById('whitelist-player-input');
            if (input) input.value = '';
        } else {
            showNotification(`Failed to add ${playerName} to whitelist: ${result.error}`, 'error');
        }
    } catch (error) {
        console.error('Error adding to whitelist:', error);
        showNotification(`Error adding ${playerName} to whitelist`, 'error');
    }
}

async function removeFromWhitelist(playerName) {
    try {
        const response = await fetch(`/api/servers/${instanceId}/whitelist/${encodeURIComponent(playerName)}`, {
            method: 'DELETE'
        });
        
        const result = await response.json();
        
        if (result.success) {
            showNotification(`${playerName} removed from whitelist`, 'success');
            await loadWhitelistInModal();
        } else {
            showNotification(`Failed to remove ${playerName} from whitelist: ${result.error}`, 'error');
        }
    } catch (error) {
        console.error('Error removing from whitelist:', error);
        showNotification(`Error removing ${playerName} from whitelist`, 'error');
    }
}

document.addEventListener('DOMContentLoaded', async () => {
    instanceId = document.body.dataset.id;
    if (!instanceId) {
        console.error("Instance ID not found!");
        return;
    }

    await loadInstanceData();
    if (!instance) {
        showNotification('Failed to load server data.', 'error');
        return;
    }

    const startBtn = document.getElementById('start-server-btn');
    const restartBtn = document.getElementById('restart-server-btn');
    const stopBtn = document.getElementById('stop-server-btn');
    const backupBtn = document.getElementById('backup-btn');

    startBtn.addEventListener('click', () => executeServerAction('start', startBtn));
    restartBtn.addEventListener('click', () => executeServerAction('restart', restartBtn));
    stopBtn.addEventListener('click', () => executeServerAction('stop', stopBtn));
    if (backupBtn) {
        backupBtn.addEventListener('click', () => executeServerAction('backup', backupBtn));
    }

    const saveWorldPropsBtn = document.getElementById('save-world-properties');
    if (saveWorldPropsBtn) {
        saveWorldPropsBtn.addEventListener('click', () => saveProperties('world'));
    }

    const saveServerPropsBtn = document.getElementById('save-server-properties');
    if (saveServerPropsBtn) {
        saveServerPropsBtn.addEventListener('click', () => saveProperties('server'));
    }

    const closeWhitelistModalBtn = document.getElementById('close-whitelist-modal');
    if (closeWhitelistModalBtn) {
        closeWhitelistModalBtn.addEventListener('click', closeWhitelistModal);
    }

    const addWhitelistBtn = document.getElementById('add-whitelist-btn');
    const whitelistPlayerInput = document.getElementById('whitelist-player-input');
    
    if (addWhitelistBtn && whitelistPlayerInput) {
        addWhitelistBtn.addEventListener('click', () => {
            addToWhitelist(whitelistPlayerInput.value);
        });
        
        whitelistPlayerInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                addToWhitelist(whitelistPlayerInput.value);
            }
        });
    }

    const whitelistModal = document.getElementById('whitelist-modal');
    if (whitelistModal) {
        whitelistModal.addEventListener('click', (e) => {
            if (e.target === whitelistModal) {
                closeWhitelistModal();
            }
        });
    }

    loadServerStatus();
    loadServerProperties();

    setInterval(loadServerStatus, 10000);
});