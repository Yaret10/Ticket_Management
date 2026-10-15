import { bootstrapApplication } from '@angular/platform-browser';
import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { provideRouter, Router, RouterLink, RouterOutlet, CanActivateFn, ActivatedRoute, NavigationEnd } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Api, User, Ticket, Results, limaDate, safeNext } from './api';
import { DetailPage } from './pages/detail';
import { DashboardPage } from './pages/dashboard';
import { UsersPage } from './pages/users';

class PageBase {
  protected api = inject(Api);
  protected router = inject(Router);
  protected cdr = inject(ChangeDetectorRef);
  error = '';
  notice = '';
  busy = false;
  async perform(action: () => Promise<void>) {
    this.busy = true; this.error = ''; this.notice = '';
    try { await action(); } catch (error) { this.error = (error as Error).message; }
    finally { this.busy = false; this.cdr.markForCheck(); }
  }
}

@Component({
  standalone: true, selector: 'app-root', imports: [RouterOutlet, RouterLink],
  template: `
    @if (!publicPage) {
      <header>
        <a class="brand" routerLink="/menu"><img src="/images/logo.png" alt="STROBBE"><span>con<span class="accent">TI</span>go</span></a>
        <nav aria-label="Principal">
          <a routerLink="/menu">Inicio</a>
          @if (me?.roles?.includes('EMPLEADO')) { <a routerLink="/tickets/new">Nuevo ticket</a> }
          <a routerLink="/tickets">Tickets</a><a routerLink="/dashboard">Dashboard</a><a routerLink="/reports">Reportes</a>
          @if (me?.roles?.includes('TI') && me.manageUsers) { <a routerLink="/users/new">Usuarios</a> }
        </nav>
        <div class="session"><span>{{ me?.name }}</span>
          <button type="button" (click)="theme()" aria-label="Cambiar tema">◐</button>
          <button type="button" (click)="logout()">Salir</button>
        </div>
      </header>
    }
    <router-outlet />
    @if (!publicPage) { <footer>conTIgo · STROBBE — Soporte de tecnología</footer> }
  `
})
class AppRoot {
  private api = inject(Api);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);
  me: User | null = null;
  get publicPage() { return this.router.url.startsWith('/login') || this.router.url.startsWith('/auth/recover'); }
  constructor() {
    this.router.events.subscribe(event => {
      if (!(event instanceof NavigationEnd)) return;
      if (!this.publicPage && !this.me) this.api.json<User>('/api/v1/auth/me').then(user => { this.me = user; this.cdr.markForCheck(); }).catch(() => {});
      this.cdr.markForCheck();
    });
  }
  theme() { document.documentElement.dataset['theme'] = document.documentElement.dataset['theme'] === 'dark' ? 'light' : 'dark'; }
  async logout() {
    try { await this.api.json('/api/v1/auth/logout', { method: 'POST' }); }
    finally { this.me = null; this.cdr.markForCheck(); await this.router.navigateByUrl('/login'); }
  }
}

