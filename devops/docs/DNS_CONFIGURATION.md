# DNS Configuration Guide for Swagger UI

This guide explains how to configure DNS to access the Persons Finder API Swagger UI through a custom domain.

## Overview

After deploying the Persons Finder application to AWS EKS with Ingress enabled, you need to configure DNS to point your custom domain to the NGINX Ingress Controller's LoadBalancer. This allows users to access the Swagger UI and API endpoints using a friendly domain name like `api.example.com` instead of the LoadBalancer's AWS-generated hostname.

## Prerequisites

- Persons Finder application deployed to AWS EKS
- NGINX Ingress Controller installed in the cluster
- Ingress resource created for the application
- Access to your DNS provider's management console
- A registered domain name

## Step 1: Get the NGINX Ingress Controller LoadBalancer Address

The NGINX Ingress Controller is exposed via an AWS Network Load Balancer (NLB). You need to find the external address of this LoadBalancer.

### Method 1: Using kubectl

```bash
# Get the LoadBalancer service for NGINX Ingress Controller
kubectl get service -n ingress-nginx ingress-nginx-controller

# Example output:
# NAME                       TYPE           CLUSTER-IP      EXTERNAL-IP                                                                     PORT(S)
# ingress-nginx-controller   LoadBalancer   10.100.200.50   a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6-1234567890.us-east-1.elb.amazonaws.com   80:30080/TCP,443:30443/TCP
```

The `EXTERNAL-IP` column shows the LoadBalancer's DNS name. Copy this value.

### Method 2: Using AWS CLI

```bash
# List all LoadBalancers in your region
aws elbv2 describe-load-balancers --region us-east-1 --query 'LoadBalancers[*].[LoadBalancerName,DNSName]' --output table

# Find the LoadBalancer associated with your EKS cluster
# Look for a name containing your cluster name or "ingress-nginx"
```

### Method 3: Using AWS Console

1. Open the AWS Management Console
2. Navigate to **EC2** > **Load Balancers**
3. Find the LoadBalancer with tags matching your EKS cluster
4. Copy the **DNS name** from the Description tab


## Step 2: Create DNS Records

You need to create a DNS record that points your custom domain to the LoadBalancer address. There are two types of records you can use:

### Option A: CNAME Record (Recommended)

A CNAME record creates an alias from your domain to the LoadBalancer's DNS name. This is the recommended approach because:
- The LoadBalancer's IP address may change, but the DNS name remains stable
- AWS manages the IP resolution automatically
- Works across multiple Availability Zones

**Important:** CNAME records cannot be used for apex/root domains (e.g., `example.com`). Use a subdomain like `api.example.com` or use an ALIAS record (see Option C).

#### Steps to Create CNAME Record:

1. Log in to your DNS provider's management console (e.g., Route 53, Cloudflare, GoDaddy, Namecheap)
2. Navigate to the DNS management section for your domain
3. Create a new CNAME record with the following values:
   - **Name/Host:** `api` (for `api.example.com`) or your desired subdomain
   - **Type:** `CNAME`
   - **Value/Target:** The LoadBalancer DNS name (e.g., `a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6-1234567890.us-east-1.elb.amazonaws.com`)
   - **TTL:** `300` (5 minutes) or your preferred value
4. Save the record

#### Example for Route 53:

```bash
# Using AWS CLI to create CNAME record in Route 53
aws route53 change-resource-record-sets \
  --hosted-zone-id Z1234567890ABC \
  --change-batch '{
    "Changes": [{
      "Action": "CREATE",
      "ResourceRecordSet": {
        "Name": "api.example.com",
        "Type": "CNAME",
        "TTL": 300,
        "ResourceRecords": [{
          "Value": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6-1234567890.us-east-1.elb.amazonaws.com"
        }]
      }
    }]
  }'
```

### Option B: A Record

An A record points your domain directly to an IP address. This requires resolving the LoadBalancer's DNS name to its IP addresses.

**Note:** AWS LoadBalancers can have multiple IP addresses that may change. This option is less reliable than CNAME.

#### Steps to Create A Record:

