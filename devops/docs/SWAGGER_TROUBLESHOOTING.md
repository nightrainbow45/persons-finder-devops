# Swagger UI Troubleshooting Guide

This guide provides solutions to common issues when deploying and using Swagger UI for the Persons Finder API on AWS EKS.

## Overview

This troubleshooting guide covers:
- Swagger UI access issues
- OpenAPI specification problems
- Authentication and authorization errors
- CORS configuration issues
- DNS and Ingress problems
- Certificate and TLS errors
- Performance and resource issues

## Quick Diagnostics

Run these commands first to gather information about your deployment:

```bash
# Check pod status
kubectl get pods -n persons-finder

# Check service and ingress
kubectl get service,ingress -n persons-finder

# Check application logs
kubectl logs -n persons-finder -l app=persons-finder --tail=50

# Check Swagger configuration
kubectl get deployment persons-finder -n persons-finder -o yaml | grep -E "SWAGGER_ENABLED|API_TITLE|CORS"

# Test local access
kubectl port-forward -n persons-finder service/persons-finder 8080:80
curl http://localhost:8080/swagger-ui.html
```

## Common Issues and Solutions

### 1. Swagger UI Returns 404 Not Found

**Symptoms:**
- Accessing `/swagger-ui.html` returns 404 error
- OpenAPI spec at `/v3/api-docs` returns 404

**Possible Causes:**
1. Swagger is disabled in configuration
2. Application failed to start properly
3. SpringDoc library not loaded
4. Incorrect path configuration

**Solutions:**

#### Check if Swagger is Enabled

```bash
# Verify SWAGGER_ENABLED environment variable
kubectl get deployment persons-finder -n persons-finder -o yaml | grep SWAGGER_ENABLED

# Expected output:
# - name: SWAGGER_ENABLED
#   value: "true"
```

If Swagger is disabled, enable it in your Helm values:

```yaml
# values.yaml
swagger:
  enabled: true
```

Redeploy:

```bash
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder
```

#### Verify Application Started Successfully

```bash
# Check pod status
kubectl get pods -n persons-finder

# Check application logs for SpringDoc initialization
kubectl logs -n persons-finder -l app=persons-finder | grep -i "springdoc\|swagger\|openapi"

# Expected log entries:
# Started OpenApiAutoConfiguration
# Initialized OpenAPI documentation
```

#### Test Direct Pod Access

```bash
# Port forward to pod
kubectl port-forward -n persons-finder <pod-name> 8080:8080

# Test Swagger UI
curl http://localhost:8080/swagger-ui.html

# Test OpenAPI spec
curl http://localhost:8080/v3/api-docs
```

If direct access works but Ingress doesn't, see [Ingress Issues](#5-ingress-routing-problems).

#### Verify SpringDoc Dependency

Check that the SpringDoc dependency is included in the application:

```bash
# Check application JAR for SpringDoc classes
kubectl exec -n persons-finder <pod-name> -- ls -la /app/lib/ | grep springdoc

# Expected output should show springdoc-openapi-ui JAR file
```

### 2. Swagger UI Loads but Shows No APIs

**Symptoms:**
- Swagger UI page loads successfully
- No API endpoints are displayed
- "No operations defined in spec!" message appears

**Possible Causes:**
1. OpenAPI specification is empty or malformed
2. Controller scanning not configured correctly
3. Controllers not annotated properly
4. Package scanning path incorrect

**Solutions:**

#### Check OpenAPI Specification

```bash
# Get the OpenAPI spec
kubectl port-forward -n persons-finder service/persons-finder 8080:80
curl http://localhost:8080/v3/api-docs | jq .

# Verify it contains paths
curl http://localhost:8080/v3/api-docs | jq '.paths'

# Expected output should show:
# {
#   "/api/v1/persons": { ... },
#   "/api/v1/persons/{id}/location": { ... },
#   "/api/v1/persons/{id}/nearby": { ... }
# }
```

