document.addEventListener('DOMContentLoaded', () => {
    const changeUsernameForm = document.getElementById('change-username-form');
    const changePasswordForm = document.getElementById('change-password-form');
    const deleteAccountBtn = document.getElementById('delete-account-btn');
    const notificationContainer = document.getElementById('notification-container');

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

    deleteAccountBtn.addEventListener('click', async () => {
        if (confirm('Are you sure you want to delete your account? This action is irreversible.')) {
            const response = await fetch('/api/user/delete', { method: 'DELETE' });
            const result = await response.json();
            if (result.success) {
                showNotification(result.message, 'success');
                setTimeout(() => window.location.href = '/login', 2000);
            } else {
                showNotification(result.error, 'error');
            }
        }
    });
});
