document.addEventListener('DOMContentLoaded', () => {
    const titles = [
        "Forge Your World",
        "Craft Your Realm",
        "Build Your Universe",
        "Start a New Adventure",
        "Create Your Legacy",
        "Shape Your Destiny",
        "Weave Your Saga",
        "Sculpt Your Reality",
        "Mold Your Kingdom",
        "Define Your Era",
        "Rule Your Horizon",
        "Conquer Your Fate",
        "Etch Your Legend",
        "Master Your Domain",
        "Ignite Your Odyssey",
        "Unfold Your Epic",
        "Chart a New Frontier",
        "Begin Your Genesis",
        "Launch Your Saga",
        "Author Your Myth",
        "Dream a New Dawn",
        "An Echo of Your Will",
        "Your Universe Awaits"
    ];
    let currentTitleIndex = 0;
    const pageTitle = document.querySelector('.page-title');

    if (pageTitle) {
        setInterval(() => {
            currentTitleIndex = (currentTitleIndex + 1) % titles.length;

            pageTitle.classList.add('fade-out');

            setTimeout(() => {
                pageTitle.textContent = titles[currentTitleIndex];
                pageTitle.classList.remove('fade-out');
            }, 500);

        }, 4000);
    }

    const modal = document.getElementById('install-modal');
    const closeButton = document.querySelector('.close-btn'); // or document.getElementById('close-delete-modal')
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

    // Fixed: Add null check for closeButton
    if (closeButton) {
        closeButton.addEventListener('click', () => {
            modal.style.display = 'none';
        });
    }

    window.addEventListener('click', (e) => {
        if (e.target === modal) {
            modal.style.display = 'none';
        }
    });

    if (installForm) {
        installForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const instanceName = instanceNameInput.value;
            const templateId = modalTemplateIdInput.value;

            if (!instanceName || !templateId) {
                alert('Instance name is required.');
                return;
            }
            modal.style.display = 'none';
            showNotification('Creating server instance...', 'info');
            try {
                const response = await fetch('/api/servers/create-and-install', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({ instanceName, templateId }),
                });

                const data = await response.json();

                if (response.ok && data.success) {
                    showNotification('Redirecting to console...', 'success');
                    window.location.href = `/servers/${data.instanceId}/console`;
                } else {
                    alert(`Error: ${data.error || 'Failed to create server instance'}`);
                }
            } catch (error) {
                console.error('Failed to create server instance:', error);
                alert('An error occurred while creating the server instance.');
            }
        });
    }

    const filterButtons = document.querySelectorAll('.filter-btn');
    const templateCards = document.querySelectorAll('.template-card');

    console.log('Filter buttons found:', filterButtons.length);
    console.log('Template cards found:', templateCards.length);

    filterButtons.forEach(button => {
        button.addEventListener('click', () => {
            filterButtons.forEach(btn => btn.classList.remove('active'));
            button.classList.add('active');

            const filter = button.dataset.filter;
            templateCards.forEach(card => {
                const cardType = card.dataset.type;
                if (filter === 'all' || cardType === filter) {
                    card.style.display = 'flex';
                } else {
                    card.style.display = 'none';
                }
            });
        });
    });

    function showNotification(message, type = 'success') {
        const container = document.getElementById('notification-container');
        if (!container) {
            console.log('Notification: ' + message);
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
});