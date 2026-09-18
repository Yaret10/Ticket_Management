import {test} from 'node:test';
import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';

let run = 0;
async function module() {
  globalThis.location={origin:'http://localhost:8080',pathname:'/tickets',search:'',assign:()=>{}};
  Object.defineProperty(globalThis,'navigator',{value:{},configurable:true});
  return import(pathToFileURL('src/main/resources/static/js/api.js').href+'?test='+(++run));
}
const response=(status,body={})=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}});

test('Fetch sends real CSRF header and retries a POST only once after renewal',async()=> {
  const api=await module();let requests=0,renewals=0;
  globalThis.fetch=async(path,options)=> {
    if(path==='/api/v1/auth/csrf') return response(200,{token:'csrf-test',headerName:'X-XSRF-TOKEN'});
    if(path==='/api/v1/auth/me') return response(401);
    if(path==='/api/v1/auth/refresh') {renewals++;assert.equal(options.headers['X-XSRF-TOKEN'],'csrf-test');return response(200);}
    requests++;assert.equal(options.credentials,'same-origin');assert.equal(options.headers.get('X-XSRF-TOKEN'),'csrf-test');assert.equal(options.body,'{"description":"test"}');
    return requests===1 ? response(401) : response(201,{id:1});
  };
  const result=await api.json('/api/v1/tickets',{method:'POST',body:'{"description":"test"}'});
  assert.equal(result.id,1);assert.equal(requests,2);assert.equal(renewals,1);
});

test('Concurrent expired requests share a single refresh operation',async()=> {
  const api=await module();let renewals=0;const calls=new Map();
  globalThis.fetch=async(path)=> {
    if(path==='/api/v1/auth/csrf') return response(200,{token:'csrf-test',headerName:'X-XSRF-TOKEN'});
    if(path==='/api/v1/auth/me') return response(401);
    if(path==='/api/v1/auth/refresh') {renewals++;await new Promise(resolve=>setTimeout(resolve,20));return response(200);}
    calls.set(path,(calls.get(path)||0)+1);return response(calls.get(path)===1?401:200);
  };
  await Promise.all([api.api('/api/v1/tickets'),api.api('/api/v1/reports/dashboard')]);
  assert.equal(renewals,1);assert.equal(calls.get('/api/v1/tickets'),2);assert.equal(calls.get('/api/v1/reports/dashboard'),2);
});

test('A second 401 stops retrying and recovery does not allow foreign destinations',async()=> {
  const api=await module();let attempts=0;
  globalThis.fetch=async(path)=> {
    if(path==='/api/v1/auth/csrf') return response(200,{token:'test',headerName:'X-XSRF-TOKEN'});
    if(path==='/api/v1/auth/refresh') return response(200);
    if(path==='/api/v1/auth/me') return response(401);
    attempts++;return response(401,{detail:'Autenticación vencida'});
  };
  await assert.rejects(api.api('/api/v1/tickets'),/Autenticación vencida/);assert.equal(attempts,2);
  assert.equal(api.safeNext('https://example.com'),'/menu');
  assert.equal(api.safeNext('//example.com'),'/menu');
  assert.equal(api.safeNext('/tickets/12?q=demo'),'/tickets/12?q=demo');
});

test('Current-user requests also renew an expired access token',async()=> {
  const api=await module();let currentUserRequests=0,renewals=0;
  globalThis.fetch=async(path)=> {
    if(path==='/api/v1/auth/csrf') return response(200,{token:'test',headerName:'X-XSRF-TOKEN'});
    if(path==='/api/v1/auth/refresh') {renewals++;return response(200);}
    currentUserRequests++;return currentUserRequests<=2?response(401):response(200,{id:1});
  };
  assert.equal((await api.json('/api/v1/auth/me')).id,1);assert.equal(renewals,1);assert.equal(currentUserRequests,3);
});

test('Preview opens during click and renews access before navigating without exposing the opener',async()=> {
  const api=await module();let requests=0,renewals=0,destination=null;
  const viewer={opener:{},document:{body:{}},location:{replace:path=>destination=path},close:()=>assert.fail('Window should remain open')};
  globalThis.window={open:()=>viewer};
  globalThis.fetch=async(path,options)=>{
    if(path==='/api/v1/auth/csrf')return response(200,{token:'test',headerName:'X-XSRF-TOKEN'});
    if(path==='/api/v1/auth/me')return response(401);
    if(path==='/api/v1/auth/refresh'){renewals++;return response(200);}
    assert.equal(viewer.opener,null);assert.equal(options.method,'HEAD');requests++;
    return response(requests===1?401:200);
  };
  await api.preview('/api/v1/tickets/1/attachments/2/view');
  assert.equal(destination,'/api/v1/tickets/1/attachments/2/view');assert.equal(requests,2);assert.equal(renewals,1);
});

test('Preview closes on denied access and reports blocked popups',async()=> {
  const api=await module();let closed=false,navigated=false;
  globalThis.window={open:()=>({document:{body:{}},location:{replace:()=>navigated=true},close:()=>closed=true})};
  globalThis.fetch=async()=>response(403,{detail:'Sin permiso'});
  await assert.rejects(api.preview('/api/v1/tickets/1/attachments/2/view'),/Sin permiso/);
  assert.equal(closed,true);assert.equal(navigated,false);
  globalThis.window={open:()=>null};
  await assert.rejects(api.preview('/api/v1/tickets/1/attachments/2/view'),/Permita abrir pestañas/);
});
