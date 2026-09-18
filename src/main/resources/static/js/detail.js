import {json,download,preview} from './api.js';import {element,date,message,busy} from './ui.js';
export async function init(me) {
  const id=document.body.dataset.ticketId;let ticket;const form=document.querySelector('#transition-form');
  async function load() {
    const [t,history,attachments]=await Promise.all([json('/api/v1/tickets/'+id),json(`/api/v1/tickets/${id}/history`),json(`/api/v1/tickets/${id}/attachments`)]);ticket=t;
    const detail=document.querySelector('#ticket-detail');detail.replaceChildren(element('h2',t.code));const grid=element('dl',null,'detail-grid');
    for(const [label,value] of [['Estado',t.state],['Prioridad',t.priority],['Equipo',t.equipment],['Usuario PC',t.pcUser],['Solicitante',t.requester],['Área',t.area],['Aprobado por',t.approver],['Atendido por',t.technician],['Registro',date(t.createdAt)],['Atención',date(t.attendedAt)],['Cierre',date(t.closedAt)]]) {const group=element('div');group.append(element('dt',label),element('dd',value||'—'));grid.append(group);}detail.append(grid,element('h3','Descripción'),element('p',t.description,'pre-wrap'));if(t.technicalReport) detail.append(element('h3',t.state==='RECHAZADO'?'Motivo de rechazo':'Informe técnico'),element('p',t.technicalReport,'pre-wrap'));
    const buttons=document.querySelector('#transition-buttons');buttons.replaceChildren();
    const actions=[];if(t.state==='PENDIENTE'&&((me.roles.includes('JEFE')&&me.areaId===t.areaId)||(me.roles.includes('GERENTE')&&me.managedAreas.includes(t.areaId)))) actions.push(['approve','Aprobar']);if(t.state==='APROBADO'&&me.roles.includes('TI')) actions.push(['attend','Registrar atención'],['reject','Rechazar']);if(t.state==='ATENDIDO'&&t.requesterId===me.id) actions.push(['close','Cerrar conforme']);
    for(const [action,label] of actions) {const button=element('button',label,'primary');button.type='submit';button.value=action;button.name='action';buttons.append(button);}
    document.querySelector('#next-step').textContent=actions.length?'Seleccione la acción que corresponda.':['CERRADO','RECHAZADO'].includes(t.state)?'Este ticket ha finalizado.':'No hay acciones disponibles para su usuario en esta etapa.';
    const list=document.querySelector('#attachment-list');list.replaceChildren();for(const a of attachments) {
      const item=element('li');item.append(element('span',`${a.name} (${Math.ceil(a.size/1024)} KB) `));
      if(['image/png','image/jpeg','application/pdf','text/plain'].includes(a.contentType)) {
        const view=element('button','Ver');view.type='button';view.setAttribute('aria-label',`Ver ${a.name} en otra pestaña`);
        view.addEventListener('click',()=>busy(view,()=>preview(`/api/v1/tickets/${id}/attachments/${a.id}/view`),'Abriendo…'));item.append(view);
      }
      const button=element('button','Descargar');button.type='button';button.setAttribute('aria-label',`Descargar ${a.name}`);
      button.addEventListener('click',()=>busy(button,()=>download(`/api/v1/tickets/${id}/attachments/${a.id}`,a.name),'Descargando…'));item.append(button);list.append(item);
    }if(!attachments.length) list.append(element('li','Todavía no hay evidencias.'));
    document.querySelector('#attachment-form').hidden=!(t.state==='PENDIENTE'&&t.requesterId===me.id);
    const log=document.querySelector('#history-list');log.replaceChildren();for(const h of history) {const entry=element('li');entry.append(element('strong',`${h.previousState||'CREACIÓN'} → ${h.newState}`),element('p',`${h.actor} · ${date(h.date)}`));if(h.observation)entry.append(element('p',h.observation,'pre-wrap'));log.append(entry);}
  }
  form.addEventListener('submit',event=>{event.preventDefault();const action=event.submitter.value;const observation=form.observation.value.trim();if(['attend','reject'].includes(action)&&!observation){message('Indique el informe técnico o motivo de rechazo.',true);return;}busy(event.submitter,async()=>{await json(`/api/v1/tickets/${id}/${action}`,{method:'POST',body:JSON.stringify({version:ticket.version,observation})});form.reset();await load();message('Ticket actualizado correctamente.');});});
  document.querySelector('#attachment-form').addEventListener('submit',event=>{event.preventDefault();const file=event.target.file.files[0];if(!file||file.size>10*1024*1024){message('Seleccione una evidencia de hasta 10 MB.',true);return;}busy(event.submitter,async()=>{await json(`/api/v1/tickets/${id}/attachments`,{method:'POST',body:new FormData(event.target)});event.target.reset();await load();message('Evidencia adjuntada.');});});await load();
}
