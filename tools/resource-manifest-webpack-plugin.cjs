const fs=require('fs');
const path=require('path');

function localRouteShape(value,file,key){
  if(typeof value!=='string'||value.startsWith('/')||(value&&value.endsWith('/'))
      ||value.includes('//')||value.includes('..')||value.includes('://')
      ||value.includes('\\')||value.includes('?')||value.includes('#')||value.includes('%'))
    throw new Error(`Invalid local MF route ${key}: ${file}`);
  if(!value)return '';
  return value.split('/').map((segment,index,segments)=>{
    if(segment==='*'){
      if(index!==segments.length-1)throw new Error(`MF wildcard must be final for ${key}: ${file}`);
      return '*';
    }
    if(segment.startsWith(':')){
      if(!/^:[A-Za-z][A-Za-z0-9_]{0,63}$/.test(segment))
        throw new Error(`Invalid MF route parameter for ${key}: ${file}`);
      return ':';
    }
    if(!/^[A-Za-z0-9._~-]+$/.test(segment))
      throw new Error(`Invalid MF route segment for ${key}: ${file}`);
    return segment.toLowerCase();
  }).join('/');
}

/** Emits reviewable manifest contracts next to remoteEntry.js. */
class JsonManifestWebpackPlugin {
  constructor(sourceFile,assetName,validate){this.sourceFile=sourceFile;this.assetName=assetName;this.validate=validate}
  apply(compiler){
    compiler.hooks.thisCompilation.tap(this.constructor.name,compilation=>{
      compilation.hooks.processAssets.tap(
        {name:this.constructor.name,stage:compiler.webpack.Compilation.PROCESS_ASSETS_STAGE_ADDITIONAL},
        ()=>{
          const file=path.resolve(compiler.context,this.sourceFile);
          const content=fs.readFileSync(file,'utf8');
          const parsed=JSON.parse(content);
          this.validate(parsed,file);
          compilation.emitAsset(this.assetName,
            new compiler.webpack.sources.RawSource(`${JSON.stringify(parsed,null,2)}\n`));
        });
    });
  }
}

class ResourceManifestWebpackPlugin extends JsonManifestWebpackPlugin {
  constructor(sourceFile){super(sourceFile,'resource-manifest.json',(parsed,file)=>{
    const forbidden=['routes','navigation','menus','runtime','remoteEntry','remoteEntryUrl',
      'exposedModule','routePrefix','slug','component','components','icon','iconKey'];
    if(parsed.schemaVersion!=='1.0'||!parsed.module||!Array.isArray(parsed.resources)
        ||forbidden.some(field=>Object.hasOwn(parsed,field))){
      throw new Error(`Invalid authorization-only resource manifest contract: ${file}`);
    }
    const resourceForbidden=['route','path','menu','navigation','icon','component','remoteEntry',
      'remoteEntryUrl','exposedModule','routePrefix','slug','label','iconKey'];
    if(parsed.resources.some(resource=>resourceForbidden.some(field=>
      Object.hasOwn(resource,field)||Object.hasOwn(resource.metadata??{},field)))){
      throw new Error(`Resource definition contains frontend metadata: ${file}`);
    }
  })}
}

class MicroFrontendManifestWebpackPlugin extends JsonManifestWebpackPlugin {
  constructor(sourceFile){super(sourceFile,'mf-manifest.json',(parsed,file)=>{
    if(parsed.schemaVersion!=='1.0'||!parsed.microfrontend||!parsed.runtime
        ||!Array.isArray(parsed.routes)||!Array.isArray(parsed.navigation)
        ||Object.hasOwn(parsed,'resources')){
      throw new Error(`Invalid MF manifest contract: ${file}`);
    }
    const routeKeys=new Set();
    const routeShapes=new Set();
    for(const route of parsed.routes){
      const shape=localRouteShape(route.path,file,route.key);
      if(!/^[a-z][a-z0-9._-]{1,99}$/.test(route.key??'')||route.component
          ||!route.requiredResource||routeKeys.has(route.key)||routeShapes.has(shape))
        throw new Error(`Invalid or conflicting local MF route: ${file}`);
      routeKeys.add(route.key);routeShapes.add(shape);
    }
    if(parsed.defaultRouteKey&&!routeKeys.has(parsed.defaultRouteKey))
      throw new Error(`MF default route is unknown: ${file}`);
    const navigationKeys=new Set();
    for(const node of parsed.navigation){
      if(!node.key||navigationKeys.has(node.key)||node.requiredResource||node.requiredAction
          ||(node.type==='PAGE'&&!routeKeys.has(node.routeKey)))
        throw new Error(`Invalid MF navigation: ${file}`);
      navigationKeys.add(node.key);
    }
  })}
}

module.exports={ResourceManifestWebpackPlugin,MicroFrontendManifestWebpackPlugin};
