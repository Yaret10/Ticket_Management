import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { Api, Dashboard } from '../api';

@Component({
  standalone: true,
  template: `<main><div class="page-heading"><p class="eyebrow">MESA DE AYUDA</p><h1>Dashboard</h1></div>
    @if (error) { <div class="message error" role="alert">{{ error }}</div> }
    @if (data; as d) {
      <section class="cards" aria-label="Indicadores">
        <article class="card"><h2>Total</h2><div class="stat">{{ d.total }}</div></article>
        @for (state of states; track state) { <article class="card"><h2>{{ state }}</h2><div class="stat">{{ d.byState[state] || 0 }}</div></article> }
        <article class="card"><h2>Atención promedio (días)</h2><div class="stat">{{ d.averageLeadDays }}</div></article>
      </section>
      <section class="card"><h2>Tickets por prioridad</h2><div class="cards">@for (priority of priorities; track priority) { <div><h3>{{ priority }}</h3><p class="stat">{{ d.byPriority[priority] || 0 }}</p></div> }</div></section>
      <section class="card"><h2>Tickets por mes (America/Lima)</h2><div class="table-wrap"><table><thead><tr><th>Mes</th><th>Tickets</th></tr></thead><tbody>
        @for (month of d.byMonth; track month.month) { <tr><td>{{ month.month }}</td><td>{{ month.count }}</td></tr> }
        @empty { <tr><td colspan="2">No hay tickets para mostrar.</td></tr> }
      </tbody></table></div></section>
    } @else { <p class="loading">Cargando indicadores…</p> }
  </main>`
})
export class DashboardPage {
  private api = inject(Api);
  private cdr = inject(ChangeDetectorRef);
  data: Dashboard | null = null; error = '';
  states = ['PENDIENTE','APROBADO','ATENDIDO','RECHAZADO','CERRADO'];
  priorities = ['ALTA','MEDIA','BAJA'];
  constructor() { this.api.json<Dashboard>('/api/v1/reports/dashboard')
    .then(data => { this.data = data; this.cdr.markForCheck(); })
    .catch(error => { this.error = error.message; this.cdr.markForCheck(); }); }
}
