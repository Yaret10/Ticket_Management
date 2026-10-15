import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, User } from '../api';

interface Area { id: number; name: string; }

@Component({
  standalone: true, imports: [FormsModule],
  template: `<main><div class="page-heading"><p class="eyebrow">MESA DE AYUDA</p><h1>Registrar usuario</h1></div>
    @if (error) { <div class="message error" role="alert">{{ error }}</div> }
    @if (notice) { <div class="message" role="status">{{ notice }}</div> }
    <section class="card narrow"><p>El registro requiere permiso explícito de TI. Solo puede asignar los roles autorizados.</p>
      @if (me && me.roles.includes('TI') && me.manageUsers) {
        <form (ngSubmit)="create()">
          <label for="dni">DNI</label><input id="dni" name="dni" [(ngModel)]="form.dni" pattern="[0-9]{8}" inputmode="numeric" maxlength="8" required>
          <label for="username">Usuario</label><input id="username" name="username" [(ngModel)]="form.username" maxlength="100" required>
          <label for="name">Nombre completo</label><input id="name" name="name" [(ngModel)]="form.name" maxlength="120" required>
          <label for="email">Correo</label><input id="email" name="email" [(ngModel)]="form.email" type="email" maxlength="254" required>
          <label for="password">Contraseña inicial (12–72 caracteres)</label><input id="password" name="password" [(ngModel)]="form.password" type="password" minlength="12" maxlength="72" autocomplete="new-password" required>
          <label for="areaId">Área principal</label><select id="areaId" name="areaId" [(ngModel)]="form.areaId" required><option [ngValue]="null">Seleccione un área</option>@for (area of areas; track area.id) { <option [ngValue]="area.id">{{ area.name }}</option> }</select>
          <label for="position">Cargo</label><input id="position" name="position" [(ngModel)]="form.position" maxlength="120" required>
          <label for="employeeCode">Código de personal</label><input id="employeeCode" name="employeeCode" [(ngModel)]="form.employeeCode" maxlength="100">
          <fieldset><legend>Roles autorizados</legend>@for (role of me.grantableRoles; track role) { <label class="check-label"><input type="checkbox" [checked]="form.roles.includes(role)" (change)="toggleRole(role, $event)"> {{ role }}</label> }</fieldset>
          @if (form.roles.includes('GERENTE')) { <fieldset><legend>Áreas de gestión</legend>@for (area of areas; track area.id) { <label class="check-label"><input type="checkbox" [checked]="form.managedAreas.includes(area.id)" (change)="toggleArea(area.id, $event)"> {{ area.name }}</label> }</fieldset> }
          <button class="primary" type="submit" [disabled]="busy">{{ busy ? 'Registrando…' : 'Registrar usuario' }}</button>
        </form>
      } @else if (me) { <p>El registro de usuarios requiere autorización explícita.</p> }
    </section>
  </main>`
})
export class UsersPage {
  private api = inject(Api);
  private cdr = inject(ChangeDetectorRef);
  me: User | null = null; areas: Area[] = [];
  busy = false; error = ''; notice = '';
  form = this.empty();
  constructor() { Promise.all([this.api.json<User>('/api/v1/auth/me'), this.api.json<Area[]>('/api/v1/areas')])
    .then(([me, areas]) => { this.me = me; this.areas = areas; this.cdr.markForCheck(); })
    .catch(error => { this.error = error.message; this.cdr.markForCheck(); }); }
  private empty() { return { dni: '', username: '', name: '', email: '', password: '', areaId: null as number | null, position: '', employeeCode: '', roles: [] as string[], managedAreas: [] as number[] }; }
  toggleRole(role: string, event: Event) {
    const checked = (event.target as HTMLInputElement).checked;
    this.form.roles = checked ? [...this.form.roles, role] : this.form.roles.filter(value => value !== role);
    if (!this.form.roles.includes('GERENTE')) this.form.managedAreas = [];
  }
  toggleArea(area: number, event: Event) {
    const checked = (event.target as HTMLInputElement).checked;
    this.form.managedAreas = checked ? [...this.form.managedAreas, area] : this.form.managedAreas.filter(value => value !== area);
  }
  async create() {
    this.busy = true; this.error = ''; this.notice = '';
    try {
      if (!this.form.roles.length) throw new Error('Seleccione al menos un rol.');
      await this.api.json('/api/v1/users', { method: 'POST', body: JSON.stringify(this.form) });
      this.form = this.empty(); this.notice = 'Usuario registrado correctamente.';
    } catch (error) { this.error = (error as Error).message; }
    finally { this.busy = false; this.cdr.markForCheck(); }
  }
}
