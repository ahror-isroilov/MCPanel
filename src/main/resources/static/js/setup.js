document.addEventListener('DOMContentLoaded', function () {
    const form = document.getElementById('setup-form');
    const password = document.getElementById('password');
    const confirmPassword = document.getElementById('confirmPassword');
    const errorMessage = document.getElementById('error-message');

    form.addEventListener('submit', function (event) {
        if (password.value !== confirmPassword.value) {
            event.preventDefault();
            errorMessage.textContent = 'Passwords do not match.';
            errorMessage.style.display = 'block';
        } else {
            errorMessage.style.display = 'none';
        }
    });
});
