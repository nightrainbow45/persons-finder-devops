# Helm Deployment Guide for Persons Finder API with Swagger UI

This guide provides comprehensive instructions for deploying the Persons Finder API with Swagger UI documentation using Helm on AWS EKS.

## Overview

The Persons Finder application includes integrated Swagger UI for interactive API documentation. This guide covers:
- Complete Helm values configuration
- Custom domain configuration
- Basic authentication setup
- CORS configuration
- TLS certificate management

## Prerequisites

- AWS EKS cluster running and accessible
- `kubectl` configured to access your cluster
- `helm` CLI installed (version 3.x)
- NGINX Ingress Controller installed in the cluster
- (Optional) cert-manager installed for automatic TLS certificates
- (Optional) A registered domain name for custom domain access

## Quick Start

### Basic Deployment (Development)

For a quick development deployment with default settings:

```bash
# Deploy with default values
helm install persons-finder ./devops/helm/persons-finder \
  --namespace persons-finder \
  --create-namespace

# Verify deployment
kubectl get pods -n persons-finder
kubectl get service -n persons-finder
```

Access Swagger UI via port-forward:

```bash
kubectl port-forward -n persons-finder service/persons-finder 8080:80
# Open http://localhost:8080/swagger-ui.html in your browser
```

## Complete Helm Values Configuration

### Development Environment Example

Create a `dev-values.yaml` file for development:

```yaml
# dev-values.yaml
replicaCount: 1

image:
  repository: 190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/persons-finder
  tag: "latest"
  pullPolicy: Always


# Swagger Configuration
swagger:
  enabled: true
  title: "Persons Finder API - Development"
  version: "1.0.0"
  description: "REST API for managing persons and their locations"
  basicAuth:
    enabled: false  # Disable auth for development

# CORS Configuration - Allow all origins for development
cors:
  allowedOrigins: "*"

# Ingress - Disabled for development (use port-forward instead)
ingress:
  enabled: false

# Resource limits - Lower for development
resources:
  limits:
    cpu: 500m
    memory: 512Mi
  requests:
    cpu: 100m
    memory: 256Mi

# Autoscaling - Disabled for development
autoscaling:
  enabled: false

# Network Policy - Disabled for development
networkPolicy:
  enabled: false
```

Deploy with development values:

```bash
helm install persons-finder ./devops/helm/persons-finder \
  -f dev-values.yaml \
  --namespace persons-finder \
  --create-namespace
```

### Staging Environment Example

Create a `staging-values.yaml` file for staging:

```yaml
# staging-values.yaml
replicaCount: 2

image:
  repository: 190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/persons-finder
  tag: "v1.0.0"  # Use specific version tag
  pullPolicy: IfNotPresent

# Swagger Configuration
swagger:
  enabled: true
  title: "Persons Finder API - Staging"
  version: "1.0.0"
  description: "REST API for managing persons and their locations (Staging Environment)"
  basicAuth:
    enabled: true
    username: "admin"
    # Generate password with: htpasswd -nb admin yourpassword
    password: "$apr1$xyz..."  # Replace with actual htpasswd hash

# CORS Configuration - Allow specific staging domains
cors:
  allowedOrigins: "https://staging.example.com,https://staging-app.example.com"

# Ingress Configuration
ingress:
  enabled: true
  className: "nginx"
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-staging"
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
  hosts:
    - host: api-staging.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: persons-finder-staging-tls
      hosts:
        - api-staging.example.com
  certManager:
    clusterIssuer: "letsencrypt-staging"

# Resource Configuration
resources:
  limits:
    cpu: 1000m
    memory: 1Gi
  requests:
    cpu: 250m
    memory: 512Mi

# Autoscaling
autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 5
  targetCPUUtilizationPercentage: 70

# Network Policy - Enable for staging
networkPolicy:
  enabled: true
```

Deploy to staging:

```bash
helm install persons-finder ./devops/helm/persons-finder \
  -f staging-values.yaml \
  --namespace persons-finder \
  --create-namespace
```


### Production Environment Example

Create a `production-values.yaml` file for production:

