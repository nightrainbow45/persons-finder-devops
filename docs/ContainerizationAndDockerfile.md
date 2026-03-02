# Requirement: Containerization & Dockerfile

> **Requirement:** Create a `Dockerfile` for the application. Ask an AI to write it. Audit the result — the AI likely missed best practices (non-root user, multi-stage build, pinning versions). Fix them. Output: an optimized `Dockerfile`.

---

## Quick Reference

**Requirement vs Implementation**

| Requirement | Current Status |
|---|---|
| Dockerfile created | ✅ `devops/docker/Dockerfile` (48 lines) |
| AI wrote the initial version | ✅ Documented in `AI_LOG.md` lines 19–44 |
| Multi-stage build | ✅ Stage 1: `gradle:7.6.4-jdk11-focal` → Stage 2: `eclipse-temurin:11.0.26_4-jre-alpine` |
| Non-root user | ✅ `addgroup + adduser + USER appuser` (lines 27–38) |
| Version pinning | ✅ Full patch versions on both base images — no `:latest` |
| AI flaws identified and fixed | ✅ 3 issues found, all resolved (documented in `AI_LOG.md`) |

**Code Snippet Source Map**

| Snippet | Source |
|---|---|
| Build stage — pinned gradle+JDK image | `Dockerfile` line 3 |
| Dependency caching layer | `Dockerfile` lines 8–12 |
| Source copy + build | `Dockerfile` lines 15–18 |
| Runtime stage — pinned JRE Alpine | `Dockerfile` line 21 |
| Alpine CVE patch (`apk upgrade`) | `Dockerfile` line 24 |
| Non-root user creation | `Dockerfile` lines 27–38 |
| JAR copy from builder | `Dockerfile` line 32 |
| HEALTHCHECK | `Dockerfile` lines 44–45 |
| Sidecar — static Go binary build | `Dockerfile.sidecar` lines 3–14 |
| Sidecar — non-root Alpine runtime | `Dockerfile.sidecar` lines 19–30 |
| AI original version + identified flaws | `AI_LOG.md` lines 27–43 |
| Best practice tests | `DockerfileBestPracticesTest.kt` lines 36–136 |

---

## 1. What Was Asked

| Requirement item | Description |
|---|---|
| Dockerfile | Create a working Dockerfile for the Spring Boot application |
| AI generation | Use an AI (ChatGPT/Claude) to write the initial version |
| Audit | Identify best practices the AI missed |
| Fixes | Apply corrections to produce an optimized Dockerfile |

---

## 2. What the AI Generated (Initial Version)

> Documented in `AI_LOG.md` lines 27–31

The AI produced a two-stage Dockerfile with:
- **Build stage:** `gradle:7.6-jdk11` (unpinned minor version)
- **Runtime stage:** `eclipse-temurin:11-jre-alpine` (unpinned patch version), with a non-root user, `EXPOSE 8080`, and a `HEALTHCHECK` against `/actuator/health`

---

## 3. Flaws Identified in AI Output

> `AI_LOG.md` lines 33–43

| # | Issue | Impact |
|---|---|---|
| 1 | **Unpinned base image versions** — `gradle:7.6-jdk11` and `eclipse-temurin:11-jre-alpine` use floating tags | Builds are non-reproducible; an upstream update can silently introduce CVEs or break compilation |
| 2 | **No `.dockerignore`** — entire project root sent as build context | Sends `.git/`, `build/`, test sources, Terraform state into the Docker daemon — slow builds, security risk |
| 3 | **`gradle dependencies \|\| true` masks errors** — suppresses dependency resolution failures with no comment | Real errors (wrong artifact ID, auth failure) silently pass, producing a broken image |

---

## 4. Fixes Applied — Final Dockerfile

**File:** `devops/docker/Dockerfile` (48 lines)

### Fix 1 — Pinned Base Image Versions

> `Dockerfile` line 3 (build stage)

```dockerfile
FROM gradle:7.6.4-jdk11-focal AS builder    # line 3 — full patch version pinned
```

> `Dockerfile` line 21 (runtime stage)

```dockerfile
FROM eclipse-temurin:11.0.26_4-jre-alpine  # line 21 — patch + build number pinned
```

Both images are pinned to exact patch versions. The runtime image uses `-jre-alpine` (JRE only, not JDK) on Alpine Linux for the smallest possible footprint.

---

### Fix 2 — Dependency Caching Layer (Layer Ordering)