If the spec is empty or missing paths, check the application configuration.

#### Verify Package Scanning Configuration

```bash
# Check application.properties configuration
kubectl exec -n persons-finder <pod-name> -- cat /app/config/application.properties | grep springdoc

# Expected configuration:
# springdoc.packages-to-scan=com.persons.finder.presentation
# springdoc.paths-to-match=/api/v1/**
```

#### Check Controller Annotations

Verify that controllers have proper OpenAPI annotations:

```bash
# Check application logs for controller scanning
kubectl logs -n persons-finder -l app=persons-finder | grep -i "mapping\|controller"

# Should show controller mappings like:
# Mapped "{[/api/v1/persons]}" onto public ...
```

#### Restart Application

Sometimes a restart resolves initialization issues:

```bash
# Restart pods
kubectl rollout restart deployment persons-finder -n persons-finder

# Wait for rollout to complete
kubectl rollout status deployment persons-finder -n persons-finder

# Verify Swagger UI now shows APIs
```

### 3. Basic Authentication Not Working

**Symptoms:**
- Accessing Swagger UI doesn't prompt for credentials
- Credentials are rejected with 401 Unauthorized
- Authentication works inconsistently

**Possible Causes:**
1. Basic auth secret not created
2. Incorrect htpasswd format
3. Ingress annotations not applied
4. Wrong username or password

**Solutions:**

#### Verify Basic Auth Secret Exists

```bash
# Check if secret exists
kubectl get secret persons-finder-basic-auth -n persons-finder

# View secret content (base64 encoded)
kubectl get secret persons-finder-basic-auth -n persons-finder -o yaml

# Decode and verify format
kubectl get secret persons-finder-basic-auth -n persons-finder -o jsonpath='{.data.auth}' | base64 -d

# Expected format: username:$apr1$salt$hash
```

If the secret doesn't exist or is incorrect, regenerate it:

```bash
# Generate new htpasswd hash
htpasswd -nb admin yourpassword

# Example output:
# admin:$apr1$xyz123...$abc456...

# Update values.yaml with the full output
swagger:
  basicAuth:
    enabled: true
    username: "admin"
    password: "$apr1$xyz123...$abc456..."

# Redeploy
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder
```

#### Verify Ingress Annotations

```bash
# Check Ingress annotations
kubectl get ingress persons-finder -n persons-finder -o yaml | grep -A 10 "annotations:"

# Expected annotations:
# nginx.ingress.kubernetes.io/auth-type: basic
# nginx.ingress.kubernetes.io/auth-secret: persons-finder-basic-auth
# nginx.ingress.kubernetes.io/auth-realm: "Authentication Required"
```

If annotations are missing, verify your Helm values:

```yaml
swagger:
  basicAuth:
    enabled: true
    username: "admin"
    password: "$apr1$..."
```

#### Test Authentication

```bash
# Test without credentials (should return 401)
curl -I https://api.example.com/swagger-ui.html

# Expected response:
# HTTP/2 401
# www-authenticate: Basic realm="Authentication Required"

# Test with credentials (should return 200)
curl -I -u admin:yourpassword https://api.example.com/swagger-ui.html

# Expected response:
# HTTP/2 200

# Verify API endpoints don't require auth
curl https://api.example.com/api/v1/persons

# Should return 200 without credentials
```

#### Common Authentication Issues

**Issue:** Password contains special characters that break the hash

**Solution:** Use a password without special shell characters, or properly escape them:

```bash
# Use a simple password for testing
htpasswd -nb admin TestPassword123

# Or escape special characters
htpasswd -nb admin 'P@ssw0rd!'
```

**Issue:** Browser caches old credentials

**Solution:** Clear browser cache or use incognito mode:

```bash
# Test with curl to bypass browser cache
curl -u admin:newpassword https://api.example.com/swagger-ui.html
```

### 4. CORS Errors in Browser

**Symptoms:**
- Browser console shows CORS errors
- "Access to fetch at '...' from origin '...' has been blocked by CORS policy"
- Preflight OPTIONS requests fail