@Component({
  standalone: true, imports: [FormsModule],
  template: `
    <div class="auth-page"><main class="auth-card">
      <img class="auth-logo" src="/images/logo.png" alt="STROBBE">
      <h1>con<span class="accent">TI</span>go</h1><p>Tu soporte de tecnología, en un solo lugar.</p>
      <h2>Iniciar sesión</h2>
      @if (error) { <div class="message error" role="alert">{{ error }}</div> }
      <form (ngSubmit)="login()">
        <label for="dni">DNI</label><input id="dni" name="dni" [(ngModel)]="dni" inputmode="numeric" pattern="[0-9]{8}" maxlength="8" autocomplete="username" required>
        <label for="password">Contraseña</label><input id="password" name="password" [(ngModel)]="password" type="password" maxlength="72" autocomplete="current-password" required>
        <button class="primary" type="submit" [disabled]="busy">{{ busy ? 'Ingresando…' : 'Ingresar' }}</button>
      </form>
      <p class="muted">Para restablecer su contraseña, contacte al personal autorizado de TI.</p>
    </main></div>
  `
})
class LoginPage extends PageBase {
  dni = ''; password = '';
  private route = inject(ActivatedRoute);
  login() { this.perform(async () => {
    await this.api.json('/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ dni: this.dni, password: this.password }) });
    await this.router.navigateByUrl(safeNext(this.route.snapshot.queryParamMap.get('next')));
  }); }
}

@Component({
  standalone: true,
  template: `<div class="auth-page"><main class="auth-card"><h1>Un momento…</h1>
    <p>Estamos renovando su autenticación para abrir la página solicitada.</p>
    @if (error) { <p class="form-error">{{ error }}</p> }
    <a href="/login">Volver al inicio de sesión</a>
  </main></div>`
})
class RecoverPage extends PageBase {
  private route = inject(ActivatedRoute);
  constructor() { super(); this.api.refresh()
    .then(() => this.router.navigateByUrl(safeNext(this.route.snapshot.queryParamMap.get('next'))))
    .catch(() => this.router.navigate(['/login'], { queryParams: { next: safeNext(this.route.snapshot.queryParamMap.get('next')) } })); }
}

@Component({
  standalone: true, imports: [RouterLink],
  template: `<main>
    <div class="page-heading"><p class="eyebrow">MESA DE AYUDA</p><h1>Inicio</h1></div>
    <section class="welcome"><h2>Hola, {{ me?.name || 'bienvenido' }}</h2><p>Registra una solicitud y acompaña su avance hasta el cierre.</p></section>
    <section class="cards" aria-label="Opciones">
      @if (me?.roles?.includes('EMPLEADO')) { <a class="card action-card" routerLink="/tickets/new"><span class="card-icon">＋</span><h2>Genera tu ticket</h2><p>Describe el problema y adjunta evidencias.</p></a> }
      <a class="card action-card" routerLink="/tickets"><span class="card-icon">☷</span><h2>Consulta tus tickets</h2><p>Revisa el estado, informe técnico y próximos pasos.</p></a>
      <a class="card action-card" routerLink="/dashboard"><span class="card-icon">▥</span><h2>Dashboard</h2><p>Conoce la distribución y tiempos de atención.</p></a>
      <a class="card action-card" routerLink="/reports"><span class="card-icon">↧</span><h2>Reportes</h2><p>Filtra solicitudes y exporta a Excel.</p></a>
    </section>
  </main>`
})
class MenuPage extends PageBase {
  me: User | null = null;
  constructor() { super(); this.api.json<User>('/api/v1/auth/me')
    .then(user => { this.me = user; this.cdr.markForCheck(); })
    .catch(error => { this.error = error.message; this.cdr.markForCheck(); }); }
}

@Component({
  standalone: true, imports: [FormsModule],
  template: `<main><div class="page-heading"><p class="eyebrow">MESA DE AYUDA</p><h1>Registrar ticket</h1></div>
    @if (error) { <div class="message error" role="alert">{{ error }}</div> }
    <section class="card narrow">
      <p class="muted">Solicitante: {{ me?.name }} · Área: {{ me?.area }}</p>
      @if (me && me.roles.includes('EMPLEADO')) {
        <form (ngSubmit)="create()">
          <label for="equipment">Nombre del equipo</label><input id="equipment" name="equipment" [(ngModel)]="form.equipment" maxlength="120" required>
          <label for="pcUser">Usuario de PC</label><input id="pcUser" name="pcUser" [(ngModel)]="form.pcUser" maxlength="100" required>
          <label for="priority">Prioridad</label><select id="priority" name="priority" [(ngModel)]="form.priority"><option value="MEDIA">Media</option><option value="ALTA">Alta</option><option value="BAJA">Baja</option></select>
          <label for="description">Descripción del problema</label><textarea id="description" name="description" [(ngModel)]="form.description" rows="6" maxlength="4000" required></textarea>
          <p class="muted">Después del registro puede adjuntar evidencias mientras el ticket esté pendiente.</p>
          <button class="primary" type="submit" [disabled]="busy">{{ busy ? 'Registrando…' : 'Registrar ticket' }}</button>
        </form>
      } @else if (me) { <p>No tiene permiso para registrar tickets.</p> }
    </section>
  </main>`
})
class NewTicketPage extends PageBase {
  me: User | null = null;
  form = { equipment: '', pcUser: '', priority: 'MEDIA', description: '' };
  constructor() { super(); this.api.json<User>('/api/v1/auth/me')
    .then(user => { this.me = user; this.cdr.markForCheck(); })
    .catch(error => { this.error = error.message; this.cdr.markForCheck(); }); }
  create() { this.perform(async () => {
    const ticket = await this.api.json<Ticket>('/api/v1/tickets', { method: 'POST', body: JSON.stringify(this.form) });
    await this.router.navigate(['/tickets', ticket.id]);
  }); }
}

@Component({
  standalone: true, imports: [FormsModule, RouterLink],
  template: `<main><div class="page-heading"><p class="eyebrow">MESA DE AYUDA</p><h1>{{ reports ? 'Reportes' : 'Consultar tickets' }}</h1></div>
    @if (error) { <div class="message error" role="alert">{{ error }}</div> }
    <section class="card">
      <form class="filters" (ngSubmit)="search()">
        <div><label for="q">Buscar</label><input id="q" name="q" [(ngModel)]="filters.q" maxlength="200" placeholder="Código, equipo o descripción"></div>
        <div><label for="state">Estado</label><select id="state" name="state" [(ngModel)]="filters.state"><option value="">Todos</option>@for (value of states; track value) { <option [value]="value">{{ value }}</option> }</select></div>
        <div><label for="priority">Prioridad</label><select id="priority" name="priority" [(ngModel)]="filters.priority"><option value="">Todas</option><option>ALTA</option><option>MEDIA</option><option>BAJA</option></select></div>
        <div><label for="sort">Ordenar por</label><select id="sort" name="sort" [(ngModel)]="filters.sort"><option value="createdAt">Fecha</option><option value="code">Código</option><option value="state">Estado</option><option value="priority">Prioridad</option></select></div>
        <div><label for="ascending">Dirección</label><select id="ascending" name="ascending" [(ngModel)]="filters.ascending"><option value="false">Descendente</option><option value="true">Ascendente</option></select></div>
        <button class="primary" type="submit" [disabled]="busy">Buscar</button><button type="button" (click)="export()" [disabled]="busy">Exportar Excel</button>
      </form>
      <p role="status">{{ result ? (result.totalElements ? result.totalElements + ' tickets encontrados.' : 'No se encontraron tickets con estos filtros.') : 'Cargando tickets…' }}</p>
      <div class="table-wrap"><table><caption class="sr-only">Tickets visibles para su usuario</caption><thead><tr><th>Código</th><th>Equipo</th><th>Solicitante / área</th><th>Prioridad</th><th>Estado</th><th>Registro</th></tr></thead>
        <tbody>@for (ticket of result?.content || []; track ticket.id) { <tr><td><a [routerLink]="['/tickets',ticket.id]">{{ ticket.code }}</a></td><td>{{ ticket.equipment }}</td><td>{{ ticket.requester }} / {{ ticket.area }}</td><td><span class="badge {{ ticket.priority }}">{{ ticket.priority }}</span></td><td><span class="badge {{ ticket.state }}">{{ ticket.state }}</span></td><td>{{ date(ticket.createdAt) }}</td></tr> }</tbody>
      </table></div>
      <div class="pagination"><button type="button" (click)="previous()" [disabled]="busy || page===0">Anterior</button><span>Página {{ result?.totalPages ? page+1 : 0 }} de {{ result?.totalPages || 0 }}</span><button type="button" (click)="next()" [disabled]="busy || page+1 >= (result?.totalPages || 0)">Siguiente</button></div>
    </section>
  </main>`
})
class ListPage extends PageBase {
  private route = inject(ActivatedRoute);
  reports = this.route.snapshot.routeConfig?.path === 'reports';
  states = ['PENDIENTE', 'APROBADO', 'ATENDIDO', 'RECHAZADO', 'CERRADO'];
  filters = { q: '', state: '', priority: '', sort: 'createdAt', ascending: 'false' };
  page = 0;
  result: Results | null = null;
  date = limaDate;
  constructor() { super(); this.load(); }
  private params(exportOnly = false) {
    const params = new URLSearchParams();
    for (const [key, value] of Object.entries(this.filters)) if (value && (!exportOnly || ['q','state','priority'].includes(key))) params.set(key, value);
    if (!exportOnly) { params.set('page', String(this.page)); params.set('size', '20'); }
    return params.toString();
  }
  load() { this.perform(async () => { this.result = await this.api.json<Results>('/api/v1/tickets?' + this.params()); }); }
  search() { this.page = 0; this.load(); }
  previous() { if (this.page > 0) { this.page--; this.load(); } }
  next() { if (this.page+1 < (this.result?.totalPages || 0)) { this.page++; this.load(); } }
  export() { this.perform(() => this.api.download('/api/v1/reports/excel?' + this.params(true), 'tickets.xlsx')); }
}

const guard: CanActivateFn = async (_, state) => {
  const api = inject(Api), router = inject(Router);
  try { await api.json<User>('/api/v1/auth/me'); return true; }
  catch { return router.createUrlTree(['/login'], { queryParams: { next: state.url } }); }
};

bootstrapApplication(AppRoot, { providers: [provideRouter([
  { path: 'login', component: LoginPage },
  { path: 'auth/recover', component: RecoverPage },
  { path: '', redirectTo: 'menu', pathMatch: 'full' },
  { path: 'menu', component: MenuPage, canActivate: [guard] },
  { path: 'tickets/new', component: NewTicketPage, canActivate: [guard] },
  { path: 'tickets/:id', component: DetailPage, canActivate: [guard] },
  { path: 'tickets', component: ListPage, canActivate: [guard] },
  { path: 'dashboard', component: DashboardPage, canActivate: [guard] },
  { path: 'reports', component: ListPage, canActivate: [guard] },
  { path: 'users/new', component: UsersPage, canActivate: [guard] },
  { path: '**', redirectTo: 'menu' }
])]}).catch(error => console.error(error));
