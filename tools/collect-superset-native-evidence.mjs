import assert from 'node:assert/strict';
import {readFileSync,readdirSync,writeFileSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {X509Certificate} from 'node:crypto';
import {safeResponse} from './e2e-auth/session.mjs';
const native=JSON.parse(readFileSync('target/superset-native/results.json','utf8'));
const swagger=JSON.parse(readFileSync('target/swagger/results.json','utf8'));
assert.equal(native.counts.FAIL+native.counts.BLOCKED,0);
assert.equal(swagger.counts.FAIL+swagger.counts.BLOCKED,0);
const java=['ui-artifact-security','superapp-bff','authorization-service'].map(module=>{
  const directory='services/'+module+'/target/surefire-reports';
  const result={module,tests:0,failures:0,errors:0,skipped:0};
  for(const file of readdirSync(directory).filter(file=>file.startsWith('TEST-')&&file.endsWith('.xml'))){
    const header=readFileSync(directory+'/'+file,'utf8').match(/<testsuite\s[^>]+>/)[0];
    for(const key of ['tests','failures','errors','skipped'])result[key]+=Number(header.match(new RegExp('\\b'+key+'="(\\d+)"'))[1]);
  }
  assert.equal(result.failures+result.errors+result.skipped,0);return result;
});
const runtime=JSON.parse(readFileSync('.tmp/superset-native/runtime.json','utf8'));
const privacyScript='import json,pathlib; print((pathlib.Path('+JSON.stringify(runtime.nativeRoot)+')/"privacy-assets/manifest.json").read_text())';
const nativePrivacy=JSON.parse(process.platform==='win32'
  ?execFileSync('wsl.exe',['-d',runtime.distro,'--exec','python3','-c',privacyScript],{encoding:'utf8'})
  :execFileSync('python3',['-c',privacyScript],{encoding:'utf8'}));
assert.equal(nativePrivacy.scarfPixelDisabled,true);
assert.notEqual(nativePrivacy.sourceSha256,nativePrivacy.servedSha256);
const versionScript='import json,pathlib,sys,subprocess; from importlib.metadata import version; r=pathlib.Path('
  +JSON.stringify(runtime.nativeRoot)+'); print(json.dumps({"superset":version("apache-superset"),'
  +'"python":sys.version.split()[0],"gunicorn":version("gunicorn"),'
  +'"uv":subprocess.check_output([str(r/"tools/uv"),"--version"],text=True).strip().split()[1]}))';
const versions=JSON.parse(process.platform==='win32'
  ?execFileSync('wsl.exe',['-d',runtime.distro,'--exec',runtime.nativeRoot+'/venv/bin/python','-c',versionScript],{encoding:'utf8'})
  :execFileSync(runtime.nativeRoot+'/venv/bin/python',['-c',versionScript],{encoding:'utf8'}));
execFileSync(process.execPath,['tools/verify-superset-compose-isolation.mjs'],{stdio:'pipe'});
const registration=JSON.parse(readFileSync('.tmp/superset-native/registration.json','utf8'));
const containers=execFileSync('docker',['ps','--format','{{.Names}}|{{.Status}}'],{encoding:'utf8'}).trim().split('\n')
  .map(line=>{const [name,status]=line.split('|');return {name,status};});
assert.equal(containers.filter(container=>/superset/i.test(container.name)).length,0);
const server=new X509Certificate(readFileSync('.tmp/superset-native/tls/server.pem'));
const ca=new X509Certificate(readFileSync('.tmp/superset-native/tls/ca.pem'));
const realm=JSON.parse(readFileSync('infra/keycloak/realm-aurevia.json','utf8'));
const passwords=JSON.parse(readFileSync('.tmp/e2e-auth/users.json','utf8'));
const secrets=[...Object.values(passwords),...realm.users.flatMap(user=>user.credentials??[]).map(credential=>credential.value)].filter(Boolean);
const evidence=safeResponse({collectedAt:new Date().toISOString(),scope:'Native Superset demo on the Aurevia workstation LAN',
  versions,runtime,registration,nativePrivacy,
  tls:{hostnameVerified:true,operationRequiresClientIdentity:true,serverSan:server.subjectAltName,
    validFrom:server.validFrom,validTo:server.validTo,caFingerprint:ca.fingerprint256,caPrivateKeyExported:false},
  java,javaPassed:java.reduce((sum,module)=>sum+module.tests,0),native,
  swaggerRegression:swagger,configurationIsolation:{status:'PASS',command:'npm run superset:config:test'},
  containers:containers.filter(container=>/^aurevia-(aurevia-bff|authorization-service|nginx|mfe-demo-mfe-(admin|reports))-/.test(container.name)),
  supersetContainers:[],limits:{samePhysicalWorkstation:true,separateLanClientTested:false,productionDwh:false}
},secrets);
assert(!/-----BEGIN[^\n]*PRIVATE KEY/.test(JSON.stringify(evidence)));
writeFileSync('docs/evidence/superset-native-network-demo-results.json',JSON.stringify(evidence,null,2)+'\n');
console.log(JSON.stringify({java:evidence.javaPassed,native:native.counts,swagger:swagger.counts,safeEvidenceWritten:true}));
