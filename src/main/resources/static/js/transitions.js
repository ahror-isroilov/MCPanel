document.addEventListener('DOMContentLoaded', () => {
    const wrapper = document.querySelector('.page-transition-wrapper');

    if (wrapper) {
        wrapper.classList.add('is-visible');
    }

    const internalLinks = document.querySelectorAll('a');

    internalLinks.forEach(link => {
        link.addEventListener('click', function (e) {
            const href = this.getAttribute('href');

            if (href && href.startsWith('/') && !this.getAttribute('target')) {
                e.preventDefault(); // Stop the browser from navigating instantly

                if (wrapper) {
                    wrapper.classList.remove('is-visible');
                }

                setTimeout(() => {
                    window.location.href = href;
                }, 150); // This duration should match the CSS transition time
            }
        });
    });
});
