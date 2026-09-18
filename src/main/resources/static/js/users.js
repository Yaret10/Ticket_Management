import {json} from './api.js';import {element,message,busy} from './ui.js';
export async function init(me) {
  const form=document.querySelector('#user-form');if(!me.roles.includes('TI')||!me.manageUsers){form.hidden=true;message('El registro de usuarios requiere autorización explícita.',true);return;}
  const areas=await json('/api/v1/areas');for(const area of areas) {const option=element('option',area.name);option.value=area.id;form.areaId.append(option);checkbox('#area-options','managedAreas',area.id,area.name);}
  for(const role of me.grantableRoles) checkbox('#role-options','roles',role,role);
  const managed=document.querySelector('#managed-area-fieldset');
  function updateAreas(){const allowed=Boolean(form.querySelector('[name=roles][value=GERENTE]:checked'));managed.hidden=!allowed;managed.disabled=!allowed;if(!allowed)managed.querySelectorAll('input').forEach(input=>input.checked=false);}
  form.addEventListener('change',updateAreas);updateAreas();
  form.addEventListener('submit',event=>{event.preventDefault();busy(event.submitter,async()=>{const data=new FormData(form);const fields=Object.fromEntries(data);fields.areaId=Number(fields.areaId);fields.roles=data.getAll('roles');fields.managedAreas=data.getAll('managedAreas').map(Number);if(!fields.roles.length)throw new Error('Seleccione al menos un rol.');await json('/api/v1/users',{method:'POST',body:JSON.stringify(fields)});form.reset();updateAreas();message('Usuario registrado correctamente.');},'Registrando…');});
}
function checkbox(container,name,value,text) {const label=element('label',null,'check-label');const input=element('input');input.type='checkbox';input.name=name;input.value=value;label.append(input,document.createTextNode(' '+text));document.querySelector(container).append(label);}
