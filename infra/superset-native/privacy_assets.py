"""Disable Superset 5's unconditional Scarf pixel in this native demo only."""
import hashlib
from importlib.metadata import distribution
import json
from pathlib import Path


def prepare_privacy_assets(root):
    package = distribution("apache-superset")
    if package.version != "5.0.0":
        raise RuntimeError("Native privacy override requires the pinned Superset 5.0.0")
    assets = Path(package.locate_file("superset/static/assets"))
    needle = 'https://apachesuperset.gateway.scarf.sh/pixel/'
    original = ('const P=({version:e="unknownVersion",sha:a="unknownSHA",build:t="unknownBuild"})=>'
                '{const n=`https://apachesuperset.gateway.scarf.sh/pixel/'
                '0d3461e1-abb1-4691-a0aa-5ed50de66af0/${e}/${a}/${t}`;'
                'return(0,U.Y)("img",{referrerPolicy:"no-referrer-when-downgrade",'
                'src:n,width:0,height:0,alt:""})}')
    candidates = [path for path in assets.glob("*.js") if needle.encode() in path.read_bytes()]
    if len(candidates) != 1:
        raise RuntimeError("Unexpected Superset Scarf asset layout; review the privacy override")
    asset = candidates[0]
    source = asset.read_text()
    if source.count(original) != 1:
        raise RuntimeError("Unexpected Superset Scarf component; review the privacy override")
    sanitized = source.replace(original, 'const P=()=>null').encode()
    destination = root / "privacy-assets"
    destination.mkdir(exist_ok=True)
    (destination / asset.name).write_bytes(sanitized)
    (destination / "manifest.json").write_text(json.dumps({
        "supersetVersion": package.version, "filename": asset.name,
        "sourceSha256": hashlib.sha256(source.encode()).hexdigest(),
        "servedSha256": hashlib.sha256(sanitized).hexdigest(),
        "scarfPixelDisabled": True,
    }, indent=2) + "\n")
