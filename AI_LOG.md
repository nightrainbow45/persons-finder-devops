# AI War Stories — Persons Finder DevOps

> Plain-language account of what AI helped with, where it screwed up, and how we fixed it.
> Every item in the checklist and every chapter below is a real mistake from this project. Not padding.

---

## Pre-Commit Sanity Checklist

### Container

- [ ] Pin image versions to patch level — no `:latest`, no floating minor tags (e.g. `gradle:7.6.4-jdk11-focal`, not `gradle:7.6-jdk11`)
- [ ] Multi-stage build: fat SDK to build, lean JRE/alpine to run
- [ ] Non-root user created and set with `USER` before `ENTRYPOINT`
- [ ] `.dockerignore` present and excludes `.git/`, `build/`, `src/test/`, Terraform state, secrets
- [ ] Alpine runtime stage runs `apk upgrade --no-cache` or OS-level CVEs pile up
- [ ] `HEALTHCHECK` points to `/actuator/health`

### Kubernetes

- [ ] HPA must use `autoscaling/v2`, not `v2beta2`
- [ ] Every container needs both `readinessProbe` and `livenessProbe`, including sidecars
- [ ] Both `resources.requests` and `resources.limits` are required
- [ ] Ingress needs `pathType: Prefix` or `Exact`
- [ ] Do not rely on SPOT for nodes running Kyverno or other admission webhooks — SPOT interruption takes the webhook down; with `failurePolicy: Fail` this bricks the entire cluster (cannot schedule any pod)
- [ ] t3.small hard cap is 11 pods per node (ENI limit) — DaemonSets (aws-node + kube-proxy + fluent-bit) consume 3 slots; actual usable slots ≈ 8 per node; plan accordingly
- [ ] Verify AWS account type before choosing instance class — Free Tier accounts cannot launch t3.medium or larger; only t3.small/t3.micro are eligible (launch will silently fail with InsufficientInstanceCapacity)
- [ ] System pods (cert-manager, external-secrets, coredns) land on whichever node has capacity — after topology changes they can pile onto one node; redistribute manually with pod deletion if needed
- [ ] `podSecurityContext`: `runAsNonRoot: true`, `allowPrivilegeEscalation: false`, `capabilities.drop: [ALL]`
- [ ] Sidecar is an outbound forward proxy — use `LISTEN_PORT` + `UPSTREAM_URL`, not `TARGET_HOST` + `TARGET_PORT`
- [ ] When sidecar is enabled, inject `LLM_PROXY_URL` into the main container

### NetworkPolicy

- [ ] Allow both `UDP 53` and `TCP 53` — TCP fallback exists for large DNS responses
- [ ] Same-namespace pod communication needs an explicit `podSelector: {}` egress rule
- [ ] Use `ipBlock` excluding RFC1918 ranges for external HTTPS — not open `0.0.0.0/0`
- [ ] VPC CNI addon needs `enableNetworkPolicy: "true"` or policies do nothing

### CI/CD

- [ ] Trivy needs `--exit-code 1` or CVEs silently pass without failing the build
- [ ] OIDC job needs `permissions: { id-token: write, contents: read }`
- [ ] OIDC trust policy needs `StringLike` and must cover both repos
- [ ] Pin tool versions — don't use `@master`
- [ ] Both `cosign sign` and `cosign attest` need `--yes` or CI hangs waiting for terminal input
- [ ] SBOM artifact upload needs `retention-days: 90` — default is instant delete
- [ ] Periodic re-scan must filter to `git-*` tags only — don't scan everything
- [ ] With ECR IMMUTABLE, no floating tags (`latest`, `X.Y`, `X`) — use `git-<sha>` + exact semver only
- [ ] AI-generated docs must be verified against the actual directory listing, real default values, and CLI commands — these three drift fastest

### Swagger / OpenAPI

- [ ] Kotlin KDoc must not contain `/**` as a substring (e.g. in path patterns) — treated as a nested comment opener, causes "Unclosed comment" compile error
- [ ] Swagger Basic Auth belongs on the Ingress (nginx annotation), not the app layer — no code change needed
- [ ] Setting a specific `CORS_ALLOWED_ORIGINS` auto-enables `allowCredentials`; wildcard `*` must then be removed — they cannot coexist

### Terraform