1. Resolve the LoadBalancer DNS name to IP addresses:
   ```bash
   nslookup a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6-1234567890.us-east-1.elb.amazonaws.com
   # or
   dig a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6-1234567890.us-east-1.elb.amazonaws.com
   ```

2. Log in to your DNS provider's management console
3. Create a new A record with the following values:
   - **Name/Host:** `api` (for `api.example.com`) or your desired subdomain
   - **Type:** `A`
   - **Value/Target:** The IP address(es) from step 1
   - **TTL:** `300` (5 minutes)
4. Save the record

**Warning:** If the LoadBalancer's IP changes, you'll need to update the A record manually.


### Option C: ALIAS Record (Route 53 Only)

If you're using AWS Route 53 and want to use an apex/root domain (e.g., `example.com`), you can use an ALIAS record. This is a Route 53-specific feature that works like a CNAME but can be used for apex domains.

#### Steps to Create ALIAS Record:

1. Log in to AWS Route 53 console
2. Select your hosted zone
3. Click **Create Record**
4. Configure the record:
   - **Record name:** Leave blank for apex domain or enter subdomain
   - **Record type:** `A - IPv4 address`
   - **Alias:** Toggle ON
   - **Route traffic to:** Select "Alias to Network Load Balancer"
   - **Region:** Select your LoadBalancer's region
   - **LoadBalancer:** Select your NGINX Ingress LoadBalancer from the dropdown
   - **Routing policy:** Simple routing
   - **Evaluate target health:** Yes (recommended)
5. Click **Create records**

#### Example using AWS CLI:

```bash
# Create ALIAS record for apex domain
aws route53 change-resource-record-sets \
  --hosted-zone-id Z1234567890ABC \
  --change-batch '{
    "Changes": [{
      "Action": "CREATE",
      "ResourceRecordSet": {
        "Name": "example.com",
        "Type": "A",
        "AliasTarget": {
          "HostedZoneId": "Z215JYRZR1TBD5",
          "DNSName": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6-1234567890.us-east-1.elb.amazonaws.com",
          "EvaluateTargetHealth": true
        }
      }
    }]
  }'
```

**Note:** The `HostedZoneId` in the `AliasTarget` is the canonical hosted zone ID for Network Load Balancers in your region, not your domain's hosted zone ID. See [AWS documentation](https://docs.aws.amazon.com/general/latest/gr/elb.html) for the correct value for your region.

## Step 3: Verify DNS Resolution

After creating the DNS record, you need to verify that it's resolving correctly. DNS propagation can take anywhere from a few minutes to 48 hours, depending on TTL values and DNS provider.

### Method 1: Using nslookup

```bash
# Check if your domain resolves
nslookup api.example.com

# Expected output:
# Server:         8.8.8.8
# Address:        8.8.8.8#53
#
# Non-authoritative answer:
# api.example.com canonical name = a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6-1234567890.us-east-1.elb.amazonaws.com.
# Name:   a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6-1234567890.us-east-1.elb.amazonaws.com
# Address: 52.1.2.3
# Address: 52.1.2.4
```

### Method 2: Using dig

```bash
# Check DNS resolution with detailed information
dig api.example.com

# Check CNAME record specifically
dig api.example.com CNAME

# Check with a specific DNS server (e.g., Google DNS)
dig @8.8.8.8 api.example.com
```

### Method 3: Using host

```bash
# Simple DNS lookup
host api.example.com

# Expected output:
# api.example.com is an alias for a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6-1234567890.us-east-1.elb.amazonaws.com.
# a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6-1234567890.us-east-1.elb.amazonaws.com has address 52.1.2.3
# a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6-1234567890.us-east-1.elb.amazonaws.com has address 52.1.2.4
```


### Method 4: Using curl

Once DNS is resolving, test HTTP/HTTPS connectivity:

```bash
# Test HTTP connection (if not redirected to HTTPS)
curl -I http://api.example.com/swagger-ui.html

# Test HTTPS connection
curl -I https://api.example.com/swagger-ui.html

# Expected output (successful):
# HTTP/2 200
# server: nginx
# date: Mon, 01 Jan 2024 00:00:00 GMT
# content-type: text/html
# ...
```

### Method 5: Using Online DNS Checkers

