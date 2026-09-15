import { existsSync } from 'node:fs';
import { spawnSync } from 'node:child_process';

const artifacts=['admin','hr','finance','reports'].map(name=>`apps/mfe-${name}/dist`);
const errors=artifacts.filter(path=>!existsSync(path))
  .map(path=>`${path} is missing; run npm run build --workspace=@aurevia/${path.split('/')[1]}`);
const docker=spawnSync('docker',['version','--format','{{.Server.Version}}'],{
  encoding:'utf8',shell:process.platform==='win32',
});
if(docker.error||docker.status!==0)errors.push('Docker Engine is not running or is inaccessible');
if(errors.length){
  console.error('\nDemo MFE preflight failed:');
  errors.forEach(error=>console.error(` - ${error}`));
  process.exit(1);
}
console.log('Demo MFE preflight passed: all four build outputs and Docker are available.');
