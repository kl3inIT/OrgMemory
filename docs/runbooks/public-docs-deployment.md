# Public Docs Deployment

This runbook publishes the independent Fumadocs portal without recreating or
restarting the OrgMemory product services. The current DNS, TLS, and Nginx Proxy
Manager route are live. For a new environment, prepare and publish the image
first, then require verified DNS and proxy access before its initial deployment.

## Release Boundary

- Image: `ghcr.io/kl3init/orgmemory-docs:sha-<full-main-commit>`
- Compose project: `orgmemory-docs`
- Service: `orgmemory-docs`
- Container port: `3000`; no host port is published
- Shared edge network: existing external `proxy-network`
- Host environment: `/apps/orgmemory/.env.docs.production`, mode `0600`
- Runtime state: `/apps/orgmemory-runtime/docs`
- Public origin: `https://docs.kl3in.tech`

The docs Compose file contains no API, worker, MCP, web, Keycloak, database, or
storage service. Its deployment uses a separate lock and changes only the docs
service.

## Verified Host And Edge State

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
  workflow uses a run-scoped Docker config under `/tmp` and removes it with a
  remote-shell exit trap after the exact pull/deployment transaction;
- GitHub-hosted product deployment run `30399267433` timed out before SSH
  authentication and skipped its deploy step; later run `30400831397`
  connected and completed, so runner reachability is intermittent rather than
  absent;
- `docs.kl3in.tech` resolves publicly to the ZM ingress address;
- DNS, Let's Encrypt TLS, and the Nginx Proxy Manager route publish the healthy
  isolated docs service.

If SSH access from a GitHub-hosted runner times out, do not bypass host
verification or copy registry credentials onto the server. Restore the approved
runner-to-host path or move the job to an approved self-hosted/VPN-connected
runner.

## Publish An Immutable Image

After a public-docs change reaches `main`, a successful `CI` run triggers
`Build docs image` automatically. The workflow confirms that the named
`Public docs · Node 24` job succeeded before it publishes anything. A green CI
run in which that job was skipped becomes a docs release no-op.

The workflow:

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

Create the DNS record for `docs.kl3in.tech` toward the existing ZM ingress.
Then create one Nginx Proxy Manager host:

- scheme: `http`;
- forward host: `orgmemory-docs`;
- forward port: `3000`;
- WebSocket support: disabled;
- Block common exploits: enabled;
- certificate: Let's Encrypt for `docs.kl3in.tech`;
- Force SSL and HTTP/2: enabled after certificate issuance.

Leave the Nginx Proxy Manager advanced configuration empty. The application
owns `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, and
`Permissions-Policy` for every route, while Next.js owns immutable caching for
its fingerprinted `/_next/static/` assets. Defining a second raw `location`
would bypass Nginx Proxy Manager's generated proxy include, and a nested
`add_header` would also stop inheriting server-level headers.

Enable HSTS at the proxy only after HTTPS and deep-link verification pass, so
an incorrect certificate or proxy route does not become sticky.

## Automatic Deploy, Redeploy, And Roll Back

A successful automatic `Build docs image` run triggers `Deploy docs`
automatically. A manual image build never triggers deployment. The deployment
proceeds only when the triggering run contains a successful
`Publish immutable docs image` job. An older build becomes a no-op when a newer
descendant docs image has already been published; a later non-docs commit does
not suppress the last verified docs change.

The workflow verifies the successful `CI` and image-build evidence, downloads
the matching release artifact, checks its commit, immutable image reference,
and digest, verifies the SSH host against the managed known-hosts value, checks
out the exact commit on the server, uses an ephemeral GHCR Docker config, and
invokes:

```bash
./infrastructure/deployment/scripts/deploy-docs.sh <full-commit-sha>
```

The script records the previous image, retains five release snapshots, waits no
more than 60 seconds for health, and runs internal and public smoke checks. Any
failed canary restores the last verified environment, starts only
`orgmemory-docs`, and reruns smoke checks. It never runs the product Compose
file.

For an intentional redeploy or rollback, run `Deploy docs` manually with a full
green `main` commit whose successful docs image is still retained, then enable
`confirm_deploy`.

## Public Verification

After DNS and TLS are healthy:

```bash
python3 infrastructure/deployment/scripts/verify-docs-publication.py \
  https://docs.kl3in.tech
```

The verifier compares the sitemap with both committed public manifests, fetches
all 24 allowlisted routes plus the root redirect, robots, sitemap, and LLM
outputs, and rejects internal evidence paths, private hosts, workspace paths,
and secret-shaped assignments.

Also run the browser suite against the released source before recording the
deployed revision. Confirm mobile navigation manually at a narrow viewport.
Record the workflow run, image digest, public TLS result, 24-route crawl,
container health, product health before/after, and rollback evidence in the
increment verification document.
