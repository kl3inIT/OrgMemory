# Public Docs Deployment

This runbook publishes the independent Fumadocs portal without recreating or
restarting the OrgMemory product services. The repository contracts can be
prepared and the image can be published before DNS is delegated. Do not run
the live deployment until DNS and Nginx Proxy Manager access are confirmed.

## Release Boundary

- Image: `ghcr.io/kl3init/orgmemory-docs:sha-<full-main-commit>`
- Compose project: `orgmemory-docs`
- Service: `orgmemory-docs`
- Container port: `3000`; no host port is published
- Shared edge network: existing external `proxy-network`
- Host environment: `/apps/orgmemory/.env.docs.production`, mode `0600`
- Runtime state: `/apps/orgmemory-runtime/docs`
- Public origin: `https://docs.om.kl3in.tech`

The docs Compose file contains no API, worker, MCP, web, Keycloak, database, or
storage service. Its deployment uses a separate lock and changes only the docs
service.

## Verified Read-Only Preflight

On 2026-07-29 the ZM host was inspected without changing runtime state:

- `/apps/orgmemory` exists;
- `proxy-network` is an external local bridge and the running Nginx Proxy
  Manager container is attached;
- Docker `29.5.1` and Compose `5.1.3` are available;
- approximately 10 GiB memory and 100 GiB disk were available;
- the existing product environment has mode `0600`;
- the GitHub `production` environment exposes all five required SSH secret
  names;
- the host intentionally retains no persistent GHCR credential; the deployment
  workflow logs in for one exact pull and logs out afterward;
- `docs.om.kl3in.tech` did not resolve yet.

DNS control and Nginx Proxy Manager configuration access were not proven.
That is the current stop condition, not permission to guess or mutate them.

## Publish An Immutable Image

After the release commit is on `main` and its `CI` workflow is green, run
`Build docs image` with the full 40-character commit SHA. The workflow:

1. proves the commit belongs to `main` and has a successful `CI` run;
2. builds the standalone non-root image from the exact commit;
3. publishes the immutable SHA tag with OCI revision metadata, SBOM, and
   provenance;
4. applies the repository's non-blocking high/critical Trivy scan policy;
5. uploads the image reference and digest as a release manifest.

Do not replace or reuse an existing SHA tag. GHCR cleanup must always retain at
least the two newest verified docs images.

## One-Time Host Preparation

Run only after the owner authorizes the live deployment:

```bash
cd /apps/orgmemory
cp infrastructure/deployment/docs.env.example .env.docs.production
chmod 0600 .env.docs.production
install -d -m 0700 /apps/orgmemory-runtime/docs
```

Keep the environment file host-managed and out of Git. Its image reference is
updated automatically by `deploy-docs.sh`; the proxy network and public URL
remain operator-controlled values.

## DNS And Nginx Proxy Manager

Create the DNS record for `docs.om.kl3in.tech` toward the existing ZM ingress.
Then create one Nginx Proxy Manager host:

- scheme: `http`;
- forward host: `orgmemory-docs`;
- forward port: `3000`;
- WebSocket support: disabled;
- Block common exploits: enabled;
- certificate: Let's Encrypt for `docs.om.kl3in.tech`;
- Force SSL and HTTP/2: enabled after certificate issuance.

The advanced configuration should deny framing, avoid MIME sniffing, and keep
documents revalidatable while allowing immutable Next.js assets to be cached:

```nginx
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;

location /_next/static/ {
    proxy_pass http://orgmemory-docs:3000;
    proxy_set_header Host $host;
    add_header Cache-Control "public, max-age=31536000, immutable" always;
}
```

Leave application documents revalidatable. Enable HSTS only after HTTPS and
deep-link verification pass, so an incorrect certificate or proxy route does
not become sticky.

## Deploy And Roll Back

Run `Deploy docs` manually with the same full commit SHA and explicitly enable
`confirm_deploy`. The workflow requires successful `CI` and `Build docs image`
runs, verifies the SSH host against the managed known-hosts value, checks out
the exact commit on the server, logs in to GHCR temporarily, and invokes:

```bash
./infrastructure/deployment/scripts/deploy-docs.sh <full-commit-sha>
```

The script records the previous image, retains five release snapshots, waits no
more than 60 seconds for health, and runs internal and public smoke checks. Any
failed canary restores the last verified environment, starts only
`orgmemory-docs`, and reruns smoke checks. It never runs the product Compose
file.

To roll back intentionally, run `Deploy docs` with an older green `main` commit
whose docs image is still retained.

## Public Verification

After DNS and TLS are healthy:

```bash
python3 infrastructure/deployment/scripts/verify-docs-publication.py \
  https://docs.om.kl3in.tech
```

The verifier compares the sitemap with both committed public manifests, fetches
all 24 allowlisted routes plus the home, robots, sitemap, and LLM outputs, and
rejects internal evidence paths, private hosts, workspace paths, and
secret-shaped assignments.

Also run the browser suite against the released source before recording the
deployed revision. Confirm mobile navigation manually at a narrow viewport.
Record the workflow run, image digest, public TLS result, 24-route crawl,
container health, product health before/after, and rollback evidence in the
increment verification document.
