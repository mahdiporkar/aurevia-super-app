/** URL syntax for approved internal service names as well as LAN/public hosts. */
export function isServiceUrl(value:unknown,originOnly=false):boolean{
  if(typeof value!=='string'||!value||value!==value.trim())return false;
  try{
    const url=new URL(value);
    return ['http:','https:'].includes(url.protocol)&&Boolean(url.hostname)
      &&!url.username&&!url.password&&!url.search&&!url.hash
      &&(!originOnly||url.pathname==='/');
  }catch{return false;}
}
export function serviceUrlRule(originOnly=false){
  return {validator:(_rule:unknown,value:unknown)=>{
    if(value===undefined||value===null||value==='')return Promise.resolve();
    return isServiceUrl(value,originOnly)?Promise.resolve():Promise.reject(new Error(
      originOnly?'Origin معتبر HTTP/HTTPS بدون مسیر، query یا اطلاعات ورود وارد کنید.':
        'URL معتبر HTTP/HTTPS بدون query یا اطلاعات ورود وارد کنید.'));
  }};
}
