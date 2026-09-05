let csrf: {headerName:string;token:string}|undefined;

export async function adminApi(path:string,init:RequestInit={}):Promise<any> {
  const method=init.method??'GET';
  const headers:Record<string,string>={
    'Content-Type':'application/json',
    ...(init.headers as Record<string,string>??{}),
  };
  if(!['GET','HEAD'].includes(method)) {
    const token=csrf??await fetch('/api/v1/csrf',{credentials:'same-origin'})
      .then(async response=>{
        if(!response.ok) throw new Error(`CSRF HTTP ${response.status}`);
        return response.json();
      });
    csrf=token;
    headers[token.headerName]=token.token;
  }
  const response=await fetch(`/api/v1/admin${path}`,{
    ...init,headers,credentials:'same-origin',
  });
  if(!response.ok) throw new Error((await response.text())||`HTTP ${response.status}`);
  return response.status===204?undefined:response.json();
}