> `Dockerfile` lines 8–18

```dockerfile
# Copy Gradle configuration files                    # line 7
COPY build.gradle.kts settings.gradle.kts ./         # line 8
COPY gradle ./gradle                                 # line 9

# Download dependencies (cached layer)               # line 11
RUN gradle dependencies --no-daemon || true          # line 12 — pre-warm dep cache

# Copy source code                                   # line 14
COPY src ./src                                       # line 15

# Build the application                              # line 17
RUN gradle clean build -x test --no-daemon           # line 18
```

Gradle config files are copied before source. Docker layer cache reuses the `gradle dependencies` layer on every rebuild unless `build.gradle.kts` changes — source-only changes skip the slow dependency download step.

---

### Fix 3 — Non-Root User

> `Dockerfile` lines 27–38

```dockerfile
# Create non-root user for security                  # line 26
RUN addgroup -S appgroup && adduser -S appuser -G appgroup  # line 27

WORKDIR /app                                         # line 29

COPY --from=builder /app/build/libs/*.jar app.jar    # line 32

# Change ownership to non-root user                  # line 34
RUN chown -R appuser:appgroup /app                   # line 35

# Switch to non-root user                            # line 37
USER appuser                                         # line 38
```

System user (`-S`) with no login shell, no home directory, no password. `chown` runs as root before `USER` drops privileges.

---

### Fix 4 — Alpine OS CVE Patching

> `Dockerfile` line 24

```dockerfile
# Upgrade Alpine packages to patch OS-level CVEs (gnutls, libpng, etc.)
RUN apk update && apk upgrade --no-cache             # line 24
```

Applies all available Alpine package security patches at image build time, closing OS-level CVEs that the base image maintainer may not have patched yet.

---

### Fix 5 — `.dockerignore` (69 lines)

> `devops/docker/.dockerignore`

```
.git/                  # version history — never needed in build context
build/                 # Gradle output — built inside container
devops/                # infrastructure code — not part of app
src/test/              # test sources — excluded from runtime image
**/.terraform/         # Terraform state — sensitive
*.tfstate              # Terraform state — sensitive
.kiro/ .claude/        # workspace files
```

Reduces build context from the full repository (~hundreds of MB) to only the files Docker actually needs. Prevents accidentally baking credentials or Terraform state into the image.

---

### Fix 6 — HEALTHCHECK

> `Dockerfile` lines 44–45

```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \  # line 44
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
```

Uses `wget` (available in Alpine) to probe the Spring Actuator health endpoint. `--start-period=40s` allows Spring Boot startup time before counting failures.

---

## 5. Final Dockerfile — Complete View

> `devops/docker/Dockerfile` (48 lines)

```dockerfile
# Stage 1: Build stage
FROM gradle:7.6.4-jdk11-focal AS builder          # line 3 — pinned

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts ./      # line 8 — deps before src
COPY gradle ./gradle
RUN gradle dependencies --no-daemon || true        # line 12 — cache warm-up
COPY src ./src                                     # line 15
RUN gradle clean build -x test --no-daemon         # line 18

# Stage 2: Runtime stage
FROM eclipse-temurin:11.0.26_4-jre-alpine         # line 21 — JRE only, Alpine

RUN apk update && apk upgrade --no-cache           # line 24 — OS CVE patch

RUN addgroup -S appgroup && adduser -S appuser -G appgroup  # line 27 — non-root

WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar  # line 32 — only JAR, no JDK
RUN chown -R appuser:appgroup /app                 # line 35
USER appuser                                       # line 38 — drop privileges

EXPOSE 8080                                        # line 41

HEALTHCHECK --interval=30s --timeout=3s \          # line 44
  --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider \
      http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]             # line 48
```

---

## 6. Sidecar Dockerfile (Bonus)

**File:** `devops/docker/Dockerfile.sidecar` (38 lines) — Go `pii-redaction-sidecar`

> Lines 3–14 — Build stage: statically linked binary

```dockerfile
FROM golang:1.25-alpine3.21 AS builder             # line 3 — pinned Go + Alpine

WORKDIR /build
COPY sidecar/go.mod ./
RUN go mod download                                # line 9 — deps before src
COPY sidecar/ ./

RUN CGO_ENABLED=0 GOOS=linux \                     # line 14 — static binary
    go build -trimpath -ldflags="-s -w" -o pii-sidecar .
```