```yaml
# production-values.yaml
replicaCount: 3

image:
  repository: 190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/persons-finder
  tag: "v1.0.0"  # Always use specific version tags in production
  pullPolicy: IfNotPresent

# Swagger Configuration
swagger:
  enabled: true
  title: "Persons Finder API"
  version: "1.0.0"
  description: "REST API for managing persons and their locations"
  basicAuth:
    enabled: true
    username: "admin"
    # Generate with: htpasswd -nb admin <strong-password>
    password: "$apr1$abc..."  # Replace with actual htpasswd hash

# CORS Configuration - Restrict to production domains only
cors:
  allowedOrigins: "https://app.example.com,https://dashboard.example.com"

# Ingress Configuration
ingress:
  enabled: true
  className: "nginx"
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/force-ssl-redirect: "true"
    # Rate limiting (optional)
    nginx.ingress.kubernetes.io/limit-rps: "100"
  hosts:
    - host: api.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: persons-finder-tls
      hosts:
        - api.example.com
  
  # IP Whitelist (optional) - Restrict to corporate network
  # ipWhitelist: "203.0.113.0/24,198.51.100.0/24"
  
  certManager:
    clusterIssuer: "letsencrypt-prod"

# Resource Configuration - Higher limits for production
resources:
  limits:
    cpu: 2000m
    memory: 2Gi
  requests:
    cpu: 500m
    memory: 1Gi

# Autoscaling - More aggressive for production
autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
  stabilizationWindowSeconds: 300

# Health Probes - Production tuning
probes:
  liveness:
    httpGet:
      path: /actuator/health
      port: 8080
    initialDelaySeconds: 60
    periodSeconds: 15
    timeoutSeconds: 5
    failureThreshold: 3
  readiness:
    httpGet:
      path: /actuator/health
      port: 8080
    initialDelaySeconds: 30
    periodSeconds: 10
    timeoutSeconds: 3
    failureThreshold: 3

# Network Policy - Enabled for production security
networkPolicy:
  enabled: true
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
      - namespaceSelector:
          matchLabels:
            name: ingress-nginx
  egress:
    # DNS
    - to:
      - namespaceSelector: {}
      ports:
      - protocol: UDP
        port: 53
      - protocol: TCP
        port: 53
    # Internal traffic
    - to:
      - podSelector: {}
    # External HTTPS
    - to:
      - ipBlock:
          cidr: 0.0.0.0/0
          except:
            - 10.0.0.0/8
            - 172.16.0.0/12
            - 192.168.0.0/16
      ports:
      - protocol: TCP
        port: 443

# Pod Security
podSecurityContext:
  runAsNonRoot: true
  runAsUser: 1000
  fsGroup: 1000

securityContext:
  allowPrivilegeEscalation: false
  capabilities:
    drop:
    - ALL
  readOnlyRootFilesystem: false
```

Deploy to production:

```bash
helm install persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder \
  --create-namespace
```


## Configuration Details

### Custom Domain Configuration

To access Swagger UI through a custom domain, you need to:

1. **Configure DNS** - Point your domain to the NGINX Ingress Controller LoadBalancer
   - See [DNS_CONFIGURATION.md](./DNS_CONFIGURATION.md) for detailed instructions

2. **Update Helm values** - Set the domain in `ingress.hosts`:

```yaml
ingress:
  enabled: true
  className: "nginx"
  hosts:
    - host: api.example.com  # Your custom domain
      paths:
        - path: /
          pathType: Prefix
```

3. **Configure TLS** - Enable HTTPS with automatic certificate management:

```yaml
ingress:
  tls:
    - secretName: persons-finder-tls
      hosts:
        - api.example.com
  certManager:
    clusterIssuer: "letsencrypt-prod"
```

4. **Deploy and verify**:

```bash
# Deploy with custom domain
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder

# Wait for certificate to be issued (1-2 minutes)
kubectl get certificate -n persons-finder -w

# Verify access
curl https://api.example.com/swagger-ui.html
```

### Basic Authentication Configuration

Basic authentication protects Swagger UI from unauthorized access while keeping API endpoints publicly accessible.

#### Step 1: Generate htpasswd Hash

Use the `htpasswd` command to generate a bcrypt password hash:

```bash
# Install htpasswd (if not already installed)
# Ubuntu/Debian:
sudo apt-get install apache2-utils

# macOS:
brew install httpd

# Generate password hash
htpasswd -nb admin yourpassword

# Example output:
# admin:$apr1$xyz123...$abc456...
```

**Important:** Copy the entire output including the username and hash.

#### Step 2: Update Helm Values

Add the authentication credentials to your values file:

```yaml
swagger:
  basicAuth:
    enabled: true
    username: "admin"
    password: "$apr1$xyz123...$abc456..."  # Paste the full hash from htpasswd
```

#### Step 3: Deploy

```bash
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder
```

#### Step 4: Verify Authentication

Test that authentication is required for Swagger UI:

```bash
# Without credentials - should return 401
curl -I https://api.example.com/swagger-ui.html

# With credentials - should return 200
curl -I -u admin:yourpassword https://api.example.com/swagger-ui.html

# API endpoints should NOT require authentication
curl https://api.example.com/api/v1/persons
```

#### Security Best Practices for Basic Auth

1. **Use strong passwords** - At least 16 characters with mixed case, numbers, and symbols
2. **Rotate credentials regularly** - Change passwords every 90 days
3. **Use different credentials per environment** - Don't reuse staging passwords in production
4. **Store credentials securely** - Use a password manager or secrets management system
5. **Enable HTTPS** - Never use basic auth over plain HTTP
6. **Consider IP whitelisting** - Add an additional layer of security

#### Alternative: Using Kubernetes Secrets

For better security, store credentials in a Kubernetes Secret:

