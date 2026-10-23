document.querySelectorAll('form[data-confirm]').forEach(function (form) {
  form.addEventListener('submit', function (event) {
    if (!window.confirm(form.getAttribute('data-confirm'))) event.preventDefault();
  });
});
document.querySelectorAll('nav a').forEach(function (link) {
  if (link.pathname === window.location.pathname) link.setAttribute('aria-current', 'page');
});