- [ ] S3 bucket and DynamoDB table must exist before `terraform init`
- [ ] `aws-auth` group mapping requires a matching `ClusterRole` + `ClusterRoleBinding` — identity without binding equals zero permissions
- [ ] Helm deployer ClusterRole needs `secrets` CRUD — Helm stores release state in Secrets
- [ ] Launch Template needs `http_put_response_hop_limit = 2` for pods to reach IMDS
- [ ] After `terraform destroy + apply`, KMS key changes — update Kyverno policy and CI ARN before deploying
- [ ] Do a full `destroy + apply` to test idempotency — incremental `apply` hides bugs

### Kyverno

- [ ] Configure IRSA before enabling Enforce mode — without it, ECR API takes ~11s, webhook timeout is 10s, instant admission failure
- [ ] Set `mutateDigest: false` for ECR IMMUTABLE tags — rewriting to digest is noise
- [ ] Same for `verifyDigest: false`
- [ ] Both main and sidecar images must be in `imageReferences` — one unguarded image is a bypass vector
- [ ] Document the Audit → Enforce prerequisite checklist in the policy file header

### Secrets & Supply Chain

- [ ] Each `.trivyignore` entry needs rationale, affected component, and a removal condition
- [ ] Use digest reference for `cosign sign`, not tag — avoids `MANIFEST_UNKNOWN` race during ECR propagation
- [ ] KMS ARN is not sensitive — put it in workflow env vars, not GitHub Secrets
- [ ] All secrets via `envFrom.secretRef` — never bake into image or Helm values

---

## The Stories

---

### 1. Dockerfile — Multi-Stage Build

**File:** `devops/docker/Dockerfile`

---

Asked AI for a Dockerfile, got something that works but had a few gremlins.

First, `COPY *.jar app.jar` blows up if Gradle outputs both a plain jar and a fat jar — two files get copied. Second, `.dockerignore` was placed in the wrong directory relative to the build context. Third, `RUN gradle dependencies || true` swallows real errors, so you'd never know if dependency resolution silently failed.

Fix: Spring Boot only produces one bootJar by default so `*.jar` is fine in practice — added a comment explaining why. Fixed `.dockerignore` path. Kept the `|| true` but added a comment saying "this is a Gradle caching pattern, not error suppression."

---

### 2. Helm Chart — Kubernetes Manifests

**Dir:** `devops/helm/persons-finder/`

---

Asked AI for a full Helm chart, got a dozen templates, five needed fixing.

1. HPA was on `autoscaling/v2beta2` — deprecated since K8s 1.26. Updated to `v2`.
2. NetworkPolicy egress was wide-open `0.0.0.0/0` — basically no policy. Switched to `ipBlock` excluding RFC1918 ranges.
3. Ingress was missing `pathType` — required field in `networking.k8s.io/v1`, would fail on apply.
4. Secret template double-encoded values — plain text from `values.yaml` got `b64enc`'d again. Fixed to encode once.
5. RBAC was giving namespace-wide `list`/`watch` on Secrets. Narrowed to `get` on specific named resources.

---

### 3. CI/CD — GitHub Actions Pipeline

**File:** `.github/workflows/ci-cd.yml`

---

Asked AI for a GitHub Actions pipeline. Looked right, had five bombs inside.

1. OIDC role ARN was a placeholder `arn:aws:iam::role/` — would crash immediately. Changed to read from `secrets.AWS_ROLE_ARN`.
2. Trivy had no `exit-code: '1'` — HIGH/CRITICAL CVEs were reported but never failed the build. Added it.
3. OIDC job was missing `permissions: { id-token: write, contents: read }` — auth would fail. Added it.
4. No Gradle wrapper validation — basic supply chain hygiene. Added `wrapper-validation-action`.
5. No Docker layer caching — rebuilding from scratch every run. Configured `type=gha` cache.

---

### 4. PII Redaction Service (Kotlin)

**Dir:** `src/main/kotlin/com/persons/finder/pii/`

---

Asked AI to write PII detection and redaction. Good structure, a few sharp edges.

1. Name regex `[A-Z][a-z]+ [A-Z][a-z]+` was too greedy — "Spring Boot", "New York" all triggered it. Added word-length requirements and common exclusions.
2. Coordinate regex matched plain integers — port numbers and IDs all flagged. Tightened to require decimal points and valid lat/lon ranges.
3. Tokens used 8-char UUID prefix — non-trivial collision risk at scale. Extended to full 36-char UUID.
4. Token map was stored as an instance field — concurrent requests would corrupt each other's mappings. Made it per-request.
5. Timestamps were raw `currentTimeMillis()` longs — annoying to read in logs. Switched to ISO-8601 strings.

---