```bash
# Create htpasswd file
htpasswd -c auth admin

# Create Kubernetes Secret
kubectl create secret generic persons-finder-basic-auth \
  --from-file=auth \
  --namespace persons-finder

# Update values.yaml to reference the secret
# (requires custom Ingress template modification)
```


### CORS Configuration

CORS (Cross-Origin Resource Sharing) allows Swagger UI and other web applications to make requests to your API from different domains.

#### Development Configuration

For development, allow all origins:

```yaml
cors:
  allowedOrigins: "*"
```

**Warning:** Never use `"*"` in production as it allows any website to access your API.

#### Production Configuration

Specify exact allowed origins as a comma-separated list:

```yaml
cors:
  allowedOrigins: "https://app.example.com,https://dashboard.example.com,https://admin.example.com"
```

#### CORS Behavior

The application configures CORS with the following settings:

- **Allowed Methods:** GET, POST, PUT, DELETE, OPTIONS
- **Allowed Headers:** Content-Type, Authorization, X-Requested-With
- **Max Age:** 3600 seconds (1 hour)
- **Credentials:** Enabled when not using wildcard origin

#### Testing CORS

Test CORS configuration with a preflight request:

```bash
# Test OPTIONS preflight request
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

#### Common CORS Issues

**Problem:** Browser shows CORS error even though origin is configured

**Solutions:**
1. Verify the origin URL is exactly correct (including protocol and port)
2. Check for trailing slashes - `https://app.example.com` vs `https://app.example.com/`
3. Ensure the application has restarted after configuration change
4. Check browser console for the exact error message

**Problem:** Credentials not being sent with requests

**Solutions:**
1. Ensure you're not using wildcard origin (`"*"`)
2. Configure the frontend to send credentials:
   ```javascript
   fetch('https://api.example.com/api/v1/persons', {
     credentials: 'include'
   })
   ```

### TLS Certificate Configuration

TLS certificates enable HTTPS access to your API. The Helm chart supports automatic certificate management using cert-manager.

#### Prerequisites

Ensure cert-manager is installed in your cluster:

```bash
# Check if cert-manager is installed
kubectl get pods -n cert-manager

# If not installed, install cert-manager
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml
```

#### Configure ClusterIssuer

Create a ClusterIssuer for Let's Encrypt:

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

Apply the ClusterIssuer:

```bash
kubectl apply -f letsencrypt-prod-issuer.yaml

# Verify it's ready
kubectl get clusterissuer letsencrypt-prod
```

#### Enable TLS in Helm Values

Configure TLS in your values file:

```yaml
ingress:
  enabled: true
  className: "nginx"
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
  hosts:
    - host: api.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: persons-finder-tls
      hosts:
        - api.example.com
  certManager:
    clusterIssuer: "letsencrypt-prod"
```

#### Deploy and Monitor Certificate Issuance

```bash
# Deploy with TLS enabled
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder

# Watch certificate creation (takes 1-2 minutes)
kubectl get certificate -n persons-finder -w

# Check certificate status
kubectl describe certificate persons-finder-tls -n persons-finder

# Verify the secret was created
kubectl get secret persons-finder-tls -n persons-finder
```

#### Verify HTTPS Access

```bash
# Test HTTPS connection
curl -I https://api.example.com/swagger-ui.html

# Verify certificate details
openssl s_client -connect api.example.com:443 -servername api.example.com < /dev/null

# Check certificate expiration
echo | openssl s_client -connect api.example.com:443 -servername api.example.com 2>/dev/null | openssl x509 -noout -dates
```

#### Certificate Renewal

Let's Encrypt certificates are valid for 90 days. cert-manager automatically renews them 30 days before expiration.

Monitor renewal:

```bash
# Check certificate renewal status
kubectl describe certificate persons-finder-tls -n persons-finder

# Check cert-manager logs
kubectl logs -n cert-manager -l app=cert-manager
```

#### Staging vs Production Certificates

For testing, use Let's Encrypt staging environment to avoid rate limits:

```yaml
# Staging configuration
ingress:
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-staging"
  certManager:
    clusterIssuer: "letsencrypt-staging"
```

Create a staging ClusterIssuer:

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

**Note:** Staging certificates will show as untrusted in browsers. Use them only for testing.


## Deployment Commands

### Install

First-time installation:

```bash
# Install with default values
helm install persons-finder ./devops/helm/persons-finder \
  --namespace persons-finder \
  --create-namespace

# Install with custom values
helm install persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder \
  --create-namespace

# Install with inline value overrides
helm install persons-finder ./devops/helm/persons-finder \
  --namespace persons-finder \
  --create-namespace \
  --set swagger.enabled=true \
  --set ingress.enabled=true \
  --set ingress.hosts[0].host=api.example.com
```

### Upgrade

Update an existing deployment:

