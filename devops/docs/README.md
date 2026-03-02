# Deployment Documentation

This directory contains comprehensive deployment guides and operational documentation for the Persons Finder application.

## Documents

| File | Purpose |
|---|---|
| `QUICKSTART.md` | Fast-path guide: Kind (local) or EKS deployment in minutes |
| `DEPLOYMENT.md` | Full deployment lifecycle: Helm values, secrets, verification, rollback |
| `RELEASE_PROCESS.md` | Semantic versioning, ECR tag strategy (`git-sha` + semver), release steps |
| `HELM_DEPLOYMENT.md` | Helm chart deep-dive: all templates, values, environment overrides |
| `DNS_CONFIGURATION.md` | DNS setup for custom domain → NGINX Ingress LoadBalancer (CNAME/ALIAS) |
| `SWAGGER_TROUBLESHOOTING.md` | Swagger UI access, Basic Auth, CORS issues, disable in prod |
| `SECURITY_REVIEW.md` | Security controls checklist: RBAC, NetworkPolicy, TLS, pod security, supply chain |
| `NETWORK_SECURITY_VERIFICATION.md` | NetworkPolicy and VPC CNI enforcement verification steps |

## Getting Started

1. **Local testing** → `QUICKSTART.md` (Kind, no cloud account needed)
2. **EKS deployment** → `QUICKSTART.md` then `DEPLOYMENT.md` for details
3. **Release a new version** → `RELEASE_PROCESS.md` for tag strategy and Helm update
4. **Custom domain / TLS** → `DNS_CONFIGURATION.md`
5. **Swagger UI not loading** → `SWAGGER_TROUBLESHOOTING.md`
6. **Security audit** → `SECURITY_REVIEW.md` + `NETWORK_SECURITY_VERIFICATION.md`

## Key Facts

- **Production cluster:** `persons-finder-prod`, AWS EKS, `ap-southeast-2`
- **Production domain:** `aifindy.digico.cloud` (NGINX Ingress + Let's Encrypt TLS)
- **ECR:** `190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/persons-finder` (IMMUTABLE tags)
- **Image tag strategy:** `git-<sha>` for main branch builds, `X.Y.Z` semver for release tags
- **Swagger UI:** `/swagger-ui/index.html` · OpenAPI spec: `/v3/api-docs`
- **Health endpoint:** `/actuator/health` (liveness + readiness probes)
