"""Lifecycle for native Gunicorn Superset processes; no Docker daemon is used."""
import json
import os
from pathlib import Path
import secrets
import signal
import subprocess
import sys
import time
from privacy_assets import prepare_privacy_assets

root = Path(os.environ.get("AUREVIA_SUPERSET_NATIVE_ROOT",
                           str(Path.home() / ".local/share/aurevia-superset-demo"))).resolve()
source = Path(__file__).resolve().parent
python = root / "venv/bin/python"

def environment(zone):
    return {**os.environ, "AUREVIA_SUPERSET_NATIVE_ROOT": str(root), "AUREVIA_SUPERSET_ZONE": zone,
            "SUPERSET_CONFIG_PATH": str(source / "superset_config.py"), "FLASK_APP": "superset"}

def checked(zone, *arguments):
    with open(root / zone / "setup.log", "ab", buffering=0) as log:
        subprocess.run([str(python), *arguments], env=environment(zone), check=True,
                       stdout=log, stderr=log)
    print(f"{zone}: {' '.join([Path(arguments[0]).name, *arguments[1:]])} completed", flush=True)

def running(pid):
    try:
        os.kill(pid, 0)
        command = Path(f"/proc/{pid}/cmdline").read_bytes()
        return str(root / "venv").encode() in command and b"gunicorn" in command
    except (ProcessLookupError, FileNotFoundError):
        return False

command = sys.argv[1] if len(sys.argv) > 1 else "status"
root.mkdir(parents=True, exist_ok=True)
root.chmod(0o700)
if command == "init":
    settings_file = root / "settings.json"
    if not settings_file.exists():
        settings_file.write_text(json.dumps({zone: {"secretKey": secrets.token_urlsafe(64),
            "adminPassword": secrets.token_urlsafe(32)} for zone in ("public", "operation")}
            | {"ingressSecret": secrets.token_urlsafe(48)}, indent=2) + "\n")
        settings_file.chmod(0o600)
    prepare_privacy_assets(root)
    for zone in ("public", "operation"):
        (root / zone).mkdir(exist_ok=True)
        checked(zone, str(root / "venv/bin/superset"), "db", "upgrade")
        checked(zone, str(source / "seed.py"), "admin")
        checked(zone, str(root / "venv/bin/superset"), "init")
    checked("operation", str(source / "seed.py"), "reports")
elif command == "start":
    prepare_privacy_assets(root)
    for zone, port in (("public", 19089), ("operation", 19088)):
        pidfile = root / zone / "gunicorn.pid"
        if pidfile.exists() and running(int(pidfile.read_text())):
            print(f"{zone}: already running")
            continue
        log = open(root / zone / "server.log", "ab", buffering=0)
        process = subprocess.Popen([str(root / "venv/bin/gunicorn"), "--bind", f"127.0.0.1:{port}",
            "--workers", "1", "--threads", "4", "--timeout", "90", "--pid", str(pidfile),
            "superset.app:create_app()"], env=environment(zone), stdout=log, stderr=log,
            start_new_session=True)
        log.close()
        print(f"{zone}: started process {process.pid} on loopback {port}")
elif command == "stop":
    for zone in ("public", "operation"):
        pidfile = root / zone / "gunicorn.pid"
        if pidfile.exists():
            pid = int(pidfile.read_text())
            if running(pid):
                os.kill(pid, signal.SIGTERM)
                for _ in range(50):
                    if not running(pid): break
                    time.sleep(0.1)
            print(f"{zone}: stopped")
elif command == "status":
    for zone in ("public", "operation"):
        pidfile = root / zone / "gunicorn.pid"
        active = pidfile.exists() and running(int(pidfile.read_text()))
        print(f"{zone}: {'running' if active else 'stopped'}")
else:
    raise SystemExit("Usage: manage.py init|start|stop|status")
