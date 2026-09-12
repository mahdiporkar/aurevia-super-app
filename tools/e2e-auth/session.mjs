import assert from 'node:assert/strict';
const redirects=new Set([301,302,303,307,308]);
const decode=value=>value.replaceAll('&amp;','&').replaceAll('&quot;','"').replaceAll('&#39;',"'");
export class Session {
  #cookies=new Map();
  constructor(origin) { this.origin=new URL(origin); this.csrf=undefined; }
  cookie(name) { return [...this.#cookies.values()].find(value=>value.name===name); }
  async request(path,options={}) {
    let url=new URL(path,this.origin), method=options.method??'GET', body=options.body;
    const headers=new Headers(options.headers);
    for(let count=0;count<15;count++) {
      headers.delete('cookie');
      const cookies=[...this.#cookies.values()].filter(cookie=>url.hostname===cookie.host&&url.pathname.startsWith(cookie.path));
      if(cookies.length) headers.set('cookie',cookies.map(cookie=>cookie.name+'='+cookie.value).join('; '));
      const response=await fetch(url,{method,body,headers,redirect:'manual',signal:AbortSignal.timeout(15000)});
      for(const value of response.headers.getSetCookie()) {
        const parts=value.split(';').map(value=>value.trim());
        const i=parts[0].indexOf('='), name=parts[0].slice(0,i), cookieValue=parts[0].slice(i+1);
        const path=parts.find(value=>/^path=/i.test(value))?.slice(5)??'/';
        const key=url.hostname+'|'+path+'|'+name;
        if(!cookieValue||parts.some(value=>/^max-age=0$/i.test(value))) this.#cookies.delete(key);
        else this.#cookies.set(key,{name,value:cookieValue,host:url.hostname,path,httpOnly:parts.some(value=>/^httponly$/i.test(value))});
      }
      if(!redirects.has(response.status)) return response;
      const next=new URL(response.headers.get('location'),url);
      // Login may only redirect within this local demo. Credentials never follow arbitrary hosts.
      assert(['localhost','127.0.0.1'].includes(next.hostname),'Unexpected login redirect host');
      url=next;
      if(response.status===303 || ([301,302].includes(response.status)&&method==='POST')) {
        method='GET';body=undefined;headers.delete('content-type');
      }
    }
    throw new Error('OIDC redirect limit exceeded');
  }
  async json(path,method='GET',body,correlationId=crypto.randomUUID()) {
    const headers={Accept:'application/json','X-Correlation-ID':correlationId};
    if(method!=='GET') {
      this.csrf??=await this.json('/api/v1/csrf');
      assert.equal(this.csrf.status,200,'CSRF request rejected');
      headers[this.csrf.body.headerName]=this.csrf.body.token;
      headers['Content-Type']='application/json';
    }
    const response=await this.request(path,{method,headers,...(body!==undefined?{body:JSON.stringify(body)}:{})});
    const text=await response.text();
    let data; try { data=text?JSON.parse(text):null; } catch { data=null; }
    return {status:response.status,body:data,correlationId:response.headers.get('X-Correlation-ID')??correlationId};
  }
  async login(username,password) {
    const response=await this.request('/auth/login');
    const html=await response.text();
    const form=html.match(/<form\b[^>]*>/i)?.[0];
    assert(form,'Keycloak login form unavailable');
    const action=decode(form.match(/\baction=["']([^"']+)["']/i)?.[1]??'');
    assert(action,'Keycloak login action unavailable');
    const actionUrl=new URL(action,response.url);
    assert.equal(actionUrl.origin,'http://localhost:8180','Unexpected login credential destination');
    const fields=new URLSearchParams({username,password,credentialId:''});
    const loggedIn=await this.request(actionUrl,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:fields});
    await loggedIn.arrayBuffer();
    const me=await this.json('/api/v1/me');
    assert.equal(me.status,200,'OIDC login failed');
    assert.equal(me.body.username,username,'OIDC username mismatch');
    assert(this.cookie('AUREVIA_SESSION')?.httpOnly,'Session cookie must be HttpOnly');
    return me.body;
  }
}
export function safeResponse(value,secrets=[]) {
  const serialized=JSON.stringify(value);
  assert(!/(?:eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+|legacy_[A-Za-z0-9_-]{30,}|"(?:(?:access|refresh|id)[_-]?token|authorization)"\s*:)/i.test(serialized),'Response contains token material');
  for(const secret of secrets) assert(!serialized.includes(secret),'Response contains credential material');
  return value;
}
