"""Optional verified wheel cache for slow networks; versions follow Superset's lock."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

root = Path(os.environ.get("AUREVIA_SUPERSET_NATIVE_ROOT", str(Path.home() / ".local/share/aurevia-superset-demo")))
# uv already cached this pure-Python dependency during resolution/build isolation.
for candidate in (Path.home() / ".cache/uv/archive-v0").glob("*/packaging/tags.py"):
    sys.path.insert(0, str(candidate.parents[1]))
    break
from packaging.tags import sys_tags
from packaging.utils import parse_wheel_filename

def download(url, bounds=None):
    arguments = ["curl", "-4", "--fail", "--silent", "--show-error", "--retry", "2", "--max-time", "120"]
    if bounds:
        arguments += ["--range", f"{bounds[0]}-{bounds[1]}"]
    return subprocess.run(arguments + [url], check=True, capture_output=True).stdout

lock = download("https://raw.githubusercontent.com/apache/superset/5.0.0/requirements/base.txt").decode()
pins = {name.lower().replace("_", "-"): version for name, version in re.findall(r"^([\w.-]+)==([^\s;]+)", lock, re.M)}
pins["apache-superset"] = "5.0.0"
compatible = set(sys_tags())
destination = root / "wheels"
destination.mkdir(parents=True, exist_ok=True)
packages = ["apache-superset", "pyarrow", "numpy", "pandas", "babel", "selenium", "cryptography", "apsw", "zstandard", "brotli"]

def cache(name):
    version = pins[name]
    release = json.loads(download(f"https://pypi.org/pypi/{name}/{version}/json"))
    wheels = [file for file in release["urls"] if file["filename"].endswith(".whl")
              and compatible.intersection(parse_wheel_filename(file["filename"])[3])]
    if not wheels:
        raise RuntimeError(f"No compatible official wheel for {name}=={version}")
    wheel = min(wheels, key=lambda file: file["size"])
    path = destination / wheel["filename"]
    if path.exists() and hashlib.sha256(path.read_bytes()).hexdigest() == wheel["digests"]["sha256"]:
        return
    size = wheel["size"]
    segments = [(start, min(start + 1024 * 1024, size) - 1) for start in range(0, size, 1024 * 1024)]
    def segment(bounds):
        value = download(wheel["url"], bounds)
        if len(value) != bounds[1] - bounds[0] + 1:
            raise RuntimeError("Wheel range response had an unexpected size")
        return value
    with ThreadPoolExecutor(max_workers=8) as pool:
        value = b"".join(pool.map(segment, segments))
    if hashlib.sha256(value).hexdigest() != wheel["digests"]["sha256"]:
        raise RuntimeError("Official wheel checksum mismatch")
    path.write_bytes(value)
    print(f"Verified native cache: {name}=={version}", flush=True)

with ThreadPoolExecutor(max_workers=3) as pool:
    list(pool.map(cache, packages))