```bash
# Upgrade with new values
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder

# Upgrade with inline overrides
helm upgrade persons-finder ./devops/helm/persons-finder \
  --namespace persons-finder \
  --set image.tag=v1.1.0 \
  --reuse-values

# Upgrade and wait for rollout to complete
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder \
  --wait \
  --timeout 5m
```

### Rollback

Rollback to a previous release:

```bash
# List release history
helm history persons-finder -n persons-finder

# Rollback to previous release
helm rollback persons-finder -n persons-finder

# Rollback to specific revision
helm rollback persons-finder 3 -n persons-finder
```

### Uninstall

Remove the deployment:

```bash
# Uninstall release
helm uninstall persons-finder -n persons-finder

# Uninstall and delete namespace
helm uninstall persons-finder -n persons-finder
kubectl delete namespace persons-finder
```

### Dry Run and Template Validation

Test configuration before deploying:

```bash
# Dry run to see what would be deployed
helm install persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder \
  --dry-run

# Generate templates without installing
helm template persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder

# Validate template syntax
helm lint ./devops/helm/persons-finder \
  -f production-values.yaml
```

## Verification and Testing

### Verify Deployment

Check that all resources are created and healthy:

```bash
# Check Helm release status
helm status persons-finder -n persons-finder

# Check pods
kubectl get pods -n persons-finder
kubectl describe pod <pod-name> -n persons-finder

# Check service
kubectl get service persons-finder -n persons-finder

# Check ingress
kubectl get ingress -n persons-finder
kubectl describe ingress persons-finder -n persons-finder

# Check certificate (if TLS enabled)
kubectl get certificate -n persons-finder
kubectl describe certificate persons-finder-tls -n persons-finder

# Check logs
kubectl logs -n persons-finder -l app=persons-finder --tail=100
```

### Test Swagger UI Access

#### Local Access (Port Forward)

```bash
# Forward port to local machine
kubectl port-forward -n persons-finder service/persons-finder 8080:80

# Access Swagger UI
open http://localhost:8080/swagger-ui.html

# Test API endpoint
curl http://localhost:8080/api/v1/persons
```

#### Remote Access (Ingress)

```bash
# Test Swagger UI (without auth)
curl -I https://api.example.com/swagger-ui.html

# Test Swagger UI (with basic auth)
curl -I -u admin:password https://api.example.com/swagger-ui.html

# Test OpenAPI specification
curl https://api.example.com/v3/api-docs | jq .

# Test API endpoint
curl https://api.example.com/api/v1/persons

# Test with POST request
curl -X POST https://api.example.com/api/v1/persons \
  -H "Content-Type: application/json" \
  -d '{"name": "John Doe"}'
```

### Health Check

```bash
# Check application health
curl https://api.example.com/actuator/health

# Expected response:
# {"status":"UP"}
```

### Load Testing

Test application performance:

```bash
# Install Apache Bench (if not installed)
sudo apt-get install apache2-utils

# Run load test (100 requests, 10 concurrent)
ab -n 100 -c 10 https://api.example.com/api/v1/persons

# Run load test with authentication
ab -n 100 -c 10 -A admin:password https://api.example.com/swagger-ui.html
```


## Troubleshooting

### Pods Not Starting

**Problem:** Pods are in `CrashLoopBackOff` or `Error` state

**Solutions:**

```bash
# Check pod status
kubectl get pods -n persons-finder

# Describe pod for events
kubectl describe pod <pod-name> -n persons-finder

# Check logs
kubectl logs -n persons-finder <pod-name>

# Check previous container logs (if restarting)
kubectl logs -n persons-finder <pod-name> --previous

# Common issues:
# 1. Image pull errors - verify image repository and tag
# 2. Configuration errors - check environment variables
# 3. Resource limits - check if pod is OOMKilled
# 4. Health check failures - verify /actuator/health endpoint
```

### Swagger UI Not Accessible

**Problem:** Cannot access Swagger UI through Ingress

**Solutions:**

```bash
# 1. Verify Ingress is created
kubectl get ingress -n persons-finder
kubectl describe ingress persons-finder -n persons-finder

# 2. Check Ingress Controller
kubectl get pods -n ingress-nginx
kubectl logs -n ingress-nginx -l app.kubernetes.io/name=ingress-nginx

# 3. Verify DNS resolution
nslookup api.example.com
dig api.example.com

# 4. Test direct pod access
kubectl port-forward -n persons-finder <pod-name> 8080:8080
curl http://localhost:8080/swagger-ui.html

# 5. Check if Swagger is enabled
kubectl get deployment persons-finder -n persons-finder -o yaml | grep SWAGGER_ENABLED
```

### Basic Authentication Not Working

**Problem:** Basic auth returns 401 even with correct credentials

**Solutions:**

```bash
# 1. Verify secret exists
kubectl get secret persons-finder-basic-auth -n persons-finder

# 2. Check secret content
kubectl get secret persons-finder-basic-auth -n persons-finder -o yaml

# 3. Verify Ingress annotations
kubectl get ingress persons-finder -n persons-finder -o yaml | grep -A 5 "annotations:"

# 4. Test with curl verbose mode
curl -v -u admin:password https://api.example.com/swagger-ui.html

# 5. Regenerate htpasswd hash
htpasswd -nb admin newpassword

# 6. Update values and redeploy
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder
```

