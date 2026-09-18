let csrf = null;
let refreshInFlight = null;
export async function csrfToken() {
  if (!csrf) {
    const response = await fetch('/api/v1/auth/csrf', {credentials: 'same-origin', cache: 'no-store'});
    if (!response.ok) throw new Error('No se pudo preparar la autenticación.');
    csrf = await response.json();
  }
  return csrf;
}
export async function refreshAuth() {
  if (!refreshInFlight) refreshInFlight = (async () => {
    const renew = async () => {
    const current = await fetch('/api/v1/auth/me', {credentials:'same-origin', cache:'no-store'});
    if (current.ok) return;
    const token = await csrfToken();
    const response = await fetch('/api/v1/auth/refresh', {method:'POST', credentials:'same-origin', headers:{[token.headerName]:token.token}});
    if (!response.ok) throw new Error('Su sesión ha vencido. Inicie sesión nuevamente.');
    };
    if (navigator.locks) await navigator.locks.request('contigo-auth-refresh', renew);
    else await renew();
  })().finally(() => {refreshInFlight = null;});
  return refreshInFlight;
}
export function safeNext(value) {
  try { const url = new URL(value || '/menu', location.origin); return url.origin === location.origin && !url.pathname.startsWith('/auth/') && !url.pathname.startsWith('/api/') && url.pathname !== '/login' ? url.pathname+url.search : '/menu'; }
  catch {return '/menu';}
}
export async function api(path, options = {}, retry = true) {
  const headers = new Headers(options.headers || {});
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type','application/json');
  if (!['GET','HEAD'].includes((options.method || 'GET').toUpperCase())) {const token = await csrfToken(); headers.set(token.headerName,token.token);}
  const response = await fetch(path, {...options, headers, credentials:'same-origin', cache:'no-store'});
  const publicAuth = ['/api/v1/auth/login','/api/v1/auth/refresh','/api/v1/auth/logout','/api/v1/auth/csrf'].includes(path.split('?')[0]);
  if (response.status === 401 && retry && !publicAuth) {
    try {await refreshAuth();return api(path,options,false);}
    catch (error) {location.assign('/login?next='+encodeURIComponent(location.pathname+location.search));throw error;}
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.errors?.join(' · ') || error.detail || 'No se pudo completar la operación.');
  }
  return response;
}
export async function json(path, options) {return (await api(path, options)).json();}
export async function download(path, name) {
  const blob = await (await api(path)).blob();const url = URL.createObjectURL(blob);const link = document.createElement('a');link.href=url;link.download=name;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
export async function preview(path) {
  // Open during the click so popup blockers do not reject a window after asynchronous renewal.
  const viewer = window.open('about:blank', '_blank');
  if (!viewer) throw new Error('Permita abrir pestañas para visualizar la evidencia.');
  viewer.opener = null;
  viewer.document.title = 'Evidencia';
  viewer.document.body.textContent = 'Cargando evidencia…';
  try {
    // HEAD checks access and renews an expired JWT before navigating with HttpOnly cookies.
    await api(path, {method: 'HEAD'});
    viewer.location.replace(path);
  } catch (error) {
    viewer.close();
    throw error;
  }
}
