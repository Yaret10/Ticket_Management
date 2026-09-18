import {json} from './api.js';import {element} from './ui.js';
export async function init() {
  const d=await json('/api/v1/reports/dashboard');const stats=document.querySelector('#stats');stats.replaceChildren();
  for(const [label,value] of [['Total',d.total],...['PENDIENTE','APROBADO','ATENDIDO','RECHAZADO','CERRADO'].map(state=>[state,d.byState[state]||0]),['Atención promedio (días)',d.averageLeadDays]]) {const card=element('article',null,'card');card.append(element('h2',label),element('div',value,'stat'));stats.append(card);}
  const priorities=document.querySelector('#priority-stats');for(const p of ['ALTA','MEDIA','BAJA']) {const card=element('div');card.append(element('h3',p),element('p',d.byPriority[p]||0,'stat'));priorities.append(card);}
  const months=document.querySelector('#monthly-stats');for(const month of d.byMonth) {const row=element('tr');row.append(element('td',month.month),element('td',month.count));months.append(row);}if(!d.total)months.append(element('tr','No hay tickets para mostrar.'));
}