### CORS Errors

**Problem:** Browser shows CORS errors when accessing API

**Solutions:**

```bash
# 1. Check CORS configuration
kubectl get deployment persons-finder -n persons-finder -o yaml | grep CORS_ALLOWED_ORIGINS

# 2. Test CORS headers
curl -X OPTIONS https://api.example.com/api/v1/persons \
  -H "Origin: https://app.example.com" \
  -H "Access-Control-Request-Method: POST" \
  -v

# 3. Verify allowed origins include your domain
# Update values.yaml:
cors:
  allowedOrigins: "https://app.example.com,https://other.example.com"

# 4. Redeploy
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder
```

### Certificate Issues

**Problem:** TLS certificate not issued or HTTPS not working

**Solutions:**

```bash
# 1. Check certificate status
kubectl get certificate -n persons-finder
kubectl describe certificate persons-finder-tls -n persons-finder

# 2. Check cert-manager logs
kubectl logs -n cert-manager -l app=cert-manager

# 3. Verify ClusterIssuer
kubectl get clusterissuer
kubectl describe clusterissuer letsencrypt-prod

# 4. Check certificate request
kubectl get certificaterequest -n persons-finder
kubectl describe certificaterequest -n persons-finder

# 5. Verify DNS is resolving correctly
nslookup api.example.com

# 6. Check Ingress annotations
kubectl get ingress persons-finder -n persons-finder -o yaml | grep cert-manager

# 7. Delete and recreate certificate
kubectl delete certificate persons-finder-tls -n persons-finder
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder
```

### High Memory Usage

**Problem:** Pods consuming too much memory or getting OOMKilled

**Solutions:**

```bash
# 1. Check current resource usage
kubectl top pods -n persons-finder

# 2. Check pod events for OOMKilled
kubectl describe pod <pod-name> -n persons-finder | grep -A 10 "Events:"

# 3. Increase memory limits
# Update values.yaml:
resources:
  limits:
    memory: 2Gi
  requests:
    memory: 1Gi

# 4. Redeploy
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder

# 5. Monitor memory usage
kubectl top pods -n persons-finder -w
```

### Ingress Returns 502 Bad Gateway

**Problem:** Ingress returns 502 errors

**Solutions:**

```bash
# 1. Check if pods are ready
kubectl get pods -n persons-finder

# 2. Verify readiness probe
kubectl describe pod <pod-name> -n persons-finder | grep -A 10 "Readiness:"

# 3. Check service endpoints
kubectl get endpoints persons-finder -n persons-finder

# 4. Verify service port matches container port
kubectl get service persons-finder -n persons-finder -o yaml | grep -A 5 "ports:"
kubectl get pod <pod-name> -n persons-finder -o yaml | grep containerPort

# 5. Check application logs
kubectl logs -n persons-finder <pod-name>

# 6. Test direct pod access
kubectl port-forward -n persons-finder <pod-name> 8080:8080
curl http://localhost:8080/actuator/health
```


## Advanced Configuration

### IP Whitelisting

Restrict access to specific IP addresses or CIDR ranges:

```yaml
ingress:
  enabled: true
  ipWhitelist: "203.0.113.0/24,198.51.100.0/24,192.0.2.1"
```

This adds the `nginx.ingress.kubernetes.io/whitelist-source-range` annotation to the Ingress.

Test IP whitelisting:

```bash
# From allowed IP - should work
curl https://api.example.com/swagger-ui.html

# From non-allowed IP - should return 403
curl https://api.example.com/swagger-ui.html
```

### Multiple Domains

Configure multiple domains for the same application:

```yaml
ingress:
  enabled: true
  hosts:
    - host: api.example.com
      paths:
        - path: /
          pathType: Prefix
    - host: api-v1.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: persons-finder-tls
      hosts:
        - api.example.com
        - api-v1.example.com
```

### Custom Ingress Annotations

Add custom NGINX Ingress annotations:

```yaml
ingress:
  enabled: true
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    # Rate limiting
    nginx.ingress.kubernetes.io/limit-rps: "100"
    nginx.ingress.kubernetes.io/limit-connections: "10"
    # Request size limits
    nginx.ingress.kubernetes.io/proxy-body-size: "10m"
    # Timeouts
    nginx.ingress.kubernetes.io/proxy-connect-timeout: "60"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "60"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "60"
    # Custom headers
    nginx.ingress.kubernetes.io/configuration-snippet: |
      more_set_headers "X-Frame-Options: DENY";
      more_set_headers "X-Content-Type-Options: nosniff";
      more_set_headers "X-XSS-Protection: 1; mode=block";
```

### Resource Quotas and Limits

Configure resource quotas for the namespace:

