document.addEventListener('DOMContentLoaded', () => {
    const titles = [
        "Forge Your World",
        "Craft Your Realm",
        "Build Your Universe",
        "Start a New Adventure",
        "Create Your Legacy",
        "Shape Your Destiny",
        "Architect Your Empire",
        "Weave Your Saga",
        "Sculpt Your Reality",
        "Design Your Existence",
        "Mold Your Kingdom",
        "Command Your Dominion",
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
        "A Tapestry of Your Making",
        "An Echo of Your Will",
        "Your Universe Awaits"
    ];
    const pageTitle = document.querySelector('.page-title');

    if (pageTitle) {
        let currentTitleIndex = 0;

        setInterval(() => {
            let newTitleIndex;
            do {
                newTitleIndex = Math.floor(Math.random() * titles.length);
            } while (newTitleIndex === currentTitleIndex);

            currentTitleIndex = newTitleIndex;

            pageTitle.classList.add('fade-out');

            setTimeout(() => {
                pageTitle.textContent = titles[currentTitleIndex];
                pageTitle.classList.remove('fade-out');
            }, 500);

        }, 4000);
    }

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

    const filterButtons = document.querySelectorAll('.filter-btn');
    const templateCards = document.querySelectorAll('.template-card');

    filterButtons.forEach(button => {
        button.addEventListener('click', () => {
            filterButtons.forEach(btn => btn.classList.remove('active'));
            button.classList.add('active');

            const filter = button.dataset.filter;

            templateCards.forEach(card => {
                if (filter === 'all' || card.dataset.type === filter) {
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