### 5. Terraform — AWS Infrastructure

**Dir:** `devops/terraform/`

---

Asked AI for full Terraform (VPC/EKS/ECR/IAM/Secrets Manager). Good structure, five gotchas.

1. `backend.tf` requires an S3 bucket and DynamoDB table to already exist before `terraform init`. AI didn't mention this — I almost crashed on init.
2. Dev environment had two NAT Gateways — unnecessary cost. Reduced to one.
3. EKS node AMI not pinned — could break on AMI updates. Added `ami_type` parameter.
4. OIDC thumbprint was hardcoded — breaks when GitHub rotates TLS certs. Switched to dynamic fetch.
5. Secrets Manager rotation referenced a Lambda ARN that didn't exist. Made rotation optional via variable, default off.

---

### 6. Application API Endpoints

**Dir:** `src/main/kotlin/com/persons/finder/`

---

Asked AI for four REST endpoints and three-layer architecture. Runs, but a few issues.

1. Haversine used `acos()` — produces NaN at antipodal points. Switched to `atan2()` for numerical stability.
2. `Location` entity used `referenceId` as both FK and PK — no history possible. Intentional design (latest-location-only for now), added a comment documenting the trade-off.
3. PUT location had no lat/lon range validation — you could pass 9999. Added range checks.
4. Nearby search loaded all locations into memory then filtered — doesn't scale. Added a comment: needs PostGIS for production, acceptable at current scale.
5. GET returns empty list for missing IDs rather than distinguishing "not found" from "bad input" — kept as-is, standard REST behavior.

---

### 7. Spring Boot Actuator Config

**Files:** `build.gradle.kts`, `application.properties`

---

Asked AI to configure health checks. Usable output, but security and functional issues.

1. Used `exposure.include=*` — exposes env, beans, metrics, everything. Not for production. Changed to only expose `health`.
2. `show-details=always` leaks internal state (DB, disk) to anyone. Changed to `when-authorized` for prod.
3. Kubernetes probe paths (`/readiness`, `/liveness`) weren't enabled — K8s probes were configured but went nowhere. Added `probes.enabled=true`.
4. Missing API key threw an exception during bean init — health endpoint couldn't even start to report the failure. Changed to a `@PostConstruct` warning log.
5. Actuator base path wasn't explicit — nearly conflicted with API routes later. Set `/actuator` explicitly.

---

### 8. Go PII Redaction Sidecar

**File:** `sidecar/main.go`

---

Asked AI to write a Go version of the PII proxy matching the Kotlin logic. Right structure, five bugs.

1. Coordinate regex was missing the `-?` prefix and range bounds — missed negative coordinates and false-positive'd on version strings like `1.25`.
2. After replacing names with `<NAME_xxxx>` tokens, the coordinate regex would match the hex digits inside the token and corrupt it. Added a `<>` character check to skip already-tokenized content.
3. No `/health` endpoint — K8s probes got 404 forever, pod never became ready. Added a route returning `{"status":"ok"}`.
4. Audit logs used `log.Println` — Go's log package prepends a timestamp, breaking the JSON format Fluent Bit expected. Switched to `fmt.Fprintln(os.Stdout, ...)`.
5. `log.Println` also defaults to stderr — Fluent Bit reads stdout. All audit logs were silently dropped.

---

### 9. Kyverno Image Signature Enforcement

**File:** `devops/kyverno/verify-image-signatures.yaml`

---

Asked AI for a Kyverno policy. Basically right, but missed important things.

1. Only listed the main image — sidecar was unguarded, a bypass vector. Added `pii-redaction-sidecar:*` to `imageReferences`.
2. `mutateDigest: true` (default) — ECR uses IMMUTABLE tags, no need to rewrite to digest. It also causes constant ArgoCD diffs. Turned it off.
3. `verifyDigest: true` (default) — IMMUTABLE tags are trustworthy enough. Tag references are fine. Turned it off.
4. Without IRSA, ECR API takes ~11s. Webhook timeout is 10s. It just times out. Configure IRSA before enabling Enforce.
5. Generated file had zero prerequisite documentation — first deployment just exploded. Added a detailed header comment.

---

### 10. NetworkPolicy — Layer 3

**File:** `devops/helm/persons-finder/templates/networkpolicy.yaml`

---

Asked AI for a NetworkPolicy. Looked complete, four problems in practice.

