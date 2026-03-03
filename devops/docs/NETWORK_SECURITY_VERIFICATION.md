# Network Security Verification Guide

## Overview

This guide provides step-by-step instructions for verifying network security configuration for the Persons Finder API deployment. It covers TLS/HTTPS verification, NetworkPolicy testing, and security best practices.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [TLS/HTTPS Verification](#tlshttps-verification)
3. [NetworkPolicy Verification](#networkpolicy-verification)
4. [Ingress Security Verification](#ingress-security-verification)
5. [Pod Security Verification](#pod-security-verification)
6. [Security Testing Checklist](#security-testing-checklist)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Tools

```bash
# kubectl - Kubernetes CLI
kubectl version --client

# curl - HTTP client
curl --version

# openssl - TLS/SSL toolkit
openssl version

# nslookup or dig - DNS lookup
nslookup -version
# or
dig -v
```

### Required Access

- Kubernetes cluster access with appropriate RBAC permissions
- DNS configuration access (for domain setup)
- Network access to test from different locations (for IP whitelist testing)

---

## TLS/HTTPS Verification

### 1. Verify cert-manager Installation

```bash
# Check if cert-manager is installed
kubectl get pods -n cert-manager

# Expected output:
# NAME                                       READY   STATUS    RESTARTS   AGE
# cert-manager-xxxxxxxxxx-xxxxx              1/1     Running   0          10d
# cert-manager-cainjector-xxxxxxxxxx-xxxxx   1/1     Running   0          10d
# cert-manager-webhook-xxxxxxxxxx-xxxxx      1/1     Running   0          10d
```

### 2. Verify ClusterIssuer Configuration

```bash
# Check ClusterIssuer exists
kubectl get clusterissuer

# Expected output:
# NAME                  READY   AGE
# letsencrypt-prod      True    10d
# letsencrypt-staging   True    10d

# Get details of production issuer
kubectl describe clusterissuer letsencrypt-prod
```

### 3. Verify DNS Configuration

```bash
# Replace api.example.com with your actual domain
DOMAIN="aifindy.digico.cloud"

# Check DNS resolution
nslookup $DOMAIN

# Or use dig for more details
dig $DOMAIN

# Verify it points to your Ingress LoadBalancer
kubectl get ingress -n <namespace>
# Compare the ADDRESS with DNS resolution result
```

### 4. Deploy with TLS Configuration

```bash
# Deploy with production values (includes TLS)
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f devops/helm/persons-finder/production-values.yaml \
  --namespace persons-finder \
  --create-namespace
```

### 5. Verify Certificate Creation

```bash
# Check Certificate resource
kubectl get certificate -n persons-finder

# Expected output:
# NAME                   READY   SECRET                 AGE
# persons-finder-tls     True    persons-finder-tls     5m

# Get certificate details
kubectl describe certificate persons-finder-tls -n persons-finder

# Check certificate secret
kubectl get secret persons-finder-tls -n persons-finder
```

### 6. Test HTTPS Access

```bash
# Test HTTPS access
curl -v https://aifindy.digico.cloud/actuator/health

# Expected output should include:
# * SSL connection using TLSv1.3 / TLS_AES_256_GCM_SHA384
# * Server certificate:
# *  subject: CN=api.example.com
# *  issuer: C=US; O=Let's Encrypt; CN=R3
# *  SSL certificate verify ok.
# < HTTP/2 200
```

### 7. Verify Certificate Details

```bash
# Check certificate expiration and details
echo | openssl s_client -servername api.example.com -connect api.example.com:443 2>/dev/null | openssl x509 -noout -dates -subject -issuer

# Expected output:
# notBefore=Jan  1 00:00:00 2024 GMT
# notAfter=Apr  1 00:00:00 2024 GMT
# subject=CN = api.example.com
# issuer=C = US, O = Let's Encrypt, CN = R3
```

### 8. Test HTTP to HTTPS Redirect

```bash
# Test that HTTP redirects to HTTPS
curl -v http://api.example.com/actuator/health

# Expected output should include:
# < HTTP/1.1 308 Permanent Redirect
# < Location: https://api.example.com/actuator/health
```

### 9. Test TLS Version and Ciphers

```bash
# Test TLS 1.2
openssl s_client -connect api.example.com:443 -tls1_2 < /dev/null

# Test TLS 1.3
openssl s_client -connect api.example.com:443 -tls1_3 < /dev/null

# Test that TLS 1.0 and 1.1 are disabled (should fail)
openssl s_client -connect api.example.com:443 -tls1 < /dev/null
# Expected: Connection should fail or be rejected

openssl s_client -connect api.example.com:443 -tls1_1 < /dev/null
# Expected: Connection should fail or be rejected
```

---

## NetworkPolicy Verification

### 1. Verify NetworkPolicy is Enabled

```bash
# Check if NetworkPolicy is deployed
kubectl get networkpolicy -n persons-finder

# Expected output:
# NAME                        POD-SELECTOR                      AGE
# persons-finder              app.kubernetes.io/name=persons-finder   5m

# Get NetworkPolicy details
kubectl describe networkpolicy persons-finder -n persons-finder
```

### 2. Test Ingress Rules

#### Test 2.1: Verify Ingress Controller Can Access Pods

```bash
# Get Ingress controller namespace
INGRESS_NS="ingress-nginx"

# Test access from Ingress controller
kubectl run test-ingress -n $INGRESS_NS --rm -it --image=curlimages/curl -- \
  curl -v http://persons-finder.persons-finder.svc.cluster.local/actuator/health

# Expected: Should succeed (200 OK)
```

#### Test 2.2: Verify Other Namespaces Cannot Access Pods

```bash
# Create a test pod in a different namespace
kubectl run test-blocked -n default --rm -it --image=curlimages/curl -- \
  curl -v --max-time 5 http://persons-finder.persons-finder.svc.cluster.local/actuator/health

# Expected: Should timeout or be blocked
# If NetworkPolicy is working correctly, this should fail
```

### 3. Test Egress Rules

#### Test 3.1: Verify DNS Resolution Works

```bash
# Test DNS resolution from application pod
POD_NAME=$(kubectl get pods -n persons-finder -l app.kubernetes.io/name=persons-finder -o jsonpath='{.items[0].metadata.name}')

kubectl exec -n persons-finder $POD_NAME -- nslookup google.com

# Expected: Should succeed (DNS resolution works)
```

#### Test 3.2: Verify HTTPS Egress Works

```bash
# Test HTTPS access to external API
kubectl exec -n persons-finder $POD_NAME -- curl -v --max-time 10 https://api.openai.com

# Expected: Should succeed (HTTPS egress allowed)
```

#### Test 3.3: Verify HTTP Egress is Blocked

```bash
# Test HTTP access to external site (should be blocked)
kubectl exec -n persons-finder $POD_NAME -- curl -v --max-time 5 http://example.com

# Expected: Should timeout or fail (only HTTPS allowed)
```

#### Test 3.4: Verify Internal Network Access is Blocked

```bash
# Test access to internal IP (should be blocked)
kubectl exec -n persons-finder $POD_NAME -- curl -v --max-time 5 http://10.0.0.1

# Expected: Should timeout or fail (internal IPs blocked)
```

### 4. Test Pod-to-Pod Communication

```bash
# Test communication between pods in the same namespace
POD1=$(kubectl get pods -n persons-finder -l app.kubernetes.io/name=persons-finder -o jsonpath='{.items[0].metadata.name}')
POD2=$(kubectl get pods -n persons-finder -l app.kubernetes.io/name=persons-finder -o jsonpath='{.items[1].metadata.name}')

# Get Pod2 IP
POD2_IP=$(kubectl get pod -n persons-finder $POD2 -o jsonpath='{.status.podIP}')

# Test connection from Pod1 to Pod2
kubectl exec -n persons-finder $POD1 -- curl -v --max-time 5 http://$POD2_IP:8080/actuator/health

# Expected: Should succeed (pod-to-pod communication allowed)
```

---

## Ingress Security Verification

### 1. Verify Basic Authentication (if enabled)

```bash
# Test without authentication (should fail)
curl -v https://api.example.com/swagger-ui.html

# Expected output:
# < HTTP/2 401
# < www-authenticate: Basic realm="Authentication Required"

# Test with authentication (should succeed)
curl -v -u admin:password https://api.example.com/swagger-ui.html

# Expected output:
# < HTTP/2 200
```

### 2. Verify API Endpoints Don't Require Authentication

```bash
# Test API endpoint without authentication (should succeed)
curl -v https://api.example.com/api/v1/persons

# Expected output:
# < HTTP/2 200
```

### 3. Verify IP Whitelist (if configured)

```bash
# Test from allowed IP (should succeed)
curl -v https://api.example.com/swagger-ui.html

# Test from non-allowed IP (should fail)
# Use a VPN or proxy to test from different IP
curl -v https://api.example.com/swagger-ui.html

# Expected output from non-allowed IP:
# < HTTP/2 403
```

### 4. Verify Security Headers

```bash
# Check security headers
curl -I https://api.example.com/actuator/health

# Look for security headers:
# Strict-Transport-Security: max-age=31536000; includeSubDomains
# X-Content-Type-Options: nosniff
# X-Frame-Options: DENY
# X-XSS-Protection: 1; mode=block
```

---

## Pod Security Verification

### 1. Verify Pod Security Context

```bash
# Get pod details
POD_NAME=$(kubectl get pods -n persons-finder -l app.kubernetes.io/name=persons-finder -o jsonpath='{.items[0].metadata.name}')

kubectl get pod -n persons-finder $POD_NAME -o yaml | grep -A 10 securityContext

# Expected output should include:
# securityContext:
#   allowPrivilegeEscalation: false
#   capabilities:
#     drop:
#     - ALL
#   readOnlyRootFilesystem: false
#   runAsNonRoot: true
#   runAsUser: 1000
```

### 2. Verify Pod is Running as Non-Root

```bash
# Check user ID inside container
kubectl exec -n persons-finder $POD_NAME -- id

# Expected output:
# uid=1000 gid=1000 groups=1000
# (Should NOT be uid=0 which is root)
```

### 3. Verify Capabilities are Dropped

```bash
# Check capabilities
kubectl exec -n persons-finder $POD_NAME -- cat /proc/1/status | grep Cap

# Expected: All capabilities should be 0 or minimal
```

---

## Security Testing Checklist

### Pre-Deployment Checklist

- [ ] cert-manager is installed and configured
- [ ] ClusterIssuer is created and ready
- [ ] DNS is configured and resolving correctly
- [ ] Production values file is configured with:
  - [ ] Specific domain name
  - [ ] TLS configuration
  - [ ] Basic authentication credentials (if enabled)
  - [ ] Specific CORS origins (not wildcard)
  - [ ] NetworkPolicy enabled (recommended)

### Post-Deployment Verification

#### TLS/HTTPS
- [ ] Certificate is created and ready
- [ ] HTTPS access works
- [ ] HTTP redirects to HTTPS
- [ ] Certificate is valid and from Let's Encrypt
- [ ] TLS 1.2 and 1.3 are supported
- [ ] TLS 1.0 and 1.1 are disabled

#### Authentication
- [ ] Swagger UI requires authentication (if enabled)
- [ ] API endpoints don't require authentication
- [ ] Invalid credentials are rejected (401)
- [ ] Valid credentials are accepted (200)

#### CORS
- [ ] Allowed origins can access API
- [ ] Non-allowed origins are blocked
- [ ] Preflight requests work correctly

#### NetworkPolicy
- [ ] Ingress controller can access pods
- [ ] Other namespaces cannot access pods
- [ ] DNS resolution works
- [ ] HTTPS egress works
- [ ] HTTP egress is blocked
- [ ] Internal network access is blocked
- [ ] Pod-to-pod communication works

#### Pod Security
- [ ] Pods run as non-root user
- [ ] Privilege escalation is disabled
- [ ] Capabilities are dropped
- [ ] Security context is properly configured

#### IP Whitelist (if configured)
- [ ] Allowed IPs can access
- [ ] Non-allowed IPs are blocked

---

## Troubleshooting

### Certificate Not Created

**Symptoms:**
- Certificate resource shows `READY: False`
- TLS secret not created

**Diagnosis:**
```bash
# Check certificate status
kubectl describe certificate persons-finder-tls -n persons-finder

# Check cert-manager logs
kubectl logs -n cert-manager -l app=cert-manager
```

**Common Causes:**
1. DNS not configured correctly
2. ClusterIssuer not ready
3. Rate limit reached (use staging issuer for testing)
4. Firewall blocking Let's Encrypt validation

**Solutions:**
1. Verify DNS resolves to correct IP
2. Check ClusterIssuer status: `kubectl get clusterissuer`
3. Use staging issuer for testing: `letsencrypt-staging`
4. Check cert-manager logs for specific errors

### NetworkPolicy Blocking Legitimate Traffic

**Symptoms:**
- Ingress controller cannot reach pods
- Pods cannot resolve DNS
- Pods cannot access external APIs

**Diagnosis:**
```bash
# Check NetworkPolicy rules
kubectl describe networkpolicy persons-finder -n persons-finder

# Check pod logs for connection errors
kubectl logs -n persons-finder $POD_NAME
```

**Common Causes:**
1. Ingress namespace label incorrect
2. DNS egress rule missing
3. HTTPS egress rule too restrictive

**Solutions:**
1. Verify Ingress namespace has correct label:
   ```bash
   kubectl get namespace ingress-nginx --show-labels
   ```
2. Ensure DNS egress allows UDP/TCP 53
3. Ensure HTTPS egress allows TCP 443 to public IPs

### Basic Authentication Not Working

**Symptoms:**
- Authentication always fails (401)
- Authentication not required

**Diagnosis:**
```bash
# Check if secret exists
kubectl get secret persons-finder-basic-auth -n persons-finder

# Check secret content
kubectl get secret persons-finder-basic-auth -n persons-finder -o yaml

# Check Ingress annotations
kubectl get ingress persons-finder -n persons-finder -o yaml | grep -A 5 annotations
```

**Common Causes:**
1. Secret not created (basicAuth.enabled=false)
2. Password format incorrect
3. Ingress annotation not applied

**Solutions:**
1. Ensure `swagger.basicAuth.enabled: true` in values
2. Generate password correctly: `htpasswd -nbB admin password`
3. Verify Ingress has configuration-snippet annotation

### CORS Not Working

**Symptoms:**
- Browser shows CORS errors
- Preflight requests fail

**Diagnosis:**
```bash
# Test preflight request
curl -v -X OPTIONS https://api.example.com/api/v1/persons \
  -H "Origin: https://app.example.com" \
  -H "Access-Control-Request-Method: POST"

# Check response headers
```

**Common Causes:**
1. Origin not in allowed list
2. CORS configuration not applied
3. Ingress overriding CORS headers

**Solutions:**
1. Add origin to `cors.allowedOrigins` in values
2. Verify CorsConfig is loaded: check application logs
3. Ensure Ingress has `enable-cors: "false"` annotation

### IP Whitelist Blocking Access

**Symptoms:**
- Access denied (403) from expected IPs
- Access allowed from unexpected IPs

**Diagnosis:**
```bash
# Check Ingress annotations
kubectl get ingress persons-finder -n persons-finder -o yaml | grep whitelist

# Check your current IP
curl https://ifconfig.me
```

**Common Causes:**
1. IP range incorrect
2. Using private IP instead of public IP
3. NAT/proxy changing source IP

**Solutions:**
1. Verify IP range format: `203.0.113.0/24`
2. Use public IP, not private IP
3. Check X-Forwarded-For header handling

---

## Security Monitoring

### Continuous Monitoring

```bash
# Monitor certificate expiration
kubectl get certificate -n persons-finder -w

# Monitor failed authentication attempts
kubectl logs -n persons-finder -l app.kubernetes.io/name=persons-finder | grep "401"

# Monitor NetworkPolicy violations
# (Requires network policy logging enabled)
kubectl logs -n kube-system -l k8s-app=calico-node | grep "denied"
```

### Automated Alerts

Consider setting up alerts for:
- Certificate expiration (< 30 days)
- High rate of 401 responses (potential brute force)
- High rate of 403 responses (potential scanning)
- NetworkPolicy violations
- Pod security context violations

---

## Conclusion

This guide provides comprehensive verification steps for network security configuration. Follow the checklist systematically to ensure all security measures are properly configured and working as expected.

For additional security considerations, refer to:
- [SECURITY_REVIEW.md](./SECURITY_REVIEW.md) - Comprehensive security review
- [SWAGGER_TROUBLESHOOTING.md](./SWAGGER_TROUBLESHOOTING.md) - Swagger-specific troubleshooting
- [HELM_DEPLOYMENT.md](./HELM_DEPLOYMENT.md) - Deployment guide

**Remember:** Security is an ongoing process. Regularly review and update security configurations, rotate credentials, and monitor for security events.