**Possible Causes:**
1. Allowed origins not configured correctly
2. Origin URL doesn't match exactly
3. CORS configuration not applied
4. Using wildcard with credentials

**Solutions:**

#### Check CORS Configuration

```bash
# Verify CORS_ALLOWED_ORIGINS environment variable
kubectl get deployment persons-finder -n persons-finder -o yaml | grep CORS_ALLOWED_ORIGINS

# Expected output:
# - name: CORS_ALLOWED_ORIGINS
#   value: "https://app.example.com,https://dashboard.example.com"
```

#### Test CORS Headers

```bash
# Test preflight OPTIONS request
curl -X OPTIONS https://api.example.com/api/v1/persons \
  -H "Origin: https://app.example.com" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Content-Type" \
  -v

# Expected response headers:
# Access-Control-Allow-Origin: https://app.example.com
# Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
# Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With
# Access-Control-Max-Age: 3600
```

#### Update CORS Configuration

If CORS headers are missing or incorrect, update your Helm values:

```yaml
# For development (allow all origins)
cors:
  allowedOrigins: "*"

# For production (specific origins only)
cors:
  allowedOrigins: "https://app.example.com,https://dashboard.example.com"
```

Redeploy:

```bash
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder

# Verify pods restarted with new configuration
kubectl get pods -n persons-finder
```

#### Common CORS Issues

**Issue:** Origin URL has trailing slash

**Problem:** `https://app.example.com/` vs `https://app.example.com`

**Solution:** Add both variants to allowed origins:

```yaml
cors:
  allowedOrigins: "https://app.example.com,https://app.example.com/"
```

**Issue:** Using wildcard with credentials

**Problem:** Cannot use `allowedOrigins: "*"` when sending credentials

**Solution:** Specify exact origins:

```yaml
cors:
  allowedOrigins: "https://app.example.com"
```

**Issue:** Port number in origin

**Problem:** `https://app.example.com:3000` not matching `https://app.example.com`

**Solution:** Include the port in allowed origins:

```yaml
cors:
  allowedOrigins: "https://app.example.com:3000"
```

### 5. Ingress Routing Problems

**Symptoms:**
- Cannot access Swagger UI through custom domain
- 404 errors when accessing through Ingress
- Direct pod access works but Ingress doesn't

**Possible Causes:**
1. Ingress not created or misconfigured
2. DNS not resolving correctly
3. Ingress Controller not running
4. Path routing incorrect

**Solutions:**

#### Verify Ingress Resource

```bash
# Check if Ingress exists
kubectl get ingress -n persons-finder

# Describe Ingress for details
kubectl describe ingress persons-finder -n persons-finder

# Check Ingress configuration
kubectl get ingress persons-finder -n persons-finder -o yaml
```

Expected Ingress configuration:

```yaml
spec:
  rules:
  - host: api.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: persons-finder
            port:
              number: 80
```

#### Verify DNS Resolution

```bash
# Check DNS resolution
nslookup api.example.com

# Expected output should show LoadBalancer address
# api.example.com canonical name = a1b2c3d4-us-east-1.elb.amazonaws.com

# Test with dig
dig api.example.com

# Verify it resolves to LoadBalancer IPs
```

If DNS is not resolving, see [DNS Configuration Guide](./DNS_CONFIGURATION.md).

#### Check Ingress Controller

```bash
# Verify NGINX Ingress Controller is running
kubectl get pods -n ingress-nginx

# Expected output:
# NAME                                        READY   STATUS    RESTARTS   AGE
# ingress-nginx-controller-xxx                1/1     Running   0          5d

# Check Ingress Controller logs
kubectl logs -n ingress-nginx -l app.kubernetes.io/name=ingress-nginx --tail=50

# Look for errors related to your Ingress
```

#### Verify Service and Endpoints

