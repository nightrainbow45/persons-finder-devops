#!/bin/bash

# Helm Chart Template Verification Script
# This script verifies that the Helm chart templates render correctly with various configurations

set -e

CHART_PATH="devops/helm/persons-finder"
RELEASE_NAME="persons-finder"

echo "=========================================="
echo "Helm Chart Template Verification"
echo "=========================================="
echo ""

# Test 1: Basic template rendering
echo "Test 1: Basic template rendering (default values)"
helm template $RELEASE_NAME $CHART_PATH > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✓ PASS: Basic template renders successfully"
else
    echo "✗ FAIL: Basic template rendering failed"
    exit 1
fi
echo ""

# Test 2: Swagger enabled - verify environment variables
echo "Test 2: Swagger enabled - verify environment variables"
OUTPUT=$(helm template $RELEASE_NAME $CHART_PATH --set swagger.enabled=true)
if echo "$OUTPUT" | grep -q "SWAGGER_ENABLED" && \
   echo "$OUTPUT" | grep -q "API_TITLE" && \
   echo "$OUTPUT" | grep -q "API_VERSION" && \
   echo "$OUTPUT" | grep -q "API_DESCRIPTION"; then
    echo "✓ PASS: All Swagger environment variables present"
else
    echo "✗ FAIL: Missing Swagger environment variables"
    exit 1
fi
echo ""

# Test 3: Swagger disabled - verify environment variables
echo "Test 3: Swagger disabled - verify environment variables"
OUTPUT=$(helm template $RELEASE_NAME $CHART_PATH --set swagger.enabled=false)
if echo "$OUTPUT" | grep -q 'SWAGGER_ENABLED' && \
   echo "$OUTPUT" | grep -q 'value: "false"' && \
   ! echo "$OUTPUT" | grep -q "API_TITLE"; then
    echo "✓ PASS: Swagger disabled correctly (no API_TITLE, API_VERSION, API_DESCRIPTION)"
else
    echo "✗ FAIL: Swagger disabled configuration incorrect"
    exit 1
fi
echo ""

# Test 4: CORS configuration
echo "Test 4: CORS configuration"
OUTPUT=$(helm template $RELEASE_NAME $CHART_PATH --set 'cors.allowedOrigins=https://example.com')
if echo "$OUTPUT" | grep -q 'CORS_ALLOWED_ORIGINS' && \
   echo "$OUTPUT" | grep -q 'https://example.com'; then
    echo "✓ PASS: CORS environment variable set correctly"
else
    echo "✗ FAIL: CORS environment variable not set correctly"
    exit 1
fi
echo ""

# Test 5: Ingress enabled without basic auth
echo "Test 5: Ingress enabled without basic auth"
OUTPUT=$(helm template $RELEASE_NAME $CHART_PATH \
    --set ingress.enabled=true \
    --set 'ingress.hosts[0].host=api.example.com' \
    --set 'ingress.hosts[0].paths[0].path=/' \
    --set 'ingress.hosts[0].paths[0].pathType=Prefix')
if echo "$OUTPUT" | grep -q "kind: Ingress" && \
   ! echo "$OUTPUT" | grep -q "configuration-snippet"; then
    echo "✓ PASS: Ingress created without basic auth"
else
    echo "✗ FAIL: Ingress configuration incorrect"
    exit 1
fi
echo ""

# Test 6: Ingress with basic auth enabled
echo "Test 6: Ingress with basic auth enabled"
OUTPUT=$(helm template $RELEASE_NAME $CHART_PATH \
    --set ingress.enabled=true \
    --set swagger.basicAuth.enabled=true \
    --set swagger.basicAuth.username=admin \
    --set 'swagger.basicAuth.password=$apr1$xyz' \
    --set 'ingress.hosts[0].host=api.example.com' \
    --set 'ingress.hosts[0].paths[0].path=/' \
    --set 'ingress.hosts[0].paths[0].pathType=Prefix')
if echo "$OUTPUT" | grep -q "configuration-snippet" && \
   echo "$OUTPUT" | grep -q "swagger-ui" && \
   echo "$OUTPUT" | grep -q "v3/api-docs" && \
   echo "$OUTPUT" | grep -q "auth_basic"; then
    echo "✓ PASS: Ingress with basic auth configured correctly"
else
    echo "✗ FAIL: Ingress basic auth configuration incorrect"
    exit 1
fi
echo ""

