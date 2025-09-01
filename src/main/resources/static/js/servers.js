document.addEventListener('DOMContentLoaded', () => {
    const deleteModal = document.getElementById('delete-modal');
    const closeDeleteModal = document.getElementById('close-delete-modal');
    const cancelDelete = document.getElementById('cancel-delete');
    const confirmDelete = document.getElementById('confirm-delete');
    const deleteServerName = document.getElementById('delete-server-name');
    
    let currentServerId = null;
    let currentServerName = null;

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

    function openDeleteModal(serverId, serverName) {
        currentServerId = serverId;
        currentServerName = serverName;
        deleteServerName.textContent = serverName;
        deleteModal.style.display = 'flex';
    }

    function closeDeleteModalHandler() {
        deleteModal.style.display = 'none';
        currentServerId = null;
        currentServerName = null;
    }

    async function deleteServer(serverId) {
        try {
            const response = await fetch(`/api/servers/${serverId}`, {
                method: 'DELETE'
            });

            const result = await response.json();

            if (result.success) {
                showNotification(`Server "${currentServerName}" deleted successfully`, 'success');
                closeDeleteModalHandler();
                // Remove the server card from the UI
                const serverCard = document.querySelector(`[data-id="${serverId}"]`).closest('.server-card');
                if (serverCard) {
                    serverCard.style.animation = 'slideOut 0.3s ease-in-out';
                    setTimeout(() => {
                        serverCard.remove();
                    }, 300);
                }
            } else {
                showNotification(`Failed to delete server: ${result.error}`, 'error');
            }
        } catch (error) {
            console.error('Error deleting server:', error);
            showNotification('Error deleting server. Please try again.', 'error');
        }
    }

    // Event listeners for delete buttons
    document.addEventListener('click', (e) => {
        if (e.target.closest('.delete-btn')) {
            const deleteBtn = e.target.closest('.delete-btn');
            const serverId = deleteBtn.getAttribute('data-id');
            const serverName = deleteBtn.getAttribute('data-name');
            openDeleteModal(serverId, serverName);
            e.preventDefault();
            e.stopPropagation();
        }
    });

    // Modal event listeners
    closeDeleteModal.addEventListener('click', closeDeleteModalHandler);
    cancelDelete.addEventListener('click', closeDeleteModalHandler);
    
    confirmDelete.addEventListener('click', () => {
        if (currentServerId) {
            deleteServer(currentServerId);
        }
    });

    // Close modal when clicking outside
    deleteModal.addEventListener('click', (e) => {
        if (e.target === deleteModal) {
            closeDeleteModalHandler();
        }
    });

    // Escape key to close modal
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && deleteModal.style.display === 'flex') {
            closeDeleteModalHandler();
        }
    });
});