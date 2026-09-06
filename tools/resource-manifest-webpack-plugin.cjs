const fs=require('fs');
const path=require('path');

/** Emits a reviewable JSON contract next to remoteEntry.js without executing Webpack remotely. */
class ResourceManifestWebpackPlugin {
  constructor(sourceFile){this.sourceFile=sourceFile}
  apply(compiler){
    compiler.hooks.thisCompilation.tap('ResourceManifestWebpackPlugin',compilation=>{
      compilation.hooks.processAssets.tap(
        {name:'ResourceManifestWebpackPlugin',stage:compiler.webpack.Compilation.PROCESS_ASSETS_STAGE_ADDITIONAL},
        ()=>{
          const file=path.resolve(compiler.context,this.sourceFile);
          const content=fs.readFileSync(file,'utf8');
          const parsed=JSON.parse(content);
          if(parsed.schemaVersion!=='1.0'||!parsed.module||!Array.isArray(parsed.resources)
              ||!Array.isArray(parsed.routes)||!Array.isArray(parsed.navigation)){
            throw new Error(`Invalid resource manifest contract: ${file}`);
          }
          compilation.emitAsset('resource-manifest.json',
            new compiler.webpack.sources.RawSource(`${JSON.stringify(parsed,null,2)}\n`));
        });
    });
  }
}

module.exports={ResourceManifestWebpackPlugin};
