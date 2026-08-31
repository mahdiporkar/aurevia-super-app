document.documentElement.classList.add('js');
const panels={overview:['OVERVIEW','صبح بخیر، مدیر سیستم'],people:['PEOPLE','مرکز منابع انسانی'],finance:['FINANCE','عملیات مالی یکپارچه'],access:['ACCESS','استودیوی دسترسی OpenFGA']};
document.querySelectorAll('[data-panel]').forEach(button=>button.addEventListener('click',()=>{document.querySelectorAll('[data-panel]').forEach(item=>item.classList.remove('active'));button.classList.add('active');const [crumb,title]=panels[button.dataset.panel];document.querySelector('#crumb').textContent=crumb;document.querySelector('#panel-title').textContent=title;}));
const observer=new IntersectionObserver(entries=>entries.forEach(entry=>entry.isIntersecting&&entry.target.classList.add('visible')),{threshold:.12});
document.querySelectorAll('.reveal').forEach(element=>observer.observe(element));
document.querySelector('#year').textContent=new Date().getFullYear();