1. No `podSelector: {}` egress rule — main app and sidecar couldn't talk to each other over localhost:8081 inside the same pod. Added it.
2. DNS was UDP-only — large DNS responses fall back to TCP 53, causing intermittent DNS failures. Added TCP 53 too.
3. Deployer ClusterRole was missing `networkpolicies` — enabling NetworkPolicy made every `helm upgrade` fail with RBAC forbidden. Added it.
4. VPC CNI addon didn't have `enableNetworkPolicy: "true"` — policies existed but were never enforced. Added the Terraform config.

---

### 11. Fluent Bit + CloudWatch — Layer 4

**Files:** `devops/k8s/fluent-bit.yaml`, `cloudwatch.tf`

---

Asked AI to deploy Fluent Bit shipping logs to CloudWatch. Got a full config, five things wrong.

1. SQLite state file path `/var/log/flb_pii_audit.db` — but `/var/log` was read-only mounted. Pod CrashLoopBackOff immediately. Moved to emptyDir at `/fluent-bit/state/`.
2. `auto_create_group true` needs `logs:CreateLogGroup` permission — IAM only had stream-level permissions. Kept hitting errors. Changed to false, log group pre-created by Terraform.
3. IAM was missing `logs:DescribeLogGroups` — Fluent Bit silently fails its health check on startup without it. Added it.
4. DaemonSet had no tolerations — wouldn't run on tainted nodes. Added a broad toleration block.
5. CloudWatch metric filter used single quotes `{ $.type = 'PII_AUDIT' }` — CloudWatch only accepts double quotes. Silently never matched. Fixed to `\"PII_AUDIT\"`.

---

### 12. Supply Chain Security: cosign + SBOM + Periodic Re-scan

**Files:** `ci-cd.yml`, `security-rescan.yml`, `.trivyignore`

---

Asked AI to add supply chain security: SBOM, cosign signing, periodic re-scan. Right framework, five detail bugs.

1. `cosign sign` ran immediately after `docker push` — ECR manifest hadn't propagated yet, got `MANIFEST_UNKNOWN`. Added a 5s sleep and switched to signing by digest reference.
2. `cosign sign` missing `--yes` — hangs waiting for terminal input in CI. Added it.
3. Periodic re-scan used `aws ecr list-images` with no filter — scanned hundreds of tags and hit IAM rate limits. Filtered to `git-*` prefix, latest 5 only.
4. `.trivyignore` had just CVE IDs with no context — rewrote each entry to include rationale, severity, affected component, and removal condition.
5. SBOM artifact had no retention setting — default is 0 days, deleted immediately on upload. Added `retention-days: 90`.

---

### 13. OIDC Trust Policy Repo Mismatch

**File:** `devops/terraform/modules/iam/trust-policies/github-oidc.json`

---

Classic gotcha: AI generated an OIDC trust policy scoped to the wrong repo. Every CI run got AccessDenied.

App code lives in `persons-finder`, but the CI/CD workflow runs from `persons-finder-devops`. The OIDC token carries the devops repo name. Policy only matched `persons-finder`. Mismatch. AccessDenied every time.

Also used `StringEquals` — which is exact-match only, no wildcards. Changed to `StringLike` and added both repo patterns.

---

### 14. t3.small Pod Cap & Rolling Update Deadlock

**File:** `devops/helm/persons-finder/templates/deployment.yaml`

---

Sneaky bug: AI gave default `maxSurge: 1`, which deadlocks on t3.small.

t3.small has an ENI limit of 11 pods per node. At baseline capacity (11 pods), `maxSurge: 1` tries to schedule a new pod before killing the old one. New pod stays Pending forever. Old pod never gets killed. Upgrade freezes.

Tried setting `ENABLE_PREFIX_DELEGATION=true` on the `aws-node` DaemonSet at runtime to increase capacity — VPC CNI restarted in a broken state, new pods couldn't get IPs, brief outage. This config change requires node replacement via Launch Template, can't be hot-applied.

Fixed at the time: `maxSurge: 0, maxUnavailable: 1` — kill old first, then start new. Brief unavailability window but the upgrade actually completes.

