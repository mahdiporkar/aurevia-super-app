import {admin,show,state,save} from './lib.mjs';
const s=await admin(); const st=state(); const P=st.panelId;
show('MF sync (after publish)',await s.json(`/api/v1/admin/panels/${P}/frontend-manifests/sync`,'POST'));
const arts=await s.json(`/api/v1/admin/panels/${P}/artifacts`);
console.log('artifacts:',JSON.stringify(arts.body?.map(a=>({id:a.id,v:a.artifact_version,active:a.active,val:a.validation_status,rm:a.resource_manifest_id})),null,1));
const panels=await s.json('/api/v1/admin/panels');
const p=panels.body.find(x=>x.id===P);
console.log('panel active_artifact_id',p.active_artifact_id,'version',p.version,'semver',p.semantic_version);
save({...st,v1ArtifactId:arts.body?.[0]?.id,panelVersion:p.version});