```yaml
# resource-quota.yaml
apiVersion: v1
kind: ResourceQuota
metadata:
  name: persons-finder-quota
  namespace: persons-finder
spec:
  hard:
    requests.cpu: "4"
    requests.memory: 8Gi
    limits.cpu: "8"
    limits.memory: 16Gi
    persistentvolumeclaims: "0"
    services.loadbalancers: "0"
```

Apply the quota:

```bash
kubectl apply -f resource-quota.yaml
```

### Horizontal Pod Autoscaling (HPA)

The chart includes HPA configuration. Customize scaling behavior:

```yaml
autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
  # Optional: target memory utilization
  # targetMemoryUtilizationPercentage: 80
  stabilizationWindowSeconds: 300
```

Monitor autoscaling:

```bash
# Check HPA status
kubectl get hpa -n persons-finder

# Describe HPA for details
kubectl describe hpa persons-finder -n persons-finder

# Watch HPA in real-time
kubectl get hpa -n persons-finder -w
```

### Network Policies

Enable network policies for enhanced security:

```yaml
networkPolicy:
  enabled: true
  policyTypes:
    - Ingress
    - Egress
  ingress:
    # Allow traffic from Ingress Controller
    - from:
      - namespaceSelector:
          matchLabels:
            name: ingress-nginx
  egress:
    # Allow DNS
    - to:
      - namespaceSelector: {}
      ports:
      - protocol: UDP
        port: 53
      - protocol: TCP
        port: 53
    # Allow internal traffic
    - to:
      - podSelector: {}
    # Allow external HTTPS
    - to:
      - ipBlock:
          cidr: 0.0.0.0/0
          except:
            - 10.0.0.0/8
            - 172.16.0.0/12
            - 192.168.0.0/16
      ports:
      - protocol: TCP
        port: 443
```

Test network policies:

```bash
# Verify policy is created
kubectl get networkpolicy -n persons-finder

# Describe policy
kubectl describe networkpolicy persons-finder -n persons-finder

# Test connectivity from allowed namespace
kubectl run test-pod --image=curlimages/curl -n ingress-nginx -- sleep 3600
kubectl exec -it test-pod -n ingress-nginx -- curl http://persons-finder.persons-finder.svc.cluster.local/actuator/health

# Test connectivity from non-allowed namespace (should fail)
kubectl run test-pod --image=curlimages/curl -n default -- sleep 3600
kubectl exec -it test-pod -n default -- curl http://persons-finder.persons-finder.svc.cluster.local/actuator/health
```

### PII Redaction Sidecar

Enable the PII redaction sidecar for additional data protection:

```yaml
sidecar:
  enabled: true
  upstreamUrl: "https://api.openai.com"
  image:
    repository: 190239490233.dkr.ecr.ap-southeast-2.amazonaws.com/pii-redaction-sidecar
    tag: "latest"
  resources:
    limits:
      cpu: 500m
      memory: 512Mi
    requests:
      cpu: 100m
      memory: 256Mi
  port: 8081
```

The sidecar intercepts LLM API calls and redacts PII before forwarding requests.


## Security Best Practices

### 1. Use Specific Image Tags

Never use `latest` tag in production:

```yaml
image:
  tag: "v1.0.0"  # Use specific version
  pullPolicy: IfNotPresent
```

### 2. Enable Pod Security Context

Run containers as non-root user:

```yaml
podSecurityContext:
  runAsNonRoot: true
  runAsUser: 1000
  fsGroup: 1000

securityContext:
  allowPrivilegeEscalation: false
  capabilities:
    drop:
    - ALL
  readOnlyRootFilesystem: false
```

### 3. Enable Network Policies

Restrict network traffic:

```yaml
networkPolicy:
  enabled: true
```

### 4. Use Strong Authentication

- Use strong passwords (16+ characters)
- Rotate credentials regularly
- Consider OAuth2 for production

### 5. Enable HTTPS Only

```yaml
ingress:
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/force-ssl-redirect: "true"
```

### 6. Restrict CORS Origins

Never use wildcard in production:

```yaml
cors:
  allowedOrigins: "https://app.example.com"  # Specific domains only
```

### 7. Set Resource Limits

Prevent resource exhaustion:

```yaml
resources:
  limits:
    cpu: 2000m
    memory: 2Gi
  requests:
    cpu: 500m
    memory: 1Gi
```

### 8. Enable Audit Logging

Monitor access to Swagger UI:

```yaml
ingress:
  annotations:
    nginx.ingress.kubernetes.io/enable-access-log: "true"
```

### 9. Regular Updates

- Keep Helm chart updated
- Update application images regularly
- Monitor security advisories

### 10. Secrets Management

Use Kubernetes Secrets or external secrets management:

```bash
# Create secret for sensitive data
kubectl create secret generic persons-finder-secrets \
  --from-literal=api-key=your-secret-key \
  --namespace persons-finder
```

## Monitoring and Observability

### Prometheus Metrics

The application exposes metrics at `/actuator/prometheus`:

```bash
# Access metrics
curl https://api.example.com/actuator/prometheus
```

Configure Prometheus to scrape metrics:

```yaml
# ServiceMonitor for Prometheus Operator
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: persons-finder
  namespace: persons-finder
spec:
  selector:
    matchLabels:
      app: persons-finder
  endpoints:
  - port: http
    path: /actuator/prometheus
    interval: 30s
```

### Logging

View application logs:

```bash
# View logs from all pods
kubectl logs -n persons-finder -l app=persons-finder

# Follow logs in real-time
kubectl logs -n persons-finder -l app=persons-finder -f

# View logs from specific pod
kubectl logs -n persons-finder <pod-name>

# View logs from previous container (if crashed)
kubectl logs -n persons-finder <pod-name> --previous
```

### Health Checks

Monitor application health:

```bash
# Liveness probe
curl https://api.example.com/actuator/health/liveness

# Readiness probe
curl https://api.example.com/actuator/health/readiness

# Full health details
curl https://api.example.com/actuator/health
```

### Alerting

Set up alerts for critical issues:

```yaml
# PrometheusRule example
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: persons-finder-alerts
  namespace: persons-finder
spec:
  groups:
  - name: persons-finder
    interval: 30s
    rules:
    - alert: PersonsFinderDown
      expr: up{job="persons-finder"} == 0
      for: 5m
      labels:
        severity: critical
      annotations:
        summary: "Persons Finder is down"
        description: "Persons Finder has been down for more than 5 minutes"
    
    - alert: HighErrorRate
      expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.05
      for: 5m
      labels:
        severity: warning
      annotations:
        summary: "High error rate detected"
        description: "Error rate is above 5% for the last 5 minutes"
```

## CI/CD Integration

### GitLab CI Example

```yaml
# .gitlab-ci.yml
deploy:staging:
  stage: deploy
  image: alpine/helm:latest
  script:
    - helm upgrade --install persons-finder ./devops/helm/persons-finder
        -f staging-values.yaml
        --namespace persons-finder
        --create-namespace
        --wait
        --timeout 5m
  environment:
    name: staging
    url: https://api-staging.example.com
  only:
    - develop

deploy:production:
  stage: deploy
  image: alpine/helm:latest
  script:
    - helm upgrade --install persons-finder ./devops/helm/persons-finder
        -f production-values.yaml
        --namespace persons-finder
        --create-namespace
        --wait
        --timeout 5m
  environment:
    name: production
    url: https://api.example.com
  only:
    - main
  when: manual
```

### GitHub Actions Example

```yaml
# .github/workflows/deploy.yml
name: Deploy to EKS

on:
  push:
    branches:
      - main

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v3
      
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v2
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ap-southeast-2
      
      - name: Update kubeconfig
        run: |
          aws eks update-kubeconfig --name my-cluster --region ap-southeast-2
      
      - name: Install Helm
        uses: azure/setup-helm@v3
        with:
          version: '3.12.0'
      
      - name: Deploy with Helm
        run: |
          helm upgrade --install persons-finder ./devops/helm/persons-finder \
            -f production-values.yaml \
            --namespace persons-finder \
            --create-namespace \
            --wait \
            --timeout 5m
```


## Complete Deployment Checklist

Use this checklist to ensure a successful deployment:

### Pre-Deployment

- [ ] EKS cluster is running and accessible
- [ ] `kubectl` is configured and can access the cluster
- [ ] NGINX Ingress Controller is installed
- [ ] cert-manager is installed (if using TLS)
- [ ] ClusterIssuer is created (if using TLS)
- [ ] DNS is configured and resolving correctly
- [ ] Helm chart values file is prepared
- [ ] Image is built and pushed to ECR
- [ ] Secrets are created (if needed)

### Deployment

- [ ] Run `helm lint` to validate chart
- [ ] Run `helm template` to preview resources
- [ ] Deploy with `helm install` or `helm upgrade`
- [ ] Wait for pods to be ready
- [ ] Verify certificate is issued (if using TLS)
- [ ] Check pod logs for errors

### Post-Deployment Verification

- [ ] Pods are running: `kubectl get pods -n persons-finder`
- [ ] Service is created: `kubectl get service -n persons-finder`
- [ ] Ingress is created: `kubectl get ingress -n persons-finder`
- [ ] Certificate is ready: `kubectl get certificate -n persons-finder`
- [ ] Health check passes: `curl https://api.example.com/actuator/health`
- [ ] Swagger UI is accessible: `curl https://api.example.com/swagger-ui.html`
- [ ] OpenAPI spec is available: `curl https://api.example.com/v3/api-docs`
- [ ] API endpoints work: `curl https://api.example.com/api/v1/persons`
- [ ] Basic auth works (if enabled): `curl -u admin:password https://api.example.com/swagger-ui.html`
- [ ] CORS headers are correct: Test OPTIONS request
- [ ] HTTPS redirect works: `curl -I http://api.example.com`