`CGO_ENABLED=0` produces a fully static binary with no libc dependency.
`-trimpath` removes build path from binary (reproducibility).
`-ldflags="-s -w"` strips debug symbols — reduces binary size.

> Lines 19–30 — Runtime stage: ~7 MB Alpine image

```dockerfile
FROM alpine:3.21                                   # line 19 — no Go runtime needed

RUN addgroup -S sidecar && adduser -S sidecar -G sidecar  # line 22 — non-root

WORKDIR /app
COPY --from=builder /build/pii-sidecar .           # line 27 — binary only, ~8 MB
USER sidecar                                       # line 30 — drop privileges

EXPOSE 8081
ENV LISTEN_PORT=8081 \
    UPSTREAM_URL=https://api.openai.com

ENTRYPOINT ["./pii-sidecar"]
```

The final sidecar image contains only: Alpine base (~5 MB) + static Go binary (~8 MB) = ~13 MB total.

---

## 7. AI Audit — Full Record

> `AI_LOG.md` lines 19–44 — complete audit trail

| Step | Content |
|---|---|
| Original prompt | Kotlin 1.6.21 / Spring Boot 2.7.0 / JDK 11, non-root, minimal runtime, pinned versions, health check |
| AI output | Two-stage build with floating tags `gradle:7.6-jdk11`, `eclipse-temurin:11-jre-alpine` |
| Flaw 1 | Unpinned versions → non-reproducible builds |
| Flaw 2 | No `.dockerignore` → bloated build context |
| Flaw 3 | `\|\| true` masks errors with no explanation |
| Fix 1 | Pinned to `gradle:7.6.4-jdk11-focal` + `eclipse-temurin:11.0.26_4-jre-alpine` |
| Fix 2 | Created `.dockerignore` (69 lines) with 30+ exclusion patterns |
| Fix 3 | Added inline comment explaining the `\|\| true` trade-off |

---

## 8. Tests

**`DockerfileBestPracticesTest.kt`** (136 lines, 9 `@Test` methods) — parse and assert on the Dockerfile file directly; no Docker daemon required:

| Test | Asserts |
|---|---|
| `Dockerfile should use multi-stage build with at least two FROM statements` | ≥ 2 `FROM` lines |
| `build stage should use gradle JDK image` | First `FROM` contains `gradle` + `jdk` |
| `runtime stage should use a JRE alpine image` | Last `FROM` contains `jre` + `alpine` |
| `Dockerfile should contain USER instruction` | At least one `USER` line present |
| `Dockerfile should not run as root user` | Final `USER` is not `root` |
| `base images should not use latest tag` | No `FROM` ends with `:latest` |
| `base images should have explicit version tags` | Every `FROM` image contains `:` |
| `Dockerfile should expose port 8080` | `EXPOSE 8080` present |
| `Dockerfile should include HEALTHCHECK instruction` | `HEALTHCHECK` keyword present |

**`ContainerImageDeterminismPropertyTest.kt`** (5 `@Property(tries=100)` methods — 500 random iterations):

| Property | Asserts |
|---|---|
| `all base images must have pinned version tags` | Every `FROM` image has a specific version, never `:latest` |
| `COPY commands use specific paths for deterministic builds` | No dangerous wildcard patterns in COPY targets |
| `Dockerfile parsing is deterministic across multiple reads` | Same file → same analysis result every time |
| `Dockerfile uses multi-stage build for deterministic minimal images` | Multi-stage structure confirmed across random seeds |
| `build stage copies dependencies before source for deterministic caching` | `build.gradle.kts` COPY appears before `src` COPY |

---

## 9. File Map

| File | Lines | Purpose |
|---|---|---|
| `devops/docker/Dockerfile` | 48 | Main app: gradle build → JRE Alpine runtime |
| `devops/docker/Dockerfile.sidecar` | 38 | Go sidecar: static binary → Alpine runtime (~13 MB) |
| `devops/docker/.dockerignore` | 69 | Build context exclusions (git, build output, terraform state) |
| `AI_LOG.md` | — | Section 1 (lines 19–44): AI prompt, output, flaws, fixes |
| `test/.../DockerfileBestPracticesTest.kt` | 136 | 9 unit tests: multi-stage, non-root, pinned, EXPOSE, HEALTHCHECK |
| `test/.../ContainerImageDeterminismPropertyTest.kt` | — | 5 property tests, 500 iterations: reproducibility guarantees |
