function showNotification(message, type = 'success') {
    const container = document.getElementById('notification-container');
    if (!container) {
        console.error('Notification container not found!');
        return;
    }
    const notification = document.createElement('div');
    notification.className = `notification alert-${type}`;
    notification.innerHTML = `<span>${message}</span>`;
    container.appendChild(notification);
    setTimeout(() => {
        notification.classList.add('removing');
        setTimeout(() => notification.remove(), 300);
    }, 4000);
}