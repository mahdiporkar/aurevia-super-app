import assert from 'node:assert/strict';
import {execFileSync,spawn} from 'node:child_process';
import {existsSync,mkdirSync,readFileSync,writeFileSync,openSync,closeSync} from 'node:fs';
import {homedir} from 'node:os';
import {join,resolve} from 'node:path';
import {request as httpsRequest} from 'node:https';

const windows=process.platform==='win32', distro=process.env.AUREVIA_SUPERSET_WSL_DISTRO??'Ubuntu';
const directory=resolve('.tmp/superset-native');mkdirSync(directory,{recursive:true});
const source=resolve('infra/superset-native'), server=resolve('tools/superset-native-ingress.mjs');
const nativeSource=windows?execFileSync('wsl.exe',['-d',distro,'--exec','wslpath','-a',source.replaceAll('\\','/')],{encoding:'utf8'}).trim():source;
const nativeRoot=process.env.AUREVIA_SUPERSET_NATIVE_ROOT??(windows
  ?execFileSync('wsl.exe',['-d',distro,'--exec','python3','-c','from pathlib import Path; print(Path.home()/".local/share/aurevia-superset-demo")'],{encoding:'utf8'}).trim()
  :join(homedir(),'.local/share/aurevia-superset-demo'));
function run(arguments_,inherit=true){
  return execFileSync(windows?'wsl.exe':arguments_[0],windows
    ?['-d',distro,'--exec','env','AUREVIA_SUPERSET_NATIVE_ROOT='+nativeRoot,
      'AUREVIA_SUPERSET_RANGE_DOWNLOAD='+(process.env.AUREVIA_SUPERSET_RANGE_DOWNLOAD??'0'),...arguments_]
    :arguments_.slice(1),{encoding:inherit?undefined:'utf8',stdio:inherit?'inherit':'pipe',
      env:{...process.env,AUREVIA_SUPERSET_NATIVE_ROOT:nativeRoot}});
}
function nativeRead(path){return run(['cat',path],false);}
const pidfile=join(directory,'ingress.pid');
function running(){
  if(!existsSync(pidfile))return false;
  const pid=Number(readFileSync(pidfile,'utf8'));if(!Number.isInteger(pid)||pid<1)return false;
  try{
    const command=windows?execFileSync('powershell.exe',['-NoProfile','-Command',
      '(Get-CimInstance Win32_Process -Filter "ProcessId = '+pid+'").CommandLine'],{encoding:'utf8'})
      :readFileSync('/proc/'+pid+'/cmdline','utf8');
    return command.toLowerCase().includes(server.toLowerCase());
  }catch{return false;}
}
function health(url,identity){return new Promise((resolvePromise,reject)=>{
  const request=httpsRequest(url,identity,response=>{response.resume();response.on('end',()=>
    response.statusCode===200?resolvePromise():reject(new Error('Native health HTTP '+response.statusCode)));});
  request.setTimeout(3000,()=>request.destroy(new Error('Native health timeout')));request.on('error',reject);request.end();
});}
const command=process.argv[2]??'status';
if(command==='up'){
  let host=process.env.AUREVIA_SUPERSET_HOST;
  if(!host&&windows){
    const addresses=JSON.parse(execFileSync('powershell.exe',['-NoProfile','-Command',
      'Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -notlike "vEthernet*" -and $_.IPAddress -ne "127.0.0.1" -and $_.IPAddress -notlike "169.254.*" } | Select-Object InterfaceAlias,IPAddress | ConvertTo-Json'],{encoding:'utf8'}));
    host=(Array.isArray(addresses)?addresses:[addresses])[0]?.IPAddress;
  }
  assert(host&&/^\d{1,3}(?:\.\d{1,3}){3}$/.test(host),'Set AUREVIA_SUPERSET_HOST to the native host LAN IPv4 address');
  run(['bash',nativeSource+'/setup.sh']);
  run([nativeRoot+'/venv/bin/python',nativeSource+'/certificates.py',host]);
  run([nativeRoot+'/venv/bin/python',nativeSource+'/manage.py','start']);
  const tlsDirectory=join(directory,'tls');mkdirSync(tlsDirectory,{recursive:true});
  for(const file of ['ca.pem','server.pem','server-key.pem','client.pem','client-key.pem'])
    writeFileSync(join(tlsDirectory,file),nativeRead(nativeRoot+'/tls/'+file),{mode:0o600});
  const settings=JSON.parse(nativeRead(nativeRoot+'/settings.json'));
  const config={host,ingressSecret:settings.ingressSecret,countersFile:join(directory,'counters.json'),
    tls:{ca:join(tlsDirectory,'ca.pem'),serverCert:join(tlsDirectory,'server.pem'),serverKey:join(tlsDirectory,'server-key.pem')}};
  const configFile=join(directory,'ingress.json');writeFileSync(configFile,JSON.stringify(config,null,2)+'\n',{mode:0o600});
  if(!running()){
    const output=openSync(join(directory,'ingress.log'),'a');
    const processHandle=spawn(process.execPath,[server,configFile],{detached:true,windowsHide:true,stdio:['ignore',output,output]});
    writeFileSync(pidfile,String(processHandle.pid)+'\n');processHandle.unref();closeSync(output);
  }
  const identity={ca:readFileSync(join(tlsDirectory,'ca.pem')),cert:readFileSync(join(tlsDirectory,'client.pem')),key:readFileSync(join(tlsDirectory,'client-key.pem'))};
  let ready=false;
  for(let i=0;i<60;i++){
    try{await Promise.all([health('https://'+host+':8089/health',identity),health('https://'+host+':8088/health',identity)]);ready=true;break;}
    catch{await new Promise(resolvePromise=>setTimeout(resolvePromise,1000));}
  }
  assert(ready,'Native Superset processes did not become ready; inspect private native server logs');
  writeFileSync(join(directory,'runtime.json'),JSON.stringify({host,nativeRoot,distro:windows?distro:null,
    publicUrl:'https://'+host+':8089',operationUrl:'https://'+host+':8088',
    outsideContainers:true,publicStaticOnly:true,operationRequiresMtls:true},null,2)+'\n');
  writeFileSync(join(directory,'core.env'),'AUREVIA_SUPERSET_HOST='+host+'\n');
  writeFileSync(join(directory,'reports.json'),nativeRead(nativeRoot+'/reports.json'));
  console.log('Native public Superset: https://'+host+':8089 (static assets only)');
  console.log('Native operation Superset: https://'+host+':8088 (BFF client certificate required)');
}else if(command==='down'){
  if(running())process.kill(Number(readFileSync(pidfile,'utf8')));
  run(['python3',nativeSource+'/manage.py','stop']);console.log('Native services stopped; databases and private identities retained');
}else if(command==='status'){
  run(['python3',nativeSource+'/manage.py','status']);console.log('Native HTTPS ingress: '+(running()?'running':'stopped'));
}else{
  throw new Error('Usage: node tools/superset-native.mjs up|down|status');
}
