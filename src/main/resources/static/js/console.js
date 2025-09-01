document.addEventListener('DOMContentLoaded', () => {
    const instanceId = document.body.dataset.instanceId;
    const consoleOutput = document.getElementById('console-output');
    const commandInput = document.getElementById('command-input');
    
    // Header elements
    const startServerBtn = document.getElementById('start-server-btn');
    const restartServerBtn = document.getElementById('restart-server-btn');
    const stopServerBtn = document.getElementById('stop-server-btn');
    const serverStatusTag = document.getElementById('server-status-tag');

    // Status bar elements
    const connectionDot = document.getElementById('connection-dot');
    const connectionStatusEl = document.getElementById('connection-status');
    const lineCountEl = document.getElementById('line-count');
    const serverStatusEl = document.getElementById('server-status');

    let socket;
    let lineCount = 0;
    let autoScroll = true;
    let commandHistory = [];
    let historyIndex = -1;

    function showNotification(message, type = 'success') {
        const container = document.getElementById('notification-container');
        if (!container) return;
        
        const notification = document.createElement('div');
        notification.className = `notification alert-${type}`;
        notification.innerHTML = `<span>${message}</span>`;
        container.appendChild(notification);
        setTimeout(() => {
            notification.classList.add('removing');
            setTimeout(() => notification.remove(), 300);
        }, 4000);
    }

    function setButtonLoading(button, loading) {
        if (!button) return;
        button.disabled = loading;
        if (loading) {
            button.classList.add('loading');
            const icon = button.querySelector('i, svg');
            if (icon) {
                if (icon.tagName === 'I') {
                    icon.className = 'fas fa-spinner fa-spin';
                } else if (icon.tagName === 'SVG') {
                    icon.innerHTML = '<path d="M12 2v4m0 12v4m10-10h-4M4 12H0" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>';
                    icon.style.animation = 'spin 1s linear infinite';
                }
            }
        } else {
            button.classList.remove('loading');
            const action = button.id.split('-')[0];
            const icon = button.querySelector('i, svg');
            if (icon && icon.tagName === 'I') {
                if (action === 'start') icon.className = 'fas fa-play';
                if (action === 'restart') icon.className = 'fas fa-sync-alt';
                if (action === 'stop') icon.className = 'fas fa-stop';
            } else if (icon && icon.tagName === 'SVG') {
                if (action === 'start') {
                    icon.innerHTML = '<polygon points="5,3 19,12 5,21" fill="currentColor"/>';
                } else if (action === 'restart') {
                    icon.innerHTML = '<path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/><path d="M3 21v-5h5"/>';
                } else if (action === 'stop') {
                    icon.innerHTML = '<rect x="6" y="6" width="12" height="12" fill="currentColor"/>';
                }
                icon.style.animation = '';
            }
        }
    }

    function connect() {
        const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
        const host = window.location.host;
        socket = new WebSocket(`${protocol}://${host}/ws/console/${instanceId}`);

        socket.onopen = () => {
            updateConnectionStatus(true);
            socket.send(JSON.stringify({ type: 'request_history' }));
        };

        socket.onmessage = (event) => {
            const data = JSON.parse(event.data);
            handleWebSocketMessage(data);
        };

        socket.onclose = () => {
            updateConnectionStatus(false);
            setTimeout(connect, 5000);
        };
    }

    function handleWebSocketMessage(data) {
        if (data.type === 'console' || data.type === 'history') {
            const messages = Array.isArray(data.data) ? data.data : [data.data];
            messages.forEach(appendMessage);
        } else if (data.type === 'status') {
            updateServerStatus(data.data);
        }
    }

    function appendMessage(msg) {
        const line = document.createElement('div');
        line.className = `console-line ${msg.type || 'info'}`;
        
        const timestamp = `[${new Date(msg.timestamp).toLocaleTimeString()}]`;
        const source = `[${msg.source}/${msg.type}]`;
        
        line.innerHTML = `<span class="timestamp">${timestamp}</span> ${source} ${msg.message}`;
        
        consoleOutput.appendChild(line);
        lineCount++;
        updateLineCount();

        if (autoScroll) {
            consoleOutput.scrollTop = consoleOutput.scrollHeight;
        }
    }

    function updateConnectionStatus(isConnected) {
        if (isConnected) {
            connectionDot.style.backgroundColor = '#28a745';
            connectionStatusEl.textContent = 'Connected';
        } else {
            connectionDot.style.backgroundColor = '#dc3545';
            connectionStatusEl.textContent = 'Disconnected';
        }
    }

    function updateLineCount() {
        lineCountEl.textContent = lineCount;
    }

    function updateServerStatus(status) {
        const isOnline = status.online;
        serverStatusEl.textContent = isOnline ? 'Online' : 'Offline';
        
        serverStatusTag.textContent = isOnline ? 'Running' : 'Stopped';
        serverStatusTag.classList.toggle('status-running', isOnline);
        serverStatusTag.classList.toggle('status-stopped', !isOnline);

        setButtonLoading(startServerBtn, false);
        setButtonLoading(restartServerBtn, false);  
        setButtonLoading(stopServerBtn, false);

        startServerBtn.style.display = isOnline ? 'none' : 'flex';
        restartServerBtn.style.display = isOnline ? 'flex' : 'none';
        stopServerBtn.style.display = isOnline ? 'flex' : 'none';
    }

    async function executeServerAction(action, button) {
        setButtonLoading(button, true);
        try {
            const response = await fetch(`/api/servers/${instanceId}/${action}`, { method: 'POST' });
            const result = await response.json();
            if (result.success) {
                showNotification(`Server ${action} command issued.`, 'success');
                setTimeout(() => {
                    if (button.classList.contains('loading')) {
                        setButtonLoading(button, false);
                    }
                }, 5000);
            } else {
                showNotification(`Failed to ${action} server: ${result.error}`, 'error');
                setButtonLoading(button, false);
            }
        } catch (error) {
            showNotification(`Error during ${action}: ${error.message}`, 'error');
            setButtonLoading(button, false);
        }
    }

    commandInput.addEventListener('keydown', e => {
        if (e.key === 'Enter') {
            const command = commandInput.value.trim();
            if (command) {
                if (socket && socket.readyState === WebSocket.OPEN) {
                    socket.send(JSON.stringify({ type: 'command', message: command }));
                    commandHistory.push(command);
                    historyIndex = commandHistory.length;
                    commandInput.value = '';
                }
            }
        } else if (e.key === 'ArrowUp') {
            if (historyIndex > 0) {
                historyIndex--;
                commandInput.value = commandHistory[historyIndex];
                e.preventDefault();
            }
        } else if (e.key === 'ArrowDown') {
            if (historyIndex < commandHistory.length - 1) {
                historyIndex++;
                commandInput.value = commandHistory[historyIndex];
            } else {
                historyIndex = commandHistory.length;
                commandInput.value = '';
            }
        }
    });

    startServerBtn.addEventListener('click', (e) => executeServerAction('start', e.currentTarget));
    restartServerBtn.addEventListener('click', (e) => executeServerAction('restart', e.currentTarget));
    stopServerBtn.addEventListener('click', (e) => executeServerAction('stop', e.currentTarget));

    connect();
});