```bash
# Check Service
kubectl get service persons-finder -n persons-finder

# Verify Service has endpoints
kubectl get endpoints persons-finder -n persons-finder

# Expected output should show pod IPs:
# NAME              ENDPOINTS                           AGE
# persons-finder    10.0.1.5:8080,10.0.1.6:8080        5d
```

If endpoints are empty, check pod readiness:

```bash
# Check pod status
kubectl get pods -n persons-finder

# Check readiness probe
kubectl describe pod <pod-name> -n persons-finder | grep -A 10 "Readiness:"
```

#### Test Ingress Routing

```bash
# Test HTTP access (if not redirected to HTTPS)
curl -I http://api.example.com/swagger-ui.html

# Test HTTPS access
curl -I https://api.example.com/swagger-ui.html

# Test with verbose output
curl -v https://api.example.com/swagger-ui.html

# Test OpenAPI spec
curl https://api.example.com/v3/api-docs
```

### 6. TLS Certificate Issues

**Symptoms:**
- HTTPS connection fails with certificate errors
- Browser shows "Your connection is not private"
- Certificate not issued or pending

**Possible Causes:**
1. cert-manager not installed
2. ClusterIssuer not configured
3. DNS not resolving (required for Let's Encrypt validation)
4. Certificate request failed

**Solutions:**

#### Check Certificate Status

```bash
# Check Certificate resource
kubectl get certificate -n persons-finder

# Expected output:
# NAME                   READY   SECRET                 AGE
# persons-finder-tls     True    persons-finder-tls     5d

# Describe Certificate for details
kubectl describe certificate persons-finder-tls -n persons-finder

# Check for events and status conditions
```

If Certificate is not Ready, check the status message:

```bash
# Get detailed status
kubectl get certificate persons-finder-tls -n persons-finder -o yaml | grep -A 10 "status:"
```

#### Verify cert-manager is Running

```bash
# Check cert-manager pods
kubectl get pods -n cert-manager

# Expected output:
# NAME                                       READY   STATUS    RESTARTS   AGE
# cert-manager-xxx                           1/1     Running   0          5d
# cert-manager-cainjector-xxx                1/1     Running   0          5d
# cert-manager-webhook-xxx                   1/1     Running   0          5d

# Check cert-manager logs
kubectl logs -n cert-manager -l app=cert-manager --tail=50
```

If cert-manager is not installed:

```bash
# Install cert-manager
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml

# Wait for pods to be ready
kubectl wait --for=condition=ready pod -l app.kubernetes.io/instance=cert-manager -n cert-manager --timeout=300s
```

#### Verify ClusterIssuer

```bash
# Check ClusterIssuer
kubectl get clusterissuer

# Expected output:
# NAME                  READY   AGE
# letsencrypt-prod      True    5d

# Describe ClusterIssuer
kubectl describe clusterissuer letsencrypt-prod
```

If ClusterIssuer doesn't exist, create it:

```yaml
# letsencrypt-prod-issuer.yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@example.com  # Replace with your email
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
    - http01:
        ingress:
          class: nginx
```

Apply it:

```bash
kubectl apply -f letsencrypt-prod-issuer.yaml
```

#### Check Certificate Request

```bash
# Check CertificateRequest
kubectl get certificaterequest -n persons-finder

# Describe the request
kubectl describe certificaterequest -n persons-finder

# Check for failure reasons
kubectl get certificaterequest -n persons-finder -o yaml | grep -A 10 "status:"
```

#### Verify DNS for ACME Challenge

Let's Encrypt requires DNS to resolve correctly for HTTP-01 challenge:

```bash
# Verify DNS resolves to LoadBalancer
nslookup api.example.com

# Test HTTP access (required for ACME challenge)
curl -I http://api.example.com/.well-known/acme-challenge/test

# Should return 404 (not 502 or connection error)
```

#### Delete and Recreate Certificate

If certificate is stuck, delete and recreate:

```bash
# Delete Certificate and Secret
kubectl delete certificate persons-finder-tls -n persons-finder
kubectl delete secret persons-finder-tls -n persons-finder

# Trigger recreation by redeploying
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder

# Watch certificate creation
kubectl get certificate -n persons-finder -w

# Should show READY=True after 1-2 minutes
```

#### Use Staging for Testing

To avoid Let's Encrypt rate limits during troubleshooting, use staging:

```yaml
# values.yaml
ingress:
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-staging"
  certManager:
    clusterIssuer: "letsencrypt-staging"
```

Create staging ClusterIssuer:

```yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-staging
spec:
  acme:
    server: https://acme-staging-v02.api.letsencrypt.org/directory
    email: admin@example.com
    privateKeySecretRef:
      name: letsencrypt-staging
    solvers:
    - http01:
        ingress:
          class: nginx
```

**Note:** Staging certificates will show as untrusted in browsers but confirm the process works.

### 7. Health Check Failures

**Symptoms:**
- Pods are not becoming ready
- Readiness probe failing
- Pods restarting frequently

**Possible Causes:**
1. Application not starting properly
2. Health endpoint not accessible
3. Probe timeout too short
4. Swagger initialization affecting startup time

**Solutions:**

#### Check Pod Status

```bash
# Check pod status
kubectl get pods -n persons-finder

# Describe pod for events
kubectl describe pod <pod-name> -n persons-finder

# Look for readiness probe failures:
# Readiness probe failed: Get "http://10.0.1.5:8080/actuator/health": dial tcp 10.0.1.5:8080: connect: connection refused
```

#### Verify Health Endpoint

```bash
# Port forward to pod
kubectl port-forward -n persons-finder <pod-name> 8080:8080

# Test health endpoint
curl http://localhost:8080/actuator/health

# Expected response:
# {"status":"UP"}
```

If health endpoint is not accessible:

```bash
# Check application logs
kubectl logs -n persons-finder <pod-name>

# Look for startup errors or exceptions
```

#### Adjust Probe Timing

If the application takes longer to start (especially with Swagger), increase probe delays:

```yaml
# values.yaml
probes:
  readiness:
    initialDelaySeconds: 60  # Increase from default 30
    periodSeconds: 10
    timeoutSeconds: 5
    failureThreshold: 3
  liveness:
    initialDelaySeconds: 90  # Increase from default 60
    periodSeconds: 15
    timeoutSeconds: 5
    failureThreshold: 3
```

Redeploy:

```bash
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder
```

#### Verify Swagger Doesn't Block Startup

Check if disabling Swagger resolves the issue:

```yaml
# values.yaml
swagger:
  enabled: false
```

If this fixes the issue, there may be a Swagger initialization problem. Check application logs for SpringDoc errors.

### 8. Performance Issues

**Symptoms:**
- Swagger UI loads slowly
- API responses are slow
- High CPU or memory usage

**Possible Causes:**
1. Insufficient resources allocated
2. Too many concurrent requests
3. Large OpenAPI specification
4. Memory leaks

**Solutions:**

#### Check Resource Usage

```bash
# Check current resource usage
kubectl top pods -n persons-finder

# Expected output:
# NAME                              CPU(cores)   MEMORY(bytes)
# persons-finder-xxx                100m         512Mi

# Check resource limits
kubectl describe pod <pod-name> -n persons-finder | grep -A 10 "Limits:"
```

#### Increase Resource Limits

If pods are hitting resource limits:

```yaml
# values.yaml
resources:
  limits:
    cpu: 2000m      # Increase from 1000m
    memory: 2Gi     # Increase from 1Gi
  requests:
    cpu: 500m       # Increase from 250m
    memory: 1Gi     # Increase from 512Mi
```

Redeploy:

```bash
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder
```

#### Monitor Application Metrics

```bash
# Check application logs for performance issues
kubectl logs -n persons-finder -l app=persons-finder | grep -i "slow\|timeout\|error"

# Check for memory issues
kubectl logs -n persons-finder -l app=persons-finder | grep -i "OutOfMemory\|heap"

# Monitor in real-time
kubectl logs -n persons-finder -l app=persons-finder -f
```

#### Optimize Swagger Configuration

If Swagger UI is slow to load:

```yaml
# application.properties
# Disable Swagger UI in production if not needed
springdoc.swagger-ui.enabled=false

# Or reduce the number of endpoints documented
springdoc.paths-to-match=/api/v1/**
```

#### Enable Autoscaling

If load is high, enable horizontal pod autoscaling:

```yaml
# values.yaml
autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
```

Monitor autoscaling:

```bash
# Check HPA status
kubectl get hpa -n persons-finder

# Watch autoscaling in action
kubectl get hpa -n persons-finder -w
```

### 9. API Documentation Incomplete

**Symptoms:**
- Some endpoints missing from Swagger UI
- Missing parameter descriptions
- No request/response examples

**Possible Causes:**
1. Controllers not properly annotated
2. Package scanning not including all controllers
3. Paths not matching configuration

**Solutions:**

#### Verify All Endpoints Are Documented

```bash
# Get OpenAPI spec
kubectl port-forward -n persons-finder service/persons-finder 8080:80
curl http://localhost:8080/v3/api-docs | jq '.paths | keys'

# Expected output:
# [
#   "/api/v1/persons",
#   "/api/v1/persons/{id}/location",
#   "/api/v1/persons/{id}/nearby"
# ]
```

#### Check Package Scanning Configuration

```bash
# Verify package scanning includes all controllers
kubectl exec -n persons-finder <pod-name> -- cat /app/config/application.properties | grep springdoc.packages-to-scan

# Expected:
# springdoc.packages-to-scan=com.persons.finder.presentation
```

#### Verify Controller Annotations

Check that controllers have OpenAPI annotations:

```kotlin
@RestController
@RequestMapping("api/v1/persons")
@Tag(name = "Persons", description = "Person management APIs")
class PersonController {
    
    @PostMapping("")
    @Operation(summary = "Create a new person")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Person created")
    ])
    fun createPerson(@RequestBody request: CreatePersonRequest): ResponseEntity<Person>
}
```

#### Check Application Logs

```bash
# Check for controller scanning logs
kubectl logs -n persons-finder -l app=persons-finder | grep -i "mapping\|controller\|springdoc"

# Should show controller mappings
```

### 10. 502 Bad Gateway Errors

**Symptoms:**
- Ingress returns 502 Bad Gateway
- Intermittent 502 errors
- Works sometimes but not always

**Possible Causes:**
1. Pods not ready
2. Service endpoints not available
3. Readiness probe failing
4. Application crashed

**Solutions:**

#### Check Pod Readiness

```bash
# Check if pods are ready
kubectl get pods -n persons-finder

# Expected output:
# NAME                              READY   STATUS    RESTARTS   AGE
# persons-finder-xxx                1/1     Running   0          5d

# If READY is 0/1, check readiness probe
kubectl describe pod <pod-name> -n persons-finder | grep -A 10 "Readiness:"
```

#### Verify Service Endpoints

```bash
# Check Service endpoints
kubectl get endpoints persons-finder -n persons-finder

# Expected output should show pod IPs:
# NAME              ENDPOINTS                           AGE
# persons-finder    10.0.1.5:8080,10.0.1.6:8080        5d

# If ENDPOINTS is empty, pods are not ready
```

#### Check Application Logs

```bash
# Check for application errors
kubectl logs -n persons-finder -l app=persons-finder --tail=100

# Look for:
# - Startup errors
# - Exceptions
# - Port binding issues
# - Health check failures
```

#### Verify Port Configuration

```bash
# Verify Service port matches container port
kubectl get service persons-finder -n persons-finder -o yaml | grep -A 5 "ports:"

# Expected:
# ports:
# - port: 80
#   targetPort: 8080
#   protocol: TCP

# Verify container port
kubectl get pod <pod-name> -n persons-finder -o yaml | grep containerPort

# Expected:
# - containerPort: 8080
```

#### Test Direct Pod Access

```bash
# Port forward to pod
kubectl port-forward -n persons-finder <pod-name> 8080:8080

# Test health endpoint
curl http://localhost:8080/actuator/health

# Test Swagger UI
curl http://localhost:8080/swagger-ui.html

# If direct access works, issue is with Service or Ingress
```

## Verification Commands

Use these commands to verify your Swagger UI deployment is working correctly:

### Basic Verification

```bash
# 1. Check all resources are created
kubectl get all -n persons-finder

# 2. Verify Swagger is enabled
kubectl get deployment persons-finder -n persons-finder -o yaml | grep SWAGGER_ENABLED

# 3. Test local access
kubectl port-forward -n persons-finder service/persons-finder 8080:80
curl http://localhost:8080/swagger-ui.html
curl http://localhost:8080/v3/api-docs

# 4. Test remote access (if Ingress enabled)
curl https://api.example.com/swagger-ui.html
curl https://api.example.com/v3/api-docs
```

### Complete Health Check

```bash
# 1. Pod health
kubectl get pods -n persons-finder
kubectl describe pod <pod-name> -n persons-finder

# 2. Service health
kubectl get service persons-finder -n persons-finder
kubectl get endpoints persons-finder -n persons-finder

# 3. Ingress health
kubectl get ingress -n persons-finder
kubectl describe ingress persons-finder -n persons-finder

# 4. Certificate health (if TLS enabled)
kubectl get certificate -n persons-finder
kubectl describe certificate persons-finder-tls -n persons-finder

# 5. Application health
curl https://api.example.com/actuator/health

# 6. Swagger UI health
curl -I https://api.example.com/swagger-ui.html

# 7. OpenAPI spec health
curl https://api.example.com/v3/api-docs | jq '.info'
```

### Log Analysis

```bash
# Application logs
kubectl logs -n persons-finder -l app=persons-finder --tail=100

# Ingress Controller logs
kubectl logs -n ingress-nginx -l app.kubernetes.io/name=ingress-nginx --tail=50

# cert-manager logs (if using TLS)
kubectl logs -n cert-manager -l app=cert-manager --tail=50

# Filter for errors
kubectl logs -n persons-finder -l app=persons-finder | grep -i "error\|exception\|failed"

# Filter for Swagger-related logs
kubectl logs -n persons-finder -l app=persons-finder | grep -i "swagger\|springdoc\|openapi"
```

## Getting Help

If you've tried the solutions above and still have issues:

### Gather Diagnostic Information

```bash
# Create a diagnostic report
kubectl get all -n persons-finder > diagnostic-report.txt
kubectl describe deployment persons-finder -n persons-finder >> diagnostic-report.txt
kubectl describe ingress persons-finder -n persons-finder >> diagnostic-report.txt
kubectl logs -n persons-finder -l app=persons-finder --tail=200 >> diagnostic-report.txt
kubectl get events -n persons-finder --sort-by='.lastTimestamp' >> diagnostic-report.txt
```

### Check Documentation

- [Helm Deployment Guide](./HELM_DEPLOYMENT.md) - Complete deployment instructions
- [DNS Configuration Guide](./DNS_CONFIGURATION.md) - DNS setup and troubleshooting
- [SpringDoc Documentation](https://springdoc.org/) - Official SpringDoc documentation
- [NGINX Ingress Documentation](https://kubernetes.github.io/ingress-nginx/) - Ingress configuration
- [cert-manager Documentation](https://cert-manager.io/docs/) - Certificate management

### Contact Support

Provide the following information when requesting help:

1. Kubernetes cluster version
2. Helm chart version
3. Error messages from logs
4. Output of diagnostic commands
5. Helm values configuration (redact sensitive information)
6. Steps to reproduce the issue

---

**Last Updated:** 2024
**Version:** 1.0.0
