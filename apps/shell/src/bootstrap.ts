void import('./index').catch(() => {
  const root = document.getElementById('root');
  if (!root) return;
  root.setAttribute('role', 'alert');
  root.setAttribute('data-bootstrap-error', 'true');
  root.textContent = 'بارگذاری سوپراپ ناموفق بود. صفحه را بازخوانی کنید.';
});
