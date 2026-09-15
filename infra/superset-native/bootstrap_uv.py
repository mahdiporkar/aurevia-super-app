"""Install uv from its official PyPI wheel without changing system Python."""
import hashlib
import io
import json
from pathlib import Path
import sys
import subprocess
from concurrent.futures import ThreadPoolExecutor
import zipfile

destination = Path(sys.argv[1]).resolve()
destination.mkdir(parents=True, exist_ok=True)
version = "0.8.15"
def download(url, byte_range=None):
    arguments = ["curl", "-4", "--fail", "--silent", "--show-error", "--max-time", "120"]
    if byte_range:
        arguments += ["--range", byte_range]
    return subprocess.run(arguments + [url],
                          check=True, capture_output=True).stdout

release = json.loads(download(f"https://pypi.org/pypi/uv/{version}/json"))
wheel = next(file for file in release["urls"] if file["filename"].endswith(
    "manylinux_2_17_x86_64.manylinux2014_x86_64.whl"))
size = wheel["size"]
chunk = 1024 * 1024
segments = [(start, min(start + chunk, size) - 1) for start in range(0, size, chunk)]
def segment(bounds):
    start, end = bounds
    result = download(wheel["url"], f"{start}-{end}")
    if len(result) != end - start + 1:
        raise RuntimeError("uv wheel range response had an unexpected size")
    return result
print(f"Downloading verified uv {version} ({size} bytes)", flush=True)
with ThreadPoolExecutor(max_workers=6) as pool:
    content = b"".join(pool.map(segment, segments))
if hashlib.sha256(content).hexdigest() != wheel["digests"]["sha256"]:
    raise RuntimeError("Official uv wheel checksum mismatch")
with zipfile.ZipFile(io.BytesIO(content)) as archive:
    member = next(name for name in archive.namelist() if name.endswith("/uv")
                  and ".data/scripts/" in name)
    executable = destination / "uv"
    executable.write_bytes(archive.read(member))
    executable.chmod(0o755)
print(f"Installed verified uv {version}")