### Security Verification

- [ ] Basic authentication is enabled (production)
- [ ] Strong password is used
- [ ] HTTPS is enforced
- [ ] CORS is restricted to specific domains
- [ ] Network policies are enabled (if applicable)
- [ ] Pod security context is configured
- [ ] Resource limits are set
- [ ] IP whitelist is configured (if applicable)

### Monitoring Setup

- [ ] Prometheus metrics are being scraped
- [ ] Logs are being collected
- [ ] Alerts are configured
- [ ] Dashboard is created (if applicable)

## Reference

### Helm Values Reference

Complete list of configurable values:

```yaml
# Replica count
replicaCount: 2

# Image configuration
image:
  repository: string
  pullPolicy: IfNotPresent|Always|Never
  tag: string

# Service configuration
service:
  type: ClusterIP|LoadBalancer|NodePort
  port: number
  targetPort: number

# Swagger configuration
swagger:
  enabled: boolean
  title: string
  version: string
  description: string
  basicAuth:
    enabled: boolean
    username: string
    password: string  # htpasswd hash

# CORS configuration
cors:
  allowedOrigins: string  # Comma-separated list

# Ingress configuration
ingress:
  enabled: boolean
  className: string
  annotations: object
  hosts: array
  tls: array
  ipWhitelist: string
  certManager:
    clusterIssuer: string

# Resource limits
resources:
  limits:
    cpu: string
    memory: string
  requests:
    cpu: string
    memory: string

# Autoscaling
autoscaling:
  enabled: boolean
  minReplicas: number
  maxReplicas: number
  targetCPUUtilizationPercentage: number
  stabilizationWindowSeconds: number

# Health probes
probes:
  liveness: object
  readiness: object

# Network policy
networkPolicy:
  enabled: boolean
  policyTypes: array
  ingress: array
  egress: array

# Security context
podSecurityContext: object
securityContext: object

# Sidecar
sidecar:
  enabled: boolean
  upstreamUrl: string
  image: object
  resources: object
  port: number
```

### Common Commands Reference

```bash
# Installation
helm install <release-name> <chart-path> -f <values-file> -n <namespace> --create-namespace

# Upgrade
helm upgrade <release-name> <chart-path> -f <values-file> -n <namespace>

# Rollback
helm rollback <release-name> <revision> -n <namespace>

# Uninstall
helm uninstall <release-name> -n <namespace>

# Status
helm status <release-name> -n <namespace>

# History
helm history <release-name> -n <namespace>

# Get values
helm get values <release-name> -n <namespace>

# Dry run
helm install <release-name> <chart-path> -f <values-file> --dry-run

# Template
helm template <release-name> <chart-path> -f <values-file>

# Lint
helm lint <chart-path> -f <values-file>
```

### Useful kubectl Commands

```bash
# Pods
kubectl get pods -n <namespace>
kubectl describe pod <pod-name> -n <namespace>
kubectl logs <pod-name> -n <namespace>
kubectl logs <pod-name> -n <namespace> -f
kubectl logs <pod-name> -n <namespace> --previous

# Services
kubectl get service -n <namespace>
kubectl describe service <service-name> -n <namespace>

# Ingress
kubectl get ingress -n <namespace>
kubectl describe ingress <ingress-name> -n <namespace>

# Certificates
kubectl get certificate -n <namespace>
kubectl describe certificate <cert-name> -n <namespace>

# Secrets
kubectl get secret -n <namespace>
kubectl describe secret <secret-name> -n <namespace>

# Events
kubectl get events -n <namespace> --sort-by='.lastTimestamp'

# Port forward
kubectl port-forward -n <namespace> service/<service-name> <local-port>:<service-port>

# Execute command in pod
kubectl exec -it <pod-name> -n <namespace> -- /bin/sh

# Resource usage
kubectl top pods -n <namespace>
kubectl top nodes
```

## Additional Resources

- [Helm Documentation](https://helm.sh/docs/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [NGINX Ingress Controller](https://kubernetes.github.io/ingress-nginx/)
- [cert-manager Documentation](https://cert-manager.io/docs/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [SpringDoc OpenAPI](https://springdoc.org/)
- [DNS Configuration Guide](./DNS_CONFIGURATION.md)

## Support

For issues or questions:

1. Check the [Troubleshooting](#troubleshooting) section
2. Review application logs: `kubectl logs -n persons-finder -l app=persons-finder`
3. Check Ingress Controller logs: `kubectl logs -n ingress-nginx -l app.kubernetes.io/name=ingress-nginx`
4. Check cert-manager logs: `kubectl logs -n cert-manager -l app=cert-manager`
5. Consult the [DNS Configuration Guide](./DNS_CONFIGURATION.md)
6. Contact your DevOps team

---

**Last Updated:** 2024
**Version:** 1.0.0
**Maintained by:** DevOps Team

