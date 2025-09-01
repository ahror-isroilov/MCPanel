document.addEventListener('DOMContentLoaded', () => {
    const modal = document.getElementById('install-modal');
    const closeButton = document.querySelector('.close-button');
    const installForm = document.getElementById('install-form');
    const modalTemplateName = document.getElementById('modal-template-name');
    const modalTemplateIdInput = document.getElementById('modal-template-id');
    const instanceNameInput = document.getElementById('instance-name');

    document.querySelectorAll('.install-button').forEach(button => {
        button.addEventListener('click', (e) => {
            const card = e.target.closest('.template-card');
            const templateId = card.dataset.templateId;
            const templateName = card.dataset.templateName;

            modalTemplateName.textContent = templateName;
            modalTemplateIdInput.value = templateId;
            instanceNameInput.value = ''; // Clear previous input
            modal.style.display = 'block';
        });
    });

    closeButton.addEventListener('click', () => {
        modal.style.display = 'none';
    });

    window.addEventListener('click', (e) => {
        if (e.target === modal) {
            modal.style.display = 'none';
        }
    });

    installForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const instanceName = instanceNameInput.value;
        const templateId = modalTemplateIdInput.value;

        if (!instanceName || !templateId) {
            alert('Instance name is required.');
            return;
        }

        try {
            const response = await fetch('/api/servers/install', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ instanceName, templateId }),
            });

            if (response.ok) {
                const data = await response.json();
                window.location.href = `/servers/${data.instanceId}/console`;
            } else {
                const error = await response.json();
                alert(`Error: ${error.message}`);
            }
        } catch (error) {
            console.error('Failed to start installation:', error);
            alert('An error occurred while starting the installation.');
        }
    });

    // Filter Logic
    const filterButtons = document.querySelectorAll('.filter-btn');
    const templateCards = document.querySelectorAll('.template-card');

    filterButtons.forEach(button => {
        button.addEventListener('click', () => {
            // Update active button
            filterButtons.forEach(btn => btn.classList.remove('active'));
            button.classList.add('active');

            const filter = button.dataset.filter;

            // Show/hide cards
            templateCards.forEach(card => {
                if (filter === 'all' || card.dataset.type === filter) {
                    card.style.display = 'flex';
                } else {
                    card.style.display = 'none';
                }
            });
        });
    });
});