document.addEventListener('DOMContentLoaded', () => {
    const restoreButton = document.getElementById('restore-defaults');
    const modal = document.getElementById('confirmation-modal');
    const confirmButton = document.getElementById('confirm-delete');
    const cancelButton = document.getElementById('cancel-delete');

    if (restoreButton) {
        restoreButton.addEventListener('click', () => {
            modal.style.display = 'flex';
        });
    }

    if (cancelButton) {
        cancelButton.addEventListener('click', () => {
            modal.style.display = 'none';
        });
    }

    if (confirmButton) {
        confirmButton.addEventListener('click', () => {
            fetch('/api/user/delete-all', {
                method: 'DELETE'
            })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    window.location.href = data.data;
                } else {
                    console.error('Error:', data.message);
                    modal.style.display = 'none';
                }
            })
            .catch(error => {
                console.error('Error:', error);
                modal.style.display = 'none';
            });
        });
    }

    window.addEventListener('click', (event) => {
        if (event.target === modal) {
            modal.style.display = 'none';
        }
    });
});
