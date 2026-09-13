import { mkdirSync, existsSync, readFileSync, writeFileSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
const root='.tmp/e2e-auth';
mkdirSync(root+'/secrets/e2e',{recursive:true});
// Preserve the existing Core legacy-demo fixture when the optional file provider is used.
// Read its local WireMock contract rather than printing or duplicating credential values.
mkdirSync(root+'/secrets/demo',{recursive:true});
if(!existsSync(root+'/secrets/demo/legacy.json')){
  const fixture=JSON.parse(readFileSync('infra/mock-legacy/mappings/token.json','utf8'));
  const fields=new URLSearchParams(fixture.request.bodyPatterns.map(pattern=>pattern.contains).join('&'));
  const username=fields.get('username'),password=fields.get('password');
  if(!username||!password)throw new Error('Legacy demo fixture credentials are unavailable');
  writeFileSync(root+'/secrets/demo/legacy.json',JSON.stringify({username,password,version:'demo-fixture-v1'}),{mode:0o600});
}
let secret;
if (existsSync(root+'/secrets/e2e/legacy.json')) secret=JSON.parse(readFileSync(root+'/secrets/e2e/legacy.json','utf8'));
else {
  secret={username:'e2e-service',password:randomBytes(32).toString('base64url'),version:'e2e-v1'};
  writeFileSync(root+'/secrets/e2e/legacy.json',JSON.stringify(secret),{mode:0o600});
}
writeFileSync(root+'/demo.env','TEST_LEGACY_PASSWORD='+secret.password+'\n',{mode:0o600});
if (!existsSync(root+'/users.json')) {
  writeFileSync(root+'/users.json',JSON.stringify(Object.fromEntries(
    ['dual-access','sso-only','legacy-only','none'].map(suffix=>['e2e.'+suffix,randomBytes(24).toString('base64url')]))),{mode:0o600});
}
console.log('Local test secrets prepared under ignored .tmp/e2e-auth; no credential values printed.');
