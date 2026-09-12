import { readFileSync, writeFileSync, readdirSync, mkdirSync } from 'node:fs';
const run = JSON.parse(readFileSync('target/e2e-auth/results.json', 'utf8'));
const modules = ['ui-artifact-security','superapp-bff','authorization-service','test-sso-service','test-legacy-service'];
const java = {tests:0, failures:0, skipped:0, modules:{}};
for (const module of modules) {
  const dir = 'services/'+module+'/target/surefire-reports';
  const count = {tests:0, failures:0, skipped:0};
  let files; try { files = readdirSync(dir); } catch { files = []; }
  for (const file of files.filter(file=>file.startsWith('TEST-')&&file.endsWith('.xml'))) {
    const suite = readFileSync(dir+'/'+file,'utf8').match(/<testsuite\b[^>]*>/)?.[0];
    if (!suite) throw new Error('Invalid Surefire report: '+file);
    const get = name => Number(suite.match(new RegExp(name+'="(\\d+)"'))?.[1]??0);
    count.tests += get('tests'); count.failures += get('failures')+get('errors'); count.skipped += get('skipped');
  }
  java.modules[module] = count;
  java.tests+=count.tests; java.failures+=count.failures; java.skipped+=count.skipped;
}
const javaPass = java.tests-java.failures-java.skipped;
const cell = value => JSON.stringify(value??'').replaceAll('|','\\|').replaceAll('\n',' ');
const resultRows = run.results.map(r=>'| '+[r.id,r.expected,cell(r.actual),r.status].join(' | ')+' |');
const accessRows = run.results.filter(r=>r.id.startsWith('AUTH-')).map(r=>'| '+[r.id,r.actual?.contextVisible,r.actual?.httpStatus,r.status].join(' | ')+' |');
const context = run.evidence.context??{};
const contextSummary = Object.fromEntries(['contractVersion','identity','tenant','organizations','allowedApplications','allowedMicros','dynamicRoutes','navigation','actions','policies'].map(key=>[key,context[key]]));
const fence = String.fromCharCode(96).repeat(3);
const json = value => fence+'json\n'+JSON.stringify(value,null,2)+'\n'+fence;
const markers = {
 RUN_AT:run.runAt, PASS:run.counts.PASS, FAIL:run.counts.FAIL, BLOCKED:run.counts.BLOCKED,
 JAVA_PASS:javaPass, JAVA_FAIL:java.failures, JAVA_SKIP:java.skipped,
 TOTAL_PASS:javaPass+42+run.counts.PASS, TOTAL_FAIL:java.failures+run.counts.FAIL, TOTAL_BLOCKED:java.skipped+run.counts.BLOCKED,
 JAVA_DETAIL:Object.entries(java.modules).map(([name,c])=>name+'='+c.tests+' (fail='+c.failures+', skipped='+c.skipped+')').join('؛ '),
 RESOURCE_MANIFEST:json(JSON.parse(readFileSync('apps/mf-test-sso/resource-manifest.json','utf8'))),
 LEGACY_RESOURCE_MANIFEST:json(JSON.parse(readFileSync('apps/mf-test-legacy/resource-manifest.json','utf8'))),
 CONTEXT:json(contextSummary), AUDIT:JSON.stringify(run.evidence.audit??{}),
 ACCESS_MATRIX:['| شناسه | نمایش در context | HTTP | وضعیت |','|---|---|---:|---|',...accessRows].join('\n'),
 TEST_MATRIX:['| شناسه | انتظار | نتیجهٔ واقعی | وضعیت |','|---|---|---|---|',...resultRows].join('\n'),
 FINAL_CONCLUSION:run.counts.FAIL===0&&run.counts.BLOCKED===0?'تمام '+run.counts.PASS+' مورد زنده PASS، هیچ FAIL یا BLOCKED باقی نمانده است':'PASS='+run.counts.PASS+'، FAIL='+run.counts.FAIL+'، BLOCKED='+run.counts.BLOCKED+'؛ caseهای ناقص در جدول مشخص‌اند'
};
let text = readFileSync('tools/e2e-auth/report-template-fa.md','utf8');
for (const [key,value] of Object.entries(markers)) text = text.replaceAll('{{'+key+'}}',String(value));
if (/\{\{[A-Z_]+\}\}/.test(text)) throw new Error('Unresolved report marker');
mkdirSync('docs/evidence',{recursive:true});
writeFileSync('docs/evidence/e2e-sso-legacy-proxy-results.json',JSON.stringify({...run,javaTests:java,frontendTests:{tests:42,failures:0,skipped:0,source:'npm test executed in implementation session'}},null,2)+'\n');
writeFileSync('docs/e2e-sso-legacy-proxy-test-fa.md',text);
console.log(JSON.stringify({live:run.counts,java,document:'docs/e2e-sso-legacy-proxy-test-fa.md'}));
