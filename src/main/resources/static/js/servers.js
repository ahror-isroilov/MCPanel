

function setButtonLoading(button, loading) {
    if (!button) return;
    button.disabled = loading;
    if (loading) {
        button.classList.add('loading');
        const originalText = button.textContent;
        button.dataset.originalText = originalText;
        button.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
    } else {
        button.classList.remove('loading');
        const originalText = button.dataset.originalText || 'Action';
        button.innerHTML = originalText;
        delete button.dataset.originalText;
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const serverGrid = document.querySelector('.server-grid');
    const onlineServerCount = document.getElementById('online-server-count');
    const searchBar = document.getElementById('search-bar');

    let serverData = [];

    const fetchServerData = async () => {
        try {
            const response = await fetch('/api/servers/statuses');
            if (!response.ok) {
                throw new Error('Failed to fetch server statuses');
            }
            const result = await response.json();
            if (result.success) {
                serverData = result.data;
                renderServerCards();
            }
        } catch (error) {
            console.error('Error fetching server statuses:', error);
        }
    };

    const renderServerCards = () => {
        const searchTerm = searchBar.value.toLowerCase();
        const filteredData = serverData.filter(server => server.name && server.name.toLowerCase().includes(searchTerm));

        const onlineCount = serverData.filter(s => s.online).length;
        onlineServerCount.textContent = onlineCount;

        const existingCards = Array.from(serverGrid.querySelectorAll('[data-server-id]'));

        existingCards.forEach(card => {
            const serverId = card.dataset.serverId;
            const server = serverData.find(s => s.instanceId == serverId);

            if (server && filteredData.some(s => s.instanceId == serverId)) {
                updateServerCard(card, server);
                card.style.display = 'block';
            } else {
                card.style.display = 'none';
            }
        });

        filteredData.forEach(server => {
            const existingCard = serverGrid.querySelector(`[data-server-id="${server.instanceId}"]`);
            if (!existingCard) {
                const newCard = createServerCard(server);
                const addServerCard = serverGrid.querySelector('.add-server-card');
                if (addServerCard) {
                    serverGrid.insertBefore(newCard, addServerCard);
                } else {
                    serverGrid.appendChild(newCard);
                }
            }
        });
    };

    const updateServerCard = (card, server) => {
        const onlineStatus = server.online ? 'online' : 'offline';
        card.querySelector('.status-indicator').className = `status-indicator ${onlineStatus}`;
        card.querySelector('.status-text').textContent = onlineStatus;
        
        const startBtn = card.querySelector('.start-btn');
        const stopBtn = card.querySelector('.stop-btn');
        
        if (!startBtn.classList.contains('loading')) {
            startBtn.style.display = server.online ? 'none' : 'inline-flex';
        }
        if (!stopBtn.classList.contains('loading')) {
            stopBtn.style.display = server.online ? 'inline-flex' : 'none';
        }

        const ramUsageEl = card.querySelector('.ram-usage-value');
        const diskUsageEl = card.querySelector('.disk-usage-value');
        const portEl = card.querySelector('.server-port');

        if (portEl) {
            portEl.textContent = server.port;
        }

        if (ramUsageEl) {
            if (server.allocatedRam >= 1024) {
                const instanceRamUsageGB = (server.instanceRamUsage / 1024).toFixed(2);
                const allocatedRamGB = (server.allocatedRam / 1024).toFixed(2);
                ramUsageEl.textContent = server.online ? `${instanceRamUsageGB} GB / ${allocatedRamGB} GB` : 'Offline';
            } else {
                ramUsageEl.textContent = server.online ? `${server.instanceRamUsage.toFixed(2)} MB / ${server.allocatedRam.toFixed(2)} MB` : 'Offline';
            }
        }

        if (diskUsageEl) {
            diskUsageEl.textContent = `${server.instanceDiskUsage.toFixed(2)} MB`;
        }
    };

    const createServerCard = (server) => {
        const card = document.createElement('div');
        card.className = 'server-card';
        card.dataset.serverId = server.instanceId;

        const onlineStatus = server.online ? 'online' : 'offline';
        const startBtnDisplay = server.online ? 'none' : 'inline-flex';
        const stopBtnDisplay = server.online ? 'inline-flex' : 'none';

        const version = server.version || 'Unknown';
        const serverType = server.serverType || 'Paper';

        let ramUsage;
        if (server.allocatedRam >= 1024) {
            const instanceRamUsageGB = (server.instanceRamUsage / 1024).toFixed(2);
            const allocatedRamGB = (server.allocatedRam / 1024).toFixed(2);
            ramUsage = server.online ? `${instanceRamUsageGB} GB / ${allocatedRamGB} GB` : 'Offline';
        } else {
            ramUsage = server.online ? `${server.instanceRamUsage.toFixed(2)} MB / ${server.allocatedRam.toFixed(2)} MB` : 'Offline';
        }
        const diskUsage = `${server.instanceDiskUsage.toFixed(2)} MB`;

        card.innerHTML = `
        <div class="server-card-content">
            <div class="card-header">
                <div class="header-top">
                    <div class="status-indicator ${onlineStatus}"></div>
                    <div class="header-content">
                        <a href="/servers/${server.instanceId}/server" class="server-name">${server.name}</a>
                    </div>
                    <div class="card-actions">
                        <button class="start-btn" style="display: ${startBtnDisplay};">Start</button>
                        <button class="stop-btn" style="display: ${stopBtnDisplay};">Stop</button>
                        <button class="delete-btn" title="Delete server">
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                <polyline points="3,6 5,6 21,6"></polyline>
                                <path d="m19,6v14a2,2 0,0 1,-2,2H7a2,2 0,0 1,-2,-2V6m3,0V4a2,2 0,0 1,2,-2h4a2,2 0,0 1,2,2v2"></path>
                                <line x1="10" y1="11" x2="10" y2="17"></line>
                                <line x1="14" y1="11" x2="14" y2="17"></line>
                            </svg>
                        </button>
                    </div>
                </div>
                <div class="server-tags">
                    ${version !== 'Unknown' ? `<span class="version-tag">${version}</span>` : ''}
                    ${serverType !== 'Unknown' ? `<span class="server-type-tag">${serverType}</span>` : ''}
                </div>
            </div>
            <div class="card-body">
                <div class="server-details">
                    <div class="detail-item">
                        <span class="detail-label">Status:</span>
                        <span class="detail-value status-text">${onlineStatus}</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">Port:</span>
                        <span class="detail-value server-port">${server.port}</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">RAM Usage:</span>
                        <span class="detail-value ram-usage-value">${ramUsage}</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">Disk Usage:</span>
                        <span class="detail-value disk-usage-value">${diskUsage}</span>
                    </div>
                </div>
            </div>
        </div>
    `;
        return card;
    };

    const handleServerAction = async (instanceId, action, button) => {
        setButtonLoading(button, true);
        
        try {
            const response = await fetch(`/api/servers/${instanceId}/${action}`, {method: 'POST'});
            const result = await response.json();
            if (result.success) {
                showNotification(`Server ${action} command issued successfully.`, 'success');
                setTimeout(() => {
                    fetchServerData();
                    setButtonLoading(button, false);
                }, 2500);
            } else {
                showNotification(`Failed to ${action} server: ${result.error}`, 'error');
                setButtonLoading(button, false);
            }
        } catch (error) {
            console.error(`Error during ${action}:`, error);
            showNotification(`Error during ${action}: ${error.message}`, 'error');
            setButtonLoading(button, false);
        }
    };

    serverGrid.addEventListener('click', (event) => {
        const target = event.target.closest('button');
        if (target) {
            const card = target.closest('.server-card');
            const instanceId = card.dataset.serverId;
            if (target.classList.contains('start-btn')) {
                handleServerAction(instanceId, 'start', target);
            } else if (target.classList.contains('stop-btn')) {
                handleServerAction(instanceId, 'stop', target);
            } else if (target.classList.contains('delete-btn')) {
                const deleteModal = document.getElementById('delete-modal');
                const deleteServerName = document.getElementById('delete-server-name');
                const confirmDeleteBtn = document.getElementById('confirm-delete');

                const server = serverData.find(s => s.instanceId == instanceId);
                if (server) {
                    deleteServerName.textContent = server.name;
                    confirmDeleteBtn.dataset.instanceId = instanceId;
                    deleteModal.style.display = 'flex';
                }
            }
        }
    });

    searchBar.addEventListener('input', renderServerCards);

    const deleteModal = document.getElementById('delete-modal');
    const closeDeleteModalBtn = document.getElementById('close-delete-modal');
    const cancelDeleteBtn = document.getElementById('cancel-delete');
    const confirmDeleteBtn = document.getElementById('confirm-delete');

    closeDeleteModalBtn.addEventListener('click', () => deleteModal.style.display = 'none');
    cancelDeleteBtn.addEventListener('click', () => deleteModal.style.display = 'none');

    confirmDeleteBtn.addEventListener('click', async (event) => {
        const instanceId = event.target.dataset.instanceId;
        try {
            setButtonLoading(confirmDeleteBtn, true);
            const response = await fetch(`/api/servers/${instanceId}`, {method: 'DELETE'});
            const result = await response.json();
            if (result.success) {
                showNotification('Server deleted successfully.', 'success');
                deleteModal.style.display = 'none';
                fetchServerData();
            } else {
                showNotification(`Failed to delete server: ${result.error}`, 'error');
            }
        } catch (error) {
            console.error('Error deleting server:', error);
            showNotification(`Error deleting server: ${error.message}`, 'error');
        } finally {
            setButtonLoading(confirmDeleteBtn, false);
        }
    });

    fetchServerData();
    setInterval(fetchServerData, 5000);
});