> **Update — current actual state (verified 2026-03-03):**
>
> Three nodes, two nodegroups — **all ON_DEMAND** after SPOT pool exhaustion incident (see Chapter 23):
>
> | Nodegroup | Count | Type | AZ | Pods | What actually runs there |
> |---|---|---|---|---|---|
> | `system-nodes-prod` | 1 | ON_DEMAND | ap-southeast-2b | 4/11 | ingress-nginx + DaemonSets (aws-node/kube-proxy/fluent-bit) |
> | `persons-finder-nodes-prod` | 2 | ON_DEMAND | ap-southeast-2a + 2b | 8/11 each | persons-finder (2/2 w/ sidecar) + kyverno + cert-manager + external-secrets + coredns |
>
> `maxSurge=0` still in effect — correct for 11-pod ENI constraint.
>
> **Residual risks (still unresolved):**
> 1. Kyverno and cert-manager have no `nodeSelector` — they land wherever capacity exists, currently split across app nodes. Still a risk if those nodes fill up.
> 2. Both app nodes are in different AZs now (ap-southeast-2a + 2b) — AZ risk is mitigated but only 1 replica running (HPA min=1). An app node failure → zero replicas until rescheduled.

---

### 15. Kyverno Webhook Timeout & IRSA

**Files:** `verify-image-signatures.yaml`, `prod/main.tf`

---

Enabling Kyverno image verification was a cascade of gotchas. Root cause: IRSA not configured.

Without IRSA, Kyverno needs to hit ECR to fetch the cosign manifest: EC2 IMDS → node IAM role → STS → ECR. On t3.small through a NAT Gateway this takes ~11 seconds. Webhook timeout is 10 seconds. Times out, admission error.

The real nasty bit: even in `Audit` mode, Kyverno's mutating webhook uses `failurePolicy: Fail`. A timeout on the mutating webhook gives you an unconditional admission rejection, regardless of `validationFailureAction`. Audit mode does not mean safe testing mode.

Also: Kyverno 1.17.1 rejects policies combining `validationFailureAction: Audit` with `mutateDigest: true`. Disabled both `mutateDigest` and `verifyDigest`.

After configuring IRSA, ECR calls dropped from 11s to 1s. Problem gone.

---

### 16. The RBAC Gap That Only Shows on Fresh Clusters

**File:** `devops/terraform/modules/eks/aws-auth.tf`

---

Classic "insufficient testing" bug — worked fine on the old cluster, blew up on a fresh one.

AI generated the `aws-auth` ConfigMap group mapping, tying the GitHub Actions IAM role to the `deployers` group. But didn't create a `ClusterRole` or `ClusterRoleBinding` for `deployers`. In Kubernetes, identity without a binding equals zero permissions.

Hadn't caught it before because the cluster was never fully destroyed and rebuilt — the old cluster had RBAC objects left over from earlier manual work. First complete `destroy + apply`, clean cluster, every `helm upgrade` failed with `secrets is forbidden`.

Helm stores release state in Kubernetes Secrets. Without `secrets` CRUD, `helm upgrade` can't even start.

Fixed by adding the ClusterRole (with secrets CRUD) and ClusterRoleBinding, plus `depends_on` so EKS is ready before Terraform tries to create RBAC objects.

---

### 17. KMS Key Changes After Destroy

**Files:** `verify-image-signatures.yaml`, `ci-cd.yml`, `setup-eks.sh`

---

Non-obvious but high-severity: rebuilding infrastructure locks down the entire cluster via Kyverno.

AWS KMS asymmetric keys don't support automatic rotation. Every `terraform destroy + apply` creates a brand-new key with a new ARN and a different public key.

Kyverno policy has the old public key embedded. CI signs with the new key. Kyverno verifies against the old key. Everything fails. In Enforce mode, zero pods can be created — including system pods. Cluster becomes a brick.

CI workflow had the ARN hardcoded — keeps using the old (now-deleted) key after rebuild. `cosign sign` throws KMS `NotFoundException`.

Added a step to `setup-eks.sh`: after every rebuild, automatically extract the new public key and update the policy. Added a big warning comment: "Update Kyverno policy BEFORE deploying or Enforce mode bricks the cluster." Added a `cosign_key_arn` Terraform output for easy copy-paste.

---

### 18. Sidecar Proxy Wired Backwards

**Files:** `values.yaml`, `deployment.yaml`

---

Easiest to understand, also kind of funny: AI wired the sidecar backwards.

AI gave `TARGET_HOST: localhost, TARGET_PORT: 8080` — the sidecar would forward requests to the main app's port 8080. That's a reverse proxy (external traffic → app), not what we wanted: a forward proxy (app → external LLM).

On top of that, even if the direction were right, the main app had no way to route LLM calls through the sidecar — `LLM_PROXY_URL` was never injected, so all traffic bypassed the sidecar entirely even when it was running.

Also no health probes on the sidecar — it could crash silently with the pod still showing healthy.

