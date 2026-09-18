import {json,download} from './api.js';import {element,date,message,busy} from './ui.js';
export async function init() {
  let page=0, totalPages=0;const form=document.querySelector('#filters');
  const params=()=>{const p=new URLSearchParams();for(const [key,value] of new FormData(form)) if(value) p.set(key,value);return p;};
  async function load() {
    document.querySelector('#results-info').textContent='Cargando tickets…';const p=params();p.set('page',page);p.set('size','20');
    try {const result=await json('/api/v1/tickets?'+p);totalPages=result.totalPages;const body=document.querySelector('#tickets-body');body.replaceChildren();
      for(const ticket of result.content) {const row=element('tr');const code=element('td');const link=element('a',ticket.code);link.href='/tickets/'+ticket.id;code.append(link);row.append(code,element('td',ticket.equipment),element('td',ticket.requester+' / '+ticket.area));for(const value of [ticket.priority,ticket.state]) {const cell=element('td');cell.append(element('span',value,'badge '+value));row.append(cell);}row.append(element('td',date(ticket.createdAt)));body.append(row);}
      document.querySelector('#results-info').textContent=result.totalElements ? `${result.totalElements} tickets encontrados.` : 'No se encontraron tickets con estos filtros.';
      document.querySelector('#page-info').textContent=`Página ${totalPages ? page+1 : 0} de ${totalPages}`;
      document.querySelector('#previous').disabled=page===0;document.querySelector('#next').disabled=page+1>=totalPages;
    } catch(error) {document.querySelector('#results-info').textContent='No se pudo cargar el listado.';message(error.message,true);}
  }
  form.addEventListener('submit',event=>{event.preventDefault();page=0;load();});document.querySelector('#previous').addEventListener('click',()=>{if(page>0){page--;load();}});document.querySelector('#next').addEventListener('click',()=>{if(page+1<totalPages){page++;load();}});
  document.querySelector('#export').addEventListener('click',event=>busy(event.target,()=>download('/api/v1/reports/excel?'+params(),'tickets.xlsx'),'Exportando…'));await load();
}
