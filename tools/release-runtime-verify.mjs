import assert from 'node:assert/strict';

const origin=(process.env.AUREVIA_ORIGIN??'http://localhost:8443').replace(/\/$/,'');
const showcase=(process.env.AUREVIA_SHOWCASE_URL??'https://mahdiporkar.github.io/aurevia-super-app').replace(/\/$/,'');
const adminPaths=[
  'operator-guide','ou-access/ous','ou-access/groups','ou-access/applications','ou-access/explain',
  'access-studio','panels','proxy-routes/targets','proxy-routes/routes','proxy-routes/operations',
  'outbound-connections','outbound-auth','integration-test','superset-instances','identity','logs/api',
  'logs/audit','superset'
];

async function timedFetch(url,options={}) {
  const started=performance.now();
  const response=await fetch(url,{redirect:'manual',signal:AbortSignal.timeout(10_000),...options});
  return {response,elapsedMs:performance.now()-started};
}

const root=await timedFetch(`${origin}/`);
assert.equal(root.response.status,200,'shell root must return 200');
assert.match(root.response.headers.get('content-type')??'',/^text\/html/i);
assert.ok(root.response.headers.get('content-security-policy'),'shell must emit CSP');
assert.equal(root.response.headers.get('x-content-type-options'),'nosniff');
assert.ok(root.response.headers.get('referrer-policy'));
assert.ok(root.response.headers.get('permissions-policy'));

const unauthorized=await timedFetch(`${origin}/api/v1/me`);
assert.equal(unauthorized.response.status,401,'unauthenticated identity endpoint must fail closed');

const deepLinks=await Promise.all(adminPaths.map(path=>timedFetch(`${origin}/admin/${path}`)));
for(const [index,result] of deepLinks.entries()) {
  assert.equal(result.response.status,200,`deep link /admin/${adminPaths[index]} must return 200`);
  assert.match(result.response.headers.get('content-type')??'',/^text\/html/i);
}

// A bounded concurrency probe catches connection-pool starvation and intermittent edge failures.
const loadStarted=performance.now();
const probes=await Promise.all(Array.from({length:200},(_,index)=>
  timedFetch(`${origin}${index%2===0?'/':'/admin/operator-guide'}`)));
const loadElapsedMs=performance.now()-loadStarted;
assert.equal(probes.filter(({response})=>response.status!==200).length,0,'concurrent edge probe failures');
const sorted=probes.map(({elapsedMs})=>elapsedMs).sort((a,b)=>a-b);
const p95=sorted[Math.ceil(sorted.length*.95)-1];
assert.ok(p95<5_000,`edge p95 ${p95.toFixed(1)}ms exceeded 5000ms release ceiling`);

const publicPage=await timedFetch(`${showcase}/`);
assert.equal(publicPage.response.status,200,'GitHub Pages showcase must return 200');
assert.match(publicPage.response.headers.get('content-type')??'',/^text\/html/i);
const publicHtml=await publicPage.response.text();
for(const asset of ['styles.css','app.js']) {
  assert.ok(publicHtml.includes(asset),`showcase HTML must reference ${asset}`);
  const result=await timedFetch(`${showcase}/${asset}`);
  assert.equal(result.response.status,200,`showcase ${asset} must return 200`);
}

console.log(JSON.stringify({
  origin,showcase,securityHeaders:true,unauthenticatedFailClosed:true,
  adminDeepLinks:adminPaths.length,
  concurrency:{requests:probes.length,failures:0,totalMs:+loadElapsedMs.toFixed(1),p95Ms:+p95.toFixed(1)},
  githubPages:{status:publicPage.response.status,assets:2}
},null,2));
