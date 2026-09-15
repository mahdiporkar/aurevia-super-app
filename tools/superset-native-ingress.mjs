import {createServer as createHttpsServer} from 'node:https';
import {request as httpRequest} from 'node:http';
import {readFileSync,writeFileSync} from 'node:fs';
import {resolve} from 'node:path';

const config=JSON.parse(readFileSync(process.argv[2],'utf8'));
const tls={key:readFileSync(config.tls.serverKey),cert:readFileSync(config.tls.serverCert),ca:readFileSync(config.tls.ca)};
const counts={public:{requests:0,static:0,rejected:0},operation:{requests:0,chartData:0,identityRequests:0}};
const counters=resolve(config.countersFile);
function record(){writeFileSync(counters,JSON.stringify(counts,null,2)+'\n');}
for(const [zone,port,backend] of [['public',8089,19089],['operation',8088,19088]]){
  const server=createHttpsServer({...tls,requestCert:zone==='operation',rejectUnauthorized:zone==='operation'},(request,response)=>{
    counts[zone].requests++;
    const path=new URL(request.url,'https://native.invalid').pathname;
    if(zone==='public'&&path!=='/health'&&!path.startsWith('/static/')){
      counts.public.rejected++;record();response.writeHead(404,{'Content-Type':'text/plain'});response.end('Static assets only\n');return;
    }
    if(zone==='public'&&path.startsWith('/static/'))counts.public.static++;
    if(zone==='operation'){
      if(!request.socket.authorized){response.writeHead(403);response.end();return;}
      const certificate=request.socket.getPeerCertificate();
      if(certificate.subject?.CN!=='aurevia-bff-native-demo'){response.writeHead(403);response.end();return;}
      if(path==='/api/v1/chart/data')counts.operation.chartData++;
      if(request.headers['x-aurevia-subject'])counts.operation.identityRequests++;
    }
    record();
    const headers={...request.headers,host:`127.0.0.1:${backend}`};
    delete headers.authorization;delete headers['x-aurevia-native-ingress'];
    if(zone==='operation')headers['x-aurevia-native-ingress']=config.ingressSecret;
    const upstream=httpRequest({hostname:'127.0.0.1',port:backend,path:request.url,method:request.method,headers},result=>{
      response.writeHead(result.statusCode,result.headers);result.pipe(response);
    });
    upstream.on('error',()=>{if(!response.headersSent)response.writeHead(502,{'Content-Type':'text/plain'});response.end('Native Superset unavailable\n');});
    request.pipe(upstream);
  });
  server.on('error',error=>{console.error(`Native ${zone} ingress failed: ${error.code}`);process.exit(1);});
  server.listen(port,'0.0.0.0',()=>console.log(`Native ${zone} HTTPS ingress listening on ${port}`));
}
record();