# Test 7: Basic auth secret created
echo "Test 7: Basic auth secret created when enabled"
OUTPUT=$(helm template $RELEASE_NAME $CHART_PATH \
    --set ingress.enabled=true \
    --set swagger.basicAuth.enabled=true \
    --set swagger.basicAuth.username=admin \
    --set 'swagger.basicAuth.password=$apr1$xyz')
if echo "$OUTPUT" | grep -q "persons-finder-basic-auth" && \
   echo "$OUTPUT" | grep -q "kind: Secret"; then
    echo "✓ PASS: Basic auth secret created"
else
    echo "✗ FAIL: Basic auth secret not created"
    exit 1
fi
echo ""

# Test 8: Basic auth secret NOT created when disabled
echo "Test 8: Basic auth secret NOT created when disabled"
OUTPUT=$(helm template $RELEASE_NAME $CHART_PATH \
    --set ingress.enabled=true \
    --set swagger.basicAuth.enabled=false)
if ! echo "$OUTPUT" | grep -q "persons-finder-basic-auth"; then
    echo "✓ PASS: Basic auth secret not created when disabled"
else
    echo "✗ FAIL: Basic auth secret created when it should not be"
    exit 1
fi
echo ""

# Test 9: TLS configuration
echo "Test 9: TLS configuration"
OUTPUT=$(helm template $RELEASE_NAME $CHART_PATH \
    --set ingress.enabled=true \
    --set 'ingress.tls[0].secretName=persons-finder-tls' \
    --set 'ingress.tls[0].hosts[0]=api.example.com' \
    --set 'ingress.hosts[0].host=api.example.com' \
    --set 'ingress.hosts[0].paths[0].path=/' \
    --set 'ingress.hosts[0].paths[0].pathType=Prefix')
if echo "$OUTPUT" | grep -q "cert-manager.io/cluster-issuer" && \
   echo "$OUTPUT" | grep -q "tls:" && \
   echo "$OUTPUT" | grep -q "persons-finder-tls"; then
    echo "✓ PASS: TLS configuration correct"
else
    echo "✗ FAIL: TLS configuration incorrect"
    exit 1
fi
echo ""

# Test 10: IP whitelist configuration
echo "Test 10: IP whitelist configuration"
OUTPUT=$(helm template $RELEASE_NAME $CHART_PATH \
    --set ingress.enabled=true \
    --set 'ingress.ipWhitelist=203.0.113.0/24' \
    --set 'ingress.hosts[0].host=api.example.com' \
    --set 'ingress.hosts[0].paths[0].path=/' \
    --set 'ingress.hosts[0].paths[0].pathType=Prefix')
if echo "$OUTPUT" | grep -q "whitelist-source-range" && \
   echo "$OUTPUT" | grep -q "203.0.113.0/24"; then
    echo "✓ PASS: IP whitelist configured correctly"
else
    echo "✗ FAIL: IP whitelist configuration incorrect"
    exit 1
fi
echo ""

# Test 11: Custom Swagger configuration values
echo "Test 11: Custom Swagger configuration values"
OUTPUT=$(helm template $RELEASE_NAME $CHART_PATH \
    --set swagger.enabled=true \
    --set swagger.title="Custom API Title" \
    --set swagger.version="2.0.0" \
    --set swagger.description="Custom Description")
if echo "$OUTPUT" | grep -q 'API_TITLE' && \
   echo "$OUTPUT" | grep -q 'Custom API Title' && \
   echo "$OUTPUT" | grep -q 'API_VERSION' && \
   echo "$OUTPUT" | grep -q '2.0.0' && \
   echo "$OUTPUT" | grep -q 'API_DESCRIPTION' && \
   echo "$OUTPUT" | grep -q 'Custom Description'; then
    echo "✓ PASS: Custom Swagger values applied correctly"
else
    echo "✗ FAIL: Custom Swagger values not applied correctly"
    exit 1
fi
echo ""

# Test 12: Ingress disabled - no Ingress resource created
echo "Test 12: Ingress disabled - no Ingress resource created"
OUTPUT=$(helm template $RELEASE_NAME $CHART_PATH --set ingress.enabled=false)
if ! echo "$OUTPUT" | grep -q "kind: Ingress"; then
    echo "✓ PASS: No Ingress resource when disabled"
else
    echo "✗ FAIL: Ingress resource created when disabled"
    exit 1
fi
echo ""

echo "=========================================="
echo "All tests passed! ✓"
echo "=========================================="
