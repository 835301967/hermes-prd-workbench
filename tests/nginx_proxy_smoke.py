"""Exercise the shipped proxy config with real, pinned Nginx and a synthetic upstream.

Requires Docker; uses an isolated temporary network and containers, no volumes,
database, model, credentials or existing project services.
"""
import json
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "deploy/nginx/default.conf"


def docker(*args):
    return subprocess.check_output(["docker", *args], text=True, stderr=subprocess.PIPE).strip()


def image(dockerfile, prefix):
    matches = list(dict.fromkeys(line.split()[1] for line in dockerfile.read_text().splitlines()
                                 if line.startswith("FROM " + prefix)))
    if len(matches) != 1 or "@sha256:" not in matches[0]:
        raise ValueError("A single digest-pinned test runtime is required")
    return matches[0]


def verify():
    suffix = uuid.uuid4().hex[:12]
    network = "hermes-prd-proxy-test-" + suffix
    upstream, proxy = network + "-upstream", network + "-web"
    created = []
    network_created = False
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        docker("network", "create", network)
        network_created = True
        docker("create", "--name", upstream, "--network", network,
               "--network-alias", "backend", "--user", "10002:10002",
               "--security-opt", "no-new-privileges:true", "--cap-drop", "ALL",
               "--mount", f"type=bind,src={ROOT / 'tests/fixtures/proxy-upstream.py'},dst=/fixture.py,readonly",
               image(ROOT / "deploy/docker/hermes.Dockerfile", "python:"), "python", "/fixture.py")
        created.append(upstream)
        docker("start", upstream)
        docker("create", "--name", proxy, "--network", network,
               "--publish", "127.0.0.1::8080", "--security-opt", "no-new-privileges:true",
               "--cap-drop", "ALL", "--mount",
               f"type=bind,src={CONFIG},dst=/etc/nginx/conf.d/default.conf,readonly",
               image(ROOT / "deploy/docker/web.Dockerfile", "nginxinc/"))
        created.append(proxy)
        docker("start", proxy)
        port = int(docker("port", proxy, "8080/tcp").rsplit(":", 1)[1])
        base = f"http://127.0.0.1:{port}"

        def request(host, origin=None):
            headers = {"Host": host}
            if origin is not None:
                headers["Origin"] = origin
            req = urllib.request.Request(base + "/api/v1/echo?synthetic=1", data=b"{}", headers=headers)
            with opener.open(req, timeout=3) as response:
                return json.load(response)

        for _ in range(30):
            try:
                request("127.0.0.1:" + str(port))
                break
            except (OSError, urllib.error.HTTPError):
                time.sleep(1)
        else:
            raise ValueError("Synthetic proxy did not become ready")

        for host in ["localhost:" + str(port), "127.0.0.1:" + str(port), "example.test:9080"]:
            for origin in ["http://" + host, "https://untrusted.example", "null", None]:
                payload = request(host, origin)
                assert payload == {"host": host, "origin": origin, "method": "POST",
                                   "path": "/api/v1/echo?synthetic=1"}, "Proxy changed request authority or origin"
        try:
            opener.open(base + "/internal/synthetic", timeout=3)
        except urllib.error.HTTPError as error:
            assert error.code == 404
        else:
            raise AssertionError("Internal route must not be exposed")
        print("Real Nginx: 12 authority/origin cases and internal-route isolation passed")
    finally:
        for name in reversed(created):
            subprocess.run(["docker", "rm", "-f", name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if network_created:
            subprocess.run(["docker", "network", "rm", network], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


if __name__ == "__main__":
    try:
        verify()
    except subprocess.CalledProcessError as error:
        raise SystemExit(f"Reverse-proxy test: Docker {error.cmd[1]} failed (exit {error.returncode})")
    except (ValueError, AssertionError) as error:
        raise SystemExit("Reverse-proxy test: " + str(error))
    except Exception as error:
        raise SystemExit("Reverse-proxy test failed: " + type(error).__name__)
