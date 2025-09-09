document.addEventListener('DOMContentLoaded', () => {
    const changeUsernameForm = document.getElementById('change-username-form');
    const changePasswordForm = document.getElementById('change-password-form');
    const deleteAccountBtn = document.getElementById('delete-account-btn');
    const notificationContainer = document.getElementById('notification-container');

    const deleteModal = document.getElementById('delete-account-modal');
    const closeDeleteModalBtn = document.getElementById('close-delete-modal');
    const cancelDeleteBtn = document.getElementById('cancel-delete');
    const confirmDeleteBtn = document.getElementById('confirm-delete');

    function showNotification(message, type = 'success') {
        const notification = document.createElement('div');
        notification.className = `notification alert-${type}`;
        notification.textContent = message;
        notificationContainer.appendChild(notification);
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

    changeUsernameForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const newUsername = e.target.elements.newUsername.value;
        const response = await fetch('/api/user/username', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `newUsername=${encodeURIComponent(newUsername)}`
        });
        const result = await response.json();
        if (result.success) {
            showNotification(result.message, 'success');
        } else {
            showNotification(result.error, 'error');
        }
    });

    changePasswordForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const oldPassword = e.target.elements.oldPassword.value;
        const newPassword = e.target.elements.newPassword.value;
        const response = await fetch('/api/user/password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ oldPassword, newPassword })
        });
        const result = await response.json();
        if (result.success) {
            showNotification(result.message, 'success');
            e.target.reset();
        } else {
            showNotification(result.error, 'error');
        }
    });

    deleteAccountBtn.addEventListener('click', () => {
        deleteModal.style.display = 'flex';
    });

    closeDeleteModalBtn.addEventListener('click', () => {
        deleteModal.style.display = 'none';
    });

    cancelDeleteBtn.addEventListener('click', () => {
        deleteModal.style.display = 'none';
    });

    deleteModal.addEventListener('click', (e) => {
        if (e.target === deleteModal) {
            deleteModal.style.display = 'none';
        }
    });

    confirmDeleteBtn.addEventListener('click', async () => {
        try {
            setButtonLoading(confirmDeleteBtn, true);
            const response = await fetch('/api/user/delete', {
                method: 'DELETE',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest'
                }
            });

            const result = await response.json();

            if (result.success) {
                showNotification(result.message, 'success');
                deleteModal.style.display = 'none';

                setTimeout(() => {
                    window.location.href = result.data || '/login';
                }, 1000);
            } else {
                showNotification(result.error || 'Failed to delete account', 'error');
                setButtonLoading(confirmDeleteBtn, false);
            }
        } catch (error) {
            console.error('Error deleting account:', error);
            showNotification('An error occurred while deleting the account', 'error');
            setButtonLoading(confirmDeleteBtn, false);
        }
    });
});
