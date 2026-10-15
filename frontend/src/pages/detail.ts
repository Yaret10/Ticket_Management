import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Api, Attachment, History, Ticket, User, limaDate } from '../api';

@Component({
  standalone: true, imports: [FormsModule],
  template: `<main><div class="page-heading"><p class="eyebrow">MESA DE AYUDA</p><h1>Detalle del ticket</h1></div>
    @if (error) { <div class="message error" role="alert">{{ error }}</div> }
    @if (notice) { <div class="message" role="status">{{ notice }}</div> }
    @if (ticket; as t) {
      <section class="card"><h2>{{ t.code }}</h2><dl class="detail-grid">
        <div><dt>Estado</dt><dd>{{ t.state }}</dd></div><div><dt>Prioridad</dt><dd>{{ t.priority }}</dd></div>
        <div><dt>Equipo</dt><dd>{{ t.equipment }}</dd></div><div><dt>Usuario PC</dt><dd>{{ t.pcUser }}</dd></div>
        <div><dt>Solicitante</dt><dd>{{ t.requester }}</dd></div><div><dt>Área</dt><dd>{{ t.area }}</dd></div>
        <div><dt>Aprobado por</dt><dd>{{ t.approver || '—' }}</dd></div><div><dt>Atendido por</dt><dd>{{ t.technician || '—' }}</dd></div>
        <div><dt>Registro</dt><dd>{{ date(t.createdAt) }}</dd></div><div><dt>Atención</dt><dd>{{ date(t.attendedAt) }}</dd></div><div><dt>Cierre</dt><dd>{{ date(t.closedAt) }}</dd></div>
      </dl><h3>Descripción</h3><p class="pre-wrap">{{ t.description }}</p>
        @if (t.technicalReport) { <h3>{{ t.state==='RECHAZADO' ? 'Motivo de rechazo' : 'Informe técnico' }}</h3><p class="pre-wrap">{{ t.technicalReport }}</p> }
      </section>
      <section class="card"><h2>Gestionar ticket</h2>
        @if (actions.length) { <form (ngSubmit)="$event.preventDefault()"><label for="observation">Observación / informe técnico / motivo de rechazo</label><textarea id="observation" name="observation" [(ngModel)]="observation" rows="4" maxlength="4000"></textarea>
          <div class="button-row">@for (action of actions; track action[0]) { <button type="button" class="primary" [disabled]="busy" (click)="transition(action[0])">{{ action[1] }}</button> }</div>
        </form> } @else { <p class="muted">{{ ['CERRADO','RECHAZADO'].includes(t.state) ? 'Este ticket ha finalizado.' : 'No hay acciones disponibles para su usuario en esta etapa.' }}</p> }
      </section>
      <section class="card"><h2>Evidencias</h2><ul>
        @for (attachment of attachments; track attachment.id) { <li>{{ attachment.name }} ({{ kilobytes(attachment.size) }} KB)
          <span class="inline-actions">@if (previewable(attachment)) { <button type="button" [disabled]="busy" (click)="preview(attachment)">Ver</button> }
            <button type="button" [disabled]="busy" (click)="download(attachment)">Descargar</button></span></li>
        } @empty { <li>Todavía no hay evidencias.</li> }
      </ul>
        @if (t.state==='PENDIENTE' && t.requesterId===me?.id) {
          <form (ngSubmit)="upload()"><label for="file">Adjuntar evidencia (máximo 10 MB)</label><input id="file" name="file" type="file" accept=".pdf,.png,.jpg,.jpeg,.txt" (change)="selectFile($event)" required>
            <button class="primary" type="submit" [disabled]="busy">Adjuntar</button>
          </form>
        }
      </section>
      <section class="card"><h2>Historial de cambios</h2><ol>@for (entry of history; track entry.id) {
        <li><strong>{{ entry.previousState || 'CREACIÓN' }} → {{ entry.newState }}</strong><p>{{ entry.actor }} · {{ date(entry.date) }}</p>@if (entry.observation) { <p class="pre-wrap">{{ entry.observation }}</p> }</li>
      }</ol></section>
    } @else { <p class="loading">Cargando ticket…</p> }
  </main>`
})
export class DetailPage {
  private api = inject(Api);
  private route = inject(ActivatedRoute);
  private cdr = inject(ChangeDetectorRef);
  readonly id = Number(this.route.snapshot.paramMap.get('id'));
  ticket: Ticket | null = null;
  me: User | null = null;
  attachments: Attachment[] = [];
  history: History[] = [];
  observation = '';
  file: File | null = null;
  busy = false; error = ''; notice = '';
  date = limaDate;
  constructor() { this.load(); }
  get actions(): [string, string][] {
    const t = this.ticket, me = this.me;
    if (!t || !me) return [];
    if (t.state === 'PENDIENTE' && ((me.roles.includes('JEFE') && me.areaId === t.areaId) || (me.roles.includes('GERENTE') && me.managedAreas.includes(t.areaId)))) return [['approve', 'Aprobar']];
    if (t.state === 'APROBADO' && me.roles.includes('TI')) return [['attend', 'Registrar atención'], ['reject', 'Rechazar']];
    if (t.state === 'ATENDIDO' && t.requesterId === me.id) return [['close', 'Cerrar conforme']];
    return [];
  }
  private async perform(action: () => Promise<void>) {
    this.busy = true; this.error = ''; this.notice = '';
    try { await action(); } catch (error) { this.error = (error as Error).message; }
    finally { this.busy = false; this.cdr.markForCheck(); }
  }
  async load() {
    await this.perform(async () => {
      const [me, ticket, history, attachments] = await Promise.all([
        this.api.json<User>('/api/v1/auth/me'),
        this.api.json<Ticket>(`/api/v1/tickets/${this.id}`),
        this.api.json<History[]>(`/api/v1/tickets/${this.id}/history`),
        this.api.json<Attachment[]>(`/api/v1/tickets/${this.id}/attachments`)
      ]);
      this.me = me; this.ticket = ticket; this.history = history; this.attachments = attachments;
    });
  }
  transition(action: string) { this.perform(async () => {
    if (['attend','reject'].includes(action) && !this.observation.trim()) throw new Error('Indique el informe técnico o motivo de rechazo.');
    await this.api.json(`/api/v1/tickets/${this.id}/${action}`, {
      method: 'POST', body: JSON.stringify({ version: this.ticket!.version, observation: this.observation.trim() })
    });
    this.observation = ''; await this.load(); this.notice = 'Ticket actualizado correctamente.';
  }); }
  selectFile(event: Event) { this.file = (event.target as HTMLInputElement).files?.[0] || null; }
  upload() { this.perform(async () => {
    if (!this.file || this.file.size > 10 * 1024 * 1024) throw new Error('Seleccione una evidencia de hasta 10 MB.');
    const data = new FormData(); data.set('file', this.file);
    await this.api.json(`/api/v1/tickets/${this.id}/attachments`, { method: 'POST', body: data });
    this.file = null; await this.load(); this.notice = 'Evidencia adjuntada.';
  }); }
  kilobytes(size: number) { return Math.ceil(size / 1024); }
  previewable(a: Attachment) { return ['image/png','image/jpeg','application/pdf','text/plain'].includes(a.contentType); }
  preview(a: Attachment) { this.perform(() => this.api.preview(`/api/v1/tickets/${this.id}/attachments/${a.id}/view`)); }
  download(a: Attachment) { this.perform(() => this.api.download(`/api/v1/tickets/${this.id}/attachments/${a.id}`, a.name)); }
}