Fix: Changed env vars to `LISTEN_PORT: 8081, UPSTREAM_URL: https://api.openai.com` (sidecar listens on 8081 and forwards to OpenAI), injected `LLM_PROXY_URL=http://localhost:8081` into the main container, added health probes to the sidecar.

---

### 19. Swagger & CORS Integration Tests

**Files:** `config/OpenAPIConfig.kt`, `config/CorsConfig.kt`, `templates/ingress.yaml`, `templates/basic-auth-secret.yaml`

---

Asked AI to set up Swagger UI, CORS config, and integration tests. Three non-obvious bugs.

First: Kotlin compile error. Wrote `/api/v1/**` inside a KDoc comment. Kotlin parses `/**` as a nested comment opener — "Unclosed comment" compile error. Nothing to do with Swagger. Fixed by rewriting the path description to avoid the `/**` substring.

Second: CORS misconfiguration. `allowCredentials = true` and `allowedOrigins = "*"` are mutually exclusive — both browsers and Spring reject this at runtime. AI generated a config with both enabled. Fix: when `CORS_ALLOWED_ORIGINS=*` (dev default), don't enable `allowCredentials`; when set to a specific origin, automatically drop the wildcard and enable credentials.

Third: Swagger auth in the wrong layer. AI suggested Spring Security intercepting `/swagger-ui/**` at the app layer — requires code changes, new dependencies, image rebuild. Much cleaner to put it on the Ingress via nginx annotation: `auth-type: basic` + `basic-auth-secret.yaml`. Zero application code changes, zero redeploy needed.

jqwik property tests also had a gotcha: `options()` clashed with `MockMvcRequestBuilders.options()` — had to use the fully qualified class name to resolve the ambiguity.

---

### 20. ECR Tag Strategy: Floating Tags vs IMMUTABLE

**File:** `.github/workflows/ci-cd.yml`

---

AI suggested a "multi-dimensional tag strategy" that sounded reasonable but directly conflicted with ECR IMMUTABLE.

The `docker/metadata-action` config AI generated included `type=semver,pattern={{major}}.{{minor}}` (floating minor), `type=semver,pattern={{major}}` (floating major), and `type=raw,value=latest`. All three need to overwrite an existing tag on every subsequent release. ECR IMMUTABLE prohibits overwriting any tag. Second push fails with `ImageAlreadyExistsException`.

More importantly: Helm deploy never uses `latest`, `1.2`, or `1` for actual deployments — it uses exact `git-sha` or `X.Y.Z`. So these three tags add noise to the ECR image list and nothing else.

Conclusion: ECR IMMUTABLE + two tag types only:
- `git-<sha>`: generated for all events — unique, immutable, fully traceable
- `X.Y.Z`: generated only on version tag push — exact semver, usable for Helm rollback

Floating tags (`latest`, `X.Y`, `X`) only make sense with ECR MUTABLE mode. And MUTABLE is not a production best practice.

> **Verified state:** Main image tag is `git-b9893e1` ✅. However, `sidecar.image.tag` is still `latest` in the current Helm release values — the tag policy fix was applied to the CI and main image but the sidecar value was missed. Sidecar is currently `enabled: false`, so it's not active, but the value should be corrected before re-enabling.

---

### 21. Documentation Drift

**Files:** `devops/docs/DEPLOYMENT.md`, `devops/docs/QUICKSTART.md`, `devops/docs/README.md`

---

Asked AI to generate operational docs. Complete, well-formatted, three critical mismatches with actual implementation.

First: `DEPLOYMENT.md` described the rolling update strategy as `maxSurge=1, maxUnavailable=0`. Correct on paper — start new pod first, then kill old one, zero downtime. But Chapter 14 is literally the story of how that config deadlocked upgrades on t3.small nodes. t3.small has an 11-pod ENI limit. `maxSurge=1` tries to schedule a new pod on an already-full node — stays Pending forever. AI wrote docs to standard best practice, unaware of the node constraint. Correct config is `maxSurge=0, maxUnavailable=1`.

Second: `QUICKSTART.md` had `./deploy.sh dev --tag latest`. Chapter 20 just covered why we removed `latest` from the tag strategy — ECR IMMUTABLE means once `latest` is pushed it can never be overwritten. That command in a getting-started doc would confuse or break anyone following it. Updated to `git-$(git rev-parse --short HEAD)` to match what CI actually produces.

