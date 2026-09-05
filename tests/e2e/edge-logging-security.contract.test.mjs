import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const nginx=readFileSync(new URL('../../infra/nginx/nginx.conf',import.meta.url),'utf8');

test('edge access logs exclude OIDC codes and every query string',()=>{
  assert.match(nginx,/log_format\s+aurevia_safe\s+escape=json/);
  assert.match(nginx,/"uri":"\$uri"/);
  assert.doesNotMatch(nginx,/\$request_uri\b|\$args\b|\$query_string\b/);
  assert.doesNotMatch(nginx,/\$request(?:\s|"|')/);
  assert.match(nginx,/access_log\s+\/var\/log\/nginx\/access\.log\s+aurevia_safe;/);
});