Use online tools to check DNS propagation globally:
- [whatsmydns.net](https://www.whatsmydns.net/)
- [dnschecker.org](https://dnschecker.org/)
- [dns-lookup.com](https://www.dns-lookup.com/)

Enter your domain name and check if it resolves to the LoadBalancer address across different geographic locations.

## Step 4: Update Helm Configuration

After DNS is configured, update your Helm values to use the custom domain:

```yaml
# values.yaml or production-values.yaml
ingress:
  enabled: true
  className: "nginx"
  hosts:
    - host: api.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: persons-finder-tls
      hosts:
        - api.example.com
```

Deploy the updated configuration:

```bash
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder
```

## Step 5: Verify Application Access

After DNS propagation is complete and Helm is updated, verify that you can access the application:

### Test Swagger UI Access

```bash
# Access Swagger UI
curl https://api.example.com/swagger-ui.html

# Or open in browser
open https://api.example.com/swagger-ui.html
```

### Test OpenAPI Specification

```bash
# Get OpenAPI spec
curl https://api.example.com/v3/api-docs | jq .

# Verify it returns valid JSON with API documentation
```

### Test API Endpoints

```bash
# Test API endpoint
curl https://api.example.com/api/v1/persons

# Test with authentication if basic auth is enabled
curl -u admin:password https://api.example.com/swagger-ui.html
```

## Troubleshooting

### DNS Not Resolving

**Problem:** `nslookup` or `dig` returns `NXDOMAIN` or no results.

**Solutions:**
1. Wait for DNS propagation (can take up to 48 hours)
2. Check that the DNS record was created correctly in your DNS provider
3. Verify the record type (CNAME vs A) is correct
4. Check for typos in the domain name or LoadBalancer address
5. Try flushing your local DNS cache:
   ```bash
   # macOS
   sudo dscacheutil -flushcache; sudo killall -HUP mDNSResponder
   
   # Linux
   sudo systemd-resolve --flush-caches
   
   # Windows
   ipconfig /flushdns
   ```


### DNS Resolves but Application Not Accessible

**Problem:** DNS resolves correctly, but accessing the domain returns connection errors or timeouts.

**Solutions:**
1. Verify the Ingress resource is created:
   ```bash
   kubectl get ingress -n persons-finder
   kubectl describe ingress persons-finder -n persons-finder
   ```

2. Check that the Ingress has the correct host configured:
   ```bash
   kubectl get ingress persons-finder -n persons-finder -o yaml | grep -A 5 "host:"
   ```

3. Verify the NGINX Ingress Controller is running:
   ```bash
   kubectl get pods -n ingress-nginx
   kubectl logs -n ingress-nginx -l app.kubernetes.io/name=ingress-nginx
   ```

4. Check the application pods are running:
   ```bash
   kubectl get pods -n persons-finder
   kubectl logs -n persons-finder -l app=persons-finder
   ```

5. Verify the Service is correctly configured:
   ```bash
   kubectl get service persons-finder -n persons-finder
   ```

### TLS Certificate Issues

**Problem:** HTTPS connection fails with certificate errors.

**Solutions:**
1. Check if cert-manager is installed:
   ```bash
   kubectl get pods -n cert-manager
   ```

2. Verify the Certificate resource:
   ```bash
   kubectl get certificate -n persons-finder
   kubectl describe certificate persons-finder-tls -n persons-finder
   ```

3. Check cert-manager logs:
   ```bash
   kubectl logs -n cert-manager -l app=cert-manager
   ```

4. Verify the ClusterIssuer exists:
   ```bash
   kubectl get clusterissuer
   kubectl describe clusterissuer letsencrypt-prod
   ```

5. Check the Secret containing the TLS certificate:
   ```bash
   kubectl get secret persons-finder-tls -n persons-finder
   kubectl describe secret persons-finder-tls -n persons-finder
   ```

### 404 Not Found Errors

**Problem:** Domain resolves and connects, but returns 404 errors.

**Solutions:**
1. Verify the Ingress paths are correct:
   ```bash
   kubectl get ingress persons-finder -n persons-finder -o yaml
   ```

2. Check that the paths in Ingress match the application routes:
   - `/swagger-ui.html` should route to Swagger UI
   - `/v3/api-docs` should route to OpenAPI spec
   - `/api/v1/persons` should route to API endpoints

3. Verify the Service selector matches the Pod labels:
   ```bash
   kubectl get service persons-finder -n persons-finder -o yaml | grep -A 3 "selector:"
   kubectl get pods -n persons-finder --show-labels
   ```

### 502 Bad Gateway Errors

**Problem:** Ingress returns 502 Bad Gateway errors.

**Solutions:**
1. Check if the application pods are ready:
   ```bash
   kubectl get pods -n persons-finder
   ```

2. Verify the readiness probe is passing:
   ```bash
   kubectl describe pod <pod-name> -n persons-finder | grep -A 10 "Readiness:"
   ```

3. Check application logs for startup errors:
   ```bash
   kubectl logs -n persons-finder -l app=persons-finder --tail=100
   ```

4. Verify the Service port matches the container port:
   ```bash
   kubectl get service persons-finder -n persons-finder -o yaml | grep -A 5 "ports:"
   kubectl get pod <pod-name> -n persons-finder -o yaml | grep -A 5 "containerPort:"
   ```


### LoadBalancer Address Changed

**Problem:** DNS was working but suddenly stopped resolving or points to wrong IP.

**Solutions:**
1. Get the current LoadBalancer address:
   ```bash
   kubectl get service -n ingress-nginx ingress-nginx-controller
   ```

2. If using A records, update them with the new IP addresses
3. If using CNAME records, they should automatically resolve to the new IPs (no action needed)
4. This is why CNAME records are recommended over A records

## DNS Provider-Specific Instructions

### AWS Route 53

1. Open Route 53 console: https://console.aws.amazon.com/route53/
2. Click **Hosted zones**
3. Select your domain
4. Click **Create record**
5. Follow the steps in Option A (CNAME) or Option C (ALIAS) above

### Cloudflare

1. Log in to Cloudflare dashboard
2. Select your domain
3. Go to **DNS** tab
4. Click **Add record**
5. Configure:
   - **Type:** CNAME
   - **Name:** api (or your subdomain)
   - **Target:** LoadBalancer DNS name
   - **Proxy status:** DNS only (gray cloud) - **Important:** Do not proxy through Cloudflare for EKS LoadBalancers
   - **TTL:** Auto
6. Click **Save**

**Note:** If you enable Cloudflare proxy (orange cloud), you may need to configure additional settings for WebSocket support and SSL/TLS mode.

### GoDaddy

1. Log in to GoDaddy account
2. Go to **My Products** > **DNS**
3. Click **Add** under Records
4. Configure:
   - **Type:** CNAME
   - **Name:** api
   - **Value:** LoadBalancer DNS name
   - **TTL:** 600 seconds (or default)
5. Click **Save**

### Namecheap

1. Log in to Namecheap account
2. Go to **Domain List** > Select domain > **Manage**
3. Go to **Advanced DNS** tab
4. Click **Add New Record**
5. Configure:
   - **Type:** CNAME Record
   - **Host:** api
   - **Value:** LoadBalancer DNS name
   - **TTL:** Automatic
6. Click the checkmark to save

### Google Domains

1. Log in to Google Domains
2. Select your domain
3. Go to **DNS** tab
4. Scroll to **Custom resource records**
5. Configure:
   - **Name:** api
   - **Type:** CNAME
   - **TTL:** 1H (or your preference)
   - **Data:** LoadBalancer DNS name
6. Click **Add**

## Best Practices

### Use CNAME Records

- Always prefer CNAME records over A records for LoadBalancers
- LoadBalancer IPs can change, but DNS names remain stable
- CNAME records automatically resolve to current IPs

### Set Appropriate TTL Values

- **Development/Staging:** Use low TTL (300 seconds / 5 minutes) for faster updates
- **Production:** Use moderate TTL (3600 seconds / 1 hour) for balance between performance and flexibility
- Lower TTL = faster DNS updates but more DNS queries
- Higher TTL = fewer DNS queries but slower updates

### Use Subdomains

- Use subdomains like `api.example.com` instead of apex domain `example.com`
- Subdomains work with CNAME records
- Easier to manage multiple services (api.example.com, docs.example.com, etc.)

### Enable HTTPS

- Always use HTTPS in production
- Configure cert-manager for automatic TLS certificate management
- Use Let's Encrypt for free SSL/TLS certificates
- Redirect HTTP to HTTPS via Ingress annotations

### Monitor DNS Health

- Set up monitoring for DNS resolution
- Alert on DNS resolution failures
- Monitor certificate expiration dates
- Test DNS from multiple geographic locations


## Security Considerations

### Use HTTPS Only

- Never expose Swagger UI over plain HTTP in production
- Configure TLS termination at the Ingress level
- Use strong TLS versions (TLS 1.2 or higher)

### Restrict Access

- Consider enabling basic authentication for Swagger UI
- Use IP whitelisting to restrict access to corporate networks
- Implement rate limiting to prevent abuse

### DNS Security

- Enable DNSSEC if your DNS provider supports it
- Use DNS provider's security features (e.g., Cloudflare's security settings)
- Monitor for DNS hijacking or unauthorized changes

## Example: Complete Setup

Here's a complete example of setting up DNS for `api.example.com`:

### 1. Get LoadBalancer Address

```bash
$ kubectl get service -n ingress-nginx ingress-nginx-controller
NAME                       TYPE           EXTERNAL-IP
ingress-nginx-controller   LoadBalancer   a1b2c3d4-us-east-1.elb.amazonaws.com
```

### 2. Create CNAME Record in Route 53

```bash
aws route53 change-resource-record-sets \
  --hosted-zone-id Z1234567890ABC \
  --change-batch '{
    "Changes": [{
      "Action": "CREATE",
      "ResourceRecordSet": {
        "Name": "api.example.com",
        "Type": "CNAME",
        "TTL": 300,
        "ResourceRecords": [{
          "Value": "a1b2c3d4-us-east-1.elb.amazonaws.com"
        }]
      }
    }]
  }'
```

### 3. Verify DNS Resolution

```bash
$ nslookup api.example.com
Server:         8.8.8.8
Address:        8.8.8.8#53

Non-authoritative answer:
api.example.com canonical name = a1b2c3d4-us-east-1.elb.amazonaws.com.
Name:   a1b2c3d4-us-east-1.elb.amazonaws.com
Address: 52.1.2.3
```

### 4. Update Helm Values

```yaml
# production-values.yaml
ingress:
  enabled: true
  className: "nginx"
  annotations:
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
  hosts:
    - host: api.example.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: persons-finder-tls
      hosts:
        - api.example.com

swagger:
  enabled: true
  basicAuth:
    enabled: true
    username: admin
    password: "$apr1$xyz..." # htpasswd generated hash
```

### 5. Deploy

```bash
helm upgrade persons-finder ./devops/helm/persons-finder \
  -f production-values.yaml \
  --namespace persons-finder
```

### 6. Verify Access

```bash
# Wait for certificate to be issued (may take 1-2 minutes)
kubectl get certificate -n persons-finder -w

# Test access
curl -u admin:password https://api.example.com/swagger-ui.html
```

## Additional Resources

- [AWS ELB Documentation](https://docs.aws.amazon.com/elasticloadbalancing/)
- [NGINX Ingress Controller Documentation](https://kubernetes.github.io/ingress-nginx/)
- [cert-manager Documentation](https://cert-manager.io/docs/)
- [DNS Best Practices](https://www.cloudflare.com/learning/dns/dns-records/)
- [Let's Encrypt Documentation](https://letsencrypt.org/docs/)

## Support

If you encounter issues not covered in this guide:

1. Check the application logs:
   ```bash
   kubectl logs -n persons-finder -l app=persons-finder
   ```

2. Check the Ingress Controller logs:
   ```bash
   kubectl logs -n ingress-nginx -l app.kubernetes.io/name=ingress-nginx
   ```

3. Check cert-manager logs (if using TLS):
   ```bash
   kubectl logs -n cert-manager -l app=cert-manager
   ```

4. Consult the troubleshooting section above
5. Contact your DevOps team or system administrator

---

**Last Updated:** 2024
**Version:** 1.0.0