Third: `README.md` listed 4 documents (`DEPLOYMENT.md`, `QUICKSTART.md`, `GITHUB-OIDC-SETUP.md`, `SECRETS-MANAGEMENT.md`). The actual directory had 9 files. Five real docs were missing from the index. Two listed files didn't exist at all (`GITHUB-OIDC-SETUP.md`, `SECRETS-MANAGEMENT.md`). AI assembled a plausible-looking doc list from common patterns, not from an actual directory listing.

Pattern: AI docs are accurate at creation time and drift from the implementation over time. The two highest-risk fields are **default values** (likely overridden in env-specific values files) and **CLI commands** (tags, namespaces, and paths all change as code evolves but docs lag behind).

---

### 22. New Doc + New Code Agreed, But Both Violated an Earlier Constraint

**Files:** `devops/docs/RELEASE_PROCESS.md`, `.github/workflows/ci-cd.yml`

---

What makes this gotcha unusual: the AI-generated doc and the AI-modified code were consistent with each other — the problem was that both violated an earlier-established constraint.

At the start of this session, four sources described the ECR tag strategy:

| Source | Content |
|---|---|
| `MEMORY.md` | `git-sha` / semver / `pr-N`; IMMUTABLE; no floating tags |
| `RELEASE_PROCESS.md` (newly created) | `X.Y.Z` + `X.Y` (minor) + `X` (major) + `git-sha` |
| `ci-cd.yml` (modified) | `docker/metadata-action` with `pattern={{major}}.{{minor}}`, `pattern={{major}}`, `value=latest` |
| ECR repository | IMMUTABLE tag policy |

`RELEASE_PROCESS.md` was a brand-new document describing the "improved multi-dimensional tag strategy." `ci-cd.yml` was also updated to implement that strategy. The doc and the code **agreed with each other** — the change was internally self-consistent.

But both violated a constraint established months earlier: **ECR IMMUTABLE**. Floating tags (`X.Y`, `X`, `latest`) need to overwrite existing tags on every release. IMMUTABLE prevents that. Second push throws `ImageAlreadyExistsException`. CI breaks.

Root cause: AI focused on "what does this feature do" (multi-level tags) without tracing back to "what constraints does the runtime environment have" (ECR IMMUTABLE was set up months ago). The doc/code consistency was local; the global consistency with the full system was broken.

How to catch this class of bug: after adding any new feature, ask "what **already-existing** constraints could this conflict with?" In this project, the common constraint sources are: ECR config, K8s node spec, IAM permission boundaries, and Kyverno policies.

---

### 23. Node Selection Cascade: SPOT → Kyverno Down → Full Cluster Recovery

**Files:** `devops/terraform/environments/prod/main.tf`, `.github/workflows/ci-cd.yml`, `devops/terraform/modules/eks/aws-auth.tf`, `devops/k8s/external-secret.yaml`

---

One CI failure turned into six separate problems chained together. All of them started with a node choice.

**Root cause: SPOT pool exhaustion.**

The original design was `system-nodes-prod` (1× ON_DEMAND) + `persons-finder-nodes-prod` (SPOT app nodes). CI failed, logs showed Kyverno webhook unavailable. Investigation: SPOT capacity for t3.small in `ap-southeast-2` was exhausted — AWS couldn't fulfil the Spot request (`UnfulfillableCapacity`). Both SPOT app nodes had already terminated. Kyverno's admission controller was running on those nodes. With `failurePolicy: Fail`, a down webhook = no pod can be created cluster-wide. The cluster was effectively bricked.

Recovery sequence:
1. Temporarily removed the `system=true:NoSchedule` taint from the system node to let Kyverno schedule there
2. Scaled `system-nodes-prod` from 1 → 2 to add capacity
3. Kyverno recovered, cluster unbricked
4. Triggered a CI retrigger commit to verify

**First attempted fix: switch to t3.medium.**

More pods per node → fewer pressure issues. Changed `node_instance_type = "t3.medium"`. Applied via Terraform. Node group launched — then immediately failed. AWS reported the launch template was using an instance type not eligible for the account. This account runs on the Free Tier; t3.medium is not in the Free Tier eligible list. No warning from Terraform, no warning from the AWS console until the node actually tried to start. Changed back to t3.small.

**Second fix: switch everything to ON_DEMAND.**

"别用SPOT了，都用按需" — stopped using SPOT entirely. Changed `capacity_type = "SPOT"` → `"ON_DEMAND"`. Also scaled app nodes from 1 → 2 (`node_desired_count = 2, node_min_count = 2`) to ensure capacity headroom. Committed as `a22da7a`.

**Third problem: pod memory insufficient.**

