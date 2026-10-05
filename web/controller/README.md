# Internal Data Node controller source

The controller source exposes creation, start, stop, certificate and credential
operations for its own UUID-named environments. A fixed project prefix isolates
Compose services and named volumes. Stop preserves volumes and dataset metadata.
Operations for an environment serialize, persist their state before dispatch and
report interruption after a controller restart.

Environment files contain a copied, checksum-verified upstream Data Node tree.
The generated Compose file extends its Blaze, TORCH, file-server and proxy
services. Overrides assign loopback ports, bounded resources, isolated mounts
and the local data-plane authentication used by the integration probe. The
controller records the Compose checksum and rejects external modifications
before invoking Docker. It never accepts a command, container name, image name
or host path from an HTTP request.

This source is not wired into the workbench Compose project or public API.
Its tests mock Docker operations; live lifecycle validation remains outstanding.

## Deployment permission boundary

Deploying the controller requires explicit approval for access to the Docker
socket. That socket grants broad Docker and host control regardless of the
application's action allowlist. Automatic approval review rejected introducing
that persistent access without separate user authorization. Do not activate a
socket mount, substitute an indirect Docker-access mechanism or run the live
controller until authorization is received.

The intended deployment keeps Docker access in this internal component. The
web API and generation worker continue without the Docker socket. Environment
configuration and credentials use a dedicated persistent directory; workbench
results are mounted read-only. Certificate/private-key files and credentials
are never repository content.

## Tests

With the workbench test dependencies installed:

```sh
python3 -m unittest discover -s web/controller -p 'test_*.py'
```

The checks cover owned project names, loopback ports, host-path translation,
canonical environment IDs, allowed operations, configuration checksums,
serialized dispatch, preserved data on stop, restart recovery and public
metadata filtering.
