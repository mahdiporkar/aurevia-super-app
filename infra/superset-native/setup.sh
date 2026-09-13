#!/usr/bin/env bash
set -euo pipefail
export UV_NATIVE_TLS=true
export UV_HTTP_TIMEOUT=600
demo_root="${AUREVIA_SUPERSET_NATIVE_ROOT:-$HOME/.local/share/aurevia-superset-demo}"
source_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
for required_tool in python3 curl c++; do
  command -v "$required_tool" >/dev/null || {
    echo "Missing native prerequisite: $required_tool. On Ubuntu install python3 curl g++." >&2
    exit 1
  }
done
mkdir -p "$demo_root/tools"
chmod 700 "$demo_root"
if [[ ! -x "$demo_root/tools/uv" ]]; then
  python3 "$source_root/bootstrap_uv.py" "$demo_root/tools"
fi
"$demo_root/tools/uv" python install 3.11
if [[ ! -x "$demo_root/venv/bin/python" ]]; then
  "$demo_root/tools/uv" venv --python 3.11 "$demo_root/venv"
fi
mkdir -p "$demo_root/wheels"
if [[ "${AUREVIA_SUPERSET_RANGE_DOWNLOAD:-0}" == "1" ]]; then
  "$demo_root/tools/uv" pip install --python "$demo_root/venv/bin/python" \
    --constraint https://raw.githubusercontent.com/apache/superset/5.0.0/requirements/base.txt packaging
  "$demo_root/venv/bin/python" "$source_root/download_wheels.py"
fi
wheel_requirements=()
for cached_wheel in "$demo_root/wheels/"*.whl; do
  [[ -f "$cached_wheel" ]] && wheel_requirements+=("$cached_wheel")
done
"$demo_root/tools/uv" pip install --python "$demo_root/venv/bin/python" \
  --find-links "$demo_root/wheels" \
  --constraint https://raw.githubusercontent.com/apache/superset/5.0.0/requirements/base.txt \
  apache-superset==5.0.0 'marshmallow<4' 'setuptools<81' "${wheel_requirements[@]}"
"$demo_root/venv/bin/python" "$source_root/manage.py" init