With 2 app nodes, the persons-finder pod still wouldn't schedule. Node had 761Mi available; pod requested 768Mi (512Mi main + 256Mi sidecar). Margin: -7Mi. Changed sidecar memory request from `256Mi` → `128Mi`. Total: 640Mi. Pods scheduled.

**Fourth problem: namespace mismatch.**

During the chaos, discovered two parallel deployments existed:
- `default` namespace: CI had been deploying here (original `--namespace default` in helm command)
- `persons-finder` namespace: manual deployment from earlier sessions

Two deployments, two pods, neither authoritative. Fixed by changing the CI deploy target to `--namespace persons-finder` and deleting the `default` namespace deployment.

**Fifth problem: deployer ClusterRole missing `namespaces` permission.**

The new helm command used `kubectl create namespace persons-finder --dry-run=client -o yaml | kubectl apply -f -` before `helm upgrade`. The GitHub Actions IAM role → `deployers` group → `deployer` ClusterRole. That ClusterRole had `secrets, configmaps, services, pods...` but not `namespaces`. The namespace creation step threw `forbidden`. Added `namespaces` to the ClusterRole resource list in `aws-auth.tf` and patched it live.

**Sixth problem: ExternalSecret in wrong namespace.**

After deleting the `default` namespace deployment, the `persons-finder-secrets` K8s Secret was also gone (it had been ESO-synced into `default`). The app in `persons-finder` namespace couldn't find the secret. Applied the ExternalSecret into `persons-finder` namespace manually, ESO synced it. Updated `devops/k8s/external-secret.yaml` to set `namespace: persons-finder` permanently.

**Final state after all fixes:**
- 3× t3.small ON_DEMAND nodes (no SPOT in cluster)
- All deployments in `persons-finder` namespace
- persons-finder pod: 2/2 Running (main + sidecar)
- CI green on commit `0e2835b`

**Lessons:**

1. **SPOT + admission webhooks = dangerous.** If the webhook node goes, the cluster goes. Either pin admission controllers to ON_DEMAND nodes with tolerations, or don't use SPOT at all for small single-replica setups.
2. **Free Tier accounts silently block instance types.** AWS does not warn you at plan time. The failure only surfaces when the actual EC2 launch is attempted.
3. **t3.small 11-pod ENI limit has no margin.** 3 DaemonSet pods per node → 8 usable slots. With 6 system pods (cert-manager×3 + external-secrets×3) landing on one app node, you hit 9/11 before your app even schedules. Pod redistribution via manual deletion was needed.
4. **Namespace as a source of truth.** Two competing deployments in different namespaces looked like healthy pods but served different traffic. Always verify `kubectl get pods -A` before assuming there's only one deployment.
5. **RBAC gaps compound during recovery.** Adding a namespace step to the CI script exposed a missing RBAC permission that would never have been triggered in the old flow. New procedures uncover old permission gaps.

---

## Wrap-Up

Honest take: AI was genuinely useful here — generating first drafts, scaffolding, templates — much faster than writing from scratch. But every one of these 23 chapters is a real bug we hit, mostly because:

- AI doesn't know your specific constraints (t3.small pod limits, multi-repo OIDC, KMS key lifecycle, Free Tier account restrictions)
- AI-generated configs pass the "first run" check but blow up in fresh environments or edge cases
- You need to understand every line it gives you, not just copy-paste and ship

The scariest ones are the "invisible until it's too late" bugs: missing RBAC only shows up on a fresh cluster rebuild, KMS key change only bites you after the next destroy, sidecar wired backwards but the pod looks healthy. Code review won't catch these — only actually running the thing will.

Chapter 22 adds another pattern: fixing one thing while missing another. The main image tag was fixed to `git-sha` but `sidecar.image.tag` was left as `latest`. `maxSurge` was set to `0` for single-node and never updated after adding a second app node. Node groups were separated but Kyverno and cert-manager weren't pinned to the system node — they still run on SPOT. **A partial fix is not a global fix.**

Chapter 23 adds the cascade pattern: one infrastructure failure (SPOT pool exhaustion) triggered six separate problems in sequence — cluster bricked, wrong instance type tried, memory insufficient, namespace mismatch discovered, RBAC gap exposed, secret in wrong namespace. None of these were individually hard to fix. But each fix uncovered the next problem. The lesson: **production incidents are rarely a single failure — they're a chain.** Follow it all the way to the end before declaring recovery complete. Periodic live state verification (`kubectl get pods -A`, `kubectl get nodes`, `helm get values`) is more reliable than reading docs.
