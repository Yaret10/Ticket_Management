import {json, refreshAuth, safeNext} from './api.js';
import {message, busy} from './ui.js';
const page=document.body.dataset.page;
async function start() {
  if(page==='recover') {try {await refreshAuth();location.replace(safeNext(new URLSearchParams(location.search).get('next')));} catch(error) {location.replace('/login?next='+encodeURIComponent(safeNext(new URLSearchParams(location.search).get('next'))));}return;}
  if(page==='login') {
    document.querySelector('#login-form').addEventListener('submit',event=>{event.preventDefault();busy(event.submitter,async()=>{const fields=Object.fromEntries(new FormData(event.target));await json('/api/v1/auth/login',{method:'POST',body:JSON.stringify(fields)});location.assign(safeNext(new URLSearchParams(location.search).get('next')));},'Ingresando…');});return;
  }
  const me=await json('/api/v1/auth/me');
  document.querySelector('#me-name').textContent=me.name;
  document.querySelectorAll('[data-admin]').forEach(node=>node.hidden=!(me.roles.includes('TI')&&me.manageUsers));
  document.querySelectorAll('[data-requester]').forEach(node=>node.hidden=!me.roles.includes('EMPLEADO'));
  document.querySelectorAll('nav a').forEach(node=>{if(node.pathname===location.pathname) node.setAttribute('aria-current','page');});
  document.querySelector('#logout').addEventListener('click',event=>busy(event.target,async()=>{await json('/api/v1/auth/logout',{method:'POST'});location.assign('/login');}));
  document.querySelector('#theme').addEventListener('click',()=>document.documentElement.dataset.theme=document.documentElement.dataset.theme==='dark'?'light':'dark');
  if(page==='menu') document.querySelector('#welcome-name').textContent=me.name;
  if(page==='new') {if(!me.roles.includes('EMPLEADO')) {message('No tiene permiso para registrar tickets.',true);document.querySelector('#ticket-form').hidden=true;return;}document.querySelector('#requester-data').textContent=`Solicitante: ${me.name} · Área: ${me.area}`;document.querySelector('#ticket-form').addEventListener('submit',event=>{event.preventDefault();busy(event.submitter,async()=>{const ticket=await json('/api/v1/tickets',{method:'POST',body:JSON.stringify(Object.fromEntries(new FormData(event.target)))});location.assign('/tickets/'+ticket.id);},'Registrando…');});}
  if(['list','reports'].includes(page)) await (await import('./list.js')).init();
  if(page==='detail') await (await import('./detail.js')).init(me);
  if(page==='dashboard') await (await import('./dashboard.js')).init();
  if(page==='users') await (await import('./users.js')).init(me);
}
start().catch(error=>message(error.message,true));
