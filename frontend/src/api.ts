import { Injectable } from '@angular/core';

export interface User {
  id: number; name: string; area: string; areaId: number;
  roles: string[]; managedAreas: number[]; manageUsers: boolean; grantableRoles: string[];
}
export interface Ticket {
  id: number; version: number; code: string; equipment: string; pcUser: string;
  description: string; priority: string; state: string; requesterId: number;
  requester: string; area: string; areaId: number; approver: string | null;
  technician: string | null; technicalReport: string | null;
  createdAt: string; attendedAt: string | null; closedAt: string | null;
}
export interface Results {
  content: Ticket[]; totalElements: number; totalPages: number; number: number; size: number;
}
export interface Attachment { id: number; name: string; size: number; contentType: string; }
export interface History { id: number; actor: string; date: string; previousState: string | null; newState: string; observation: string | null; }
export interface Dashboard {
  total: number; byState: Record<string, number>; byPriority: Record<string, number>;
  byMonth: { month: string; count: number }[]; averageLeadDays: number;
}

@Injectable({ providedIn: 'root' })
export class Api {
  private csrf?: { token: string; headerName: string };
  private renewing?: Promise<void>;

  private async token() {
    if (!this.csrf) {
      const response = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin', cache: 'no-store' });
      if (!response.ok) throw new Error('No se pudo preparar la autenticación.');
      this.csrf = await response.json();
    }
    return this.csrf!;
  }

  async refresh() {
    if (!this.renewing) this.renewing = (async () => {
      const renew = async () => {
        const current = await fetch('/api/v1/auth/me', { credentials: 'same-origin', cache: 'no-store' });
        if (current.ok) return;
        const csrf = await this.token();
        const response = await fetch('/api/v1/auth/refresh', {
          method: 'POST', credentials: 'same-origin', cache: 'no-store',
          headers: { [csrf.headerName]: csrf.token }
        });
        if (!response.ok) throw new Error('Su sesión ha vencido. Inicie sesión nuevamente.');
      };
      if (navigator.locks) await navigator.locks.request('contigo-auth-refresh', renew);
      else await renew();
    })().finally(() => this.renewing = undefined);
    return this.renewing;
  }

  async request(path: string, options: RequestInit = {}, retry = true): Promise<Response> {
    const headers = new Headers(options.headers);
    const method = (options.method || 'GET').toUpperCase();
    const requiresCsrf = !['GET', 'HEAD'].includes(method);
    const endpoint = path.split('?')[0];
    if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json');
    if (requiresCsrf) {
      const csrf = await this.token();
      headers.set(csrf.headerName, csrf.token);
    }
    const response = await fetch(path, { ...options, headers, credentials: 'same-origin', cache: 'no-store' });
    const publicAuth = ['/api/v1/auth/login', '/api/v1/auth/refresh', '/api/v1/auth/logout', '/api/v1/auth/csrf'].includes(endpoint);
    if (response.status === 401 && retry && !publicAuth) {
      try { await this.refresh(); return this.request(path, options, false); }
      catch (error) {
        location.assign('/login?next=' + encodeURIComponent(safeNext(location.pathname + location.search)));
        throw error;
      }
    }
    // Al cerrar sesión, el servidor puede renovar la cookie CSRF. Si Angular
    // conserva el valor anterior, se obtiene uno nuevo y se reintenta una vez.
    if (response.status === 403 && requiresCsrf && retry) {
      this.csrf = undefined;
      return this.request(path, options, false);
    }
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.errors?.join(' · ') || body.detail || 'No se pudo completar la operación.');
    }
    if (endpoint === '/api/v1/auth/logout') this.csrf = undefined;
    return response;
  }

  async json<T>(path: string, options: RequestInit = {}): Promise<T> {
    return (await this.request(path, options)).json() as Promise<T>;
  }

  async download(path: string, name: string) {
    const blob = await (await this.request(path)).blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = name;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  async preview(path: string) {
    const viewer = window.open('about:blank', '_blank');
    if (!viewer) throw new Error('Permita abrir pestañas para visualizar la evidencia.');
    viewer.opener = null;
    viewer.document.title = 'Evidencia';
    viewer.document.body.textContent = 'Cargando evidencia…';
    try { await this.request(path, { method: 'HEAD' }); viewer.location.replace(path); }
    catch (error) { viewer.close(); throw error; }
  }
}

export function safeNext(value: string | null): string {
  try {
    const url = new URL(value || '/menu', location.origin);
    return url.origin === location.origin && !url.pathname.startsWith('/auth/') &&
      !url.pathname.startsWith('/api/') && url.pathname !== '/login'
      ? url.pathname + url.search : '/menu';
  } catch { return '/menu'; }
}

export function limaDate(value: string | null): string {
  return value ? new Intl.DateTimeFormat('es-PE', {
    dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Lima'
  }).format(new Date(value)) : '—';
}
