# Implementation Plan: DevOps Production Deployment

## Overview

This implementation plan covers the complete DevOps production deployment for the Persons Finder Spring Boot application. The tasks include containerization with Docker multi-stage builds, Kubernetes orchestration with auto-scaling, CI/CD pipeline setup with security scanning, and PII protection architecture using sidecar pattern. All tasks build incrementally to create a production-ready deployment infrastructure.

## Tasks

- [x] 1. Create DevOps infrastructure folder structure
  - Create devops/ directory in repository root
  - Create devops/docker/ for Dockerfile and .dockerignore
  - Create devops/helm/persons-finder/ for Helm Chart
  - Create devops/helm/persons-finder/templates/ for K8s resource templates
  - Create devops/helm/persons-finder/charts/ for Chart dependencies
  - Create devops/terraform/ for infrastructure as code
  - Create devops/terraform/modules/ for reusable Terraform modules
  - Create devops/terraform/modules/iam/ for IAM and OIDC configuration
  - Create devops/terraform/modules/iam/policies/ for IAM policy files
  - Create devops/terraform/modules/iam/trust-policies/ for trust policy files
  - Create devops/terraform/modules/vpc/ for VPC module
  - Create devops/terraform/modules/eks/ for EKS module
  - Create devops/terraform/modules/ecr/ for ECR module
  - Create devops/terraform/modules/secrets-manager/ for Secrets Manager module
  - Create devops/terraform/environments/dev/ for dev environment
  - Create devops/terraform/environments/prod/ for prod environment
  - Create devops/scripts/ for deployment helper scripts
  - Create devops/ci/ for CI/CD pipeline configurations
  - Create devops/docs/ for deployment documentation
  - Create placeholder README.md files in each directory explaining its purpose
  - _Requirements: 8.1, 8.2, 8.5, 8.6, 8.7_

- [x] 1.1 Write unit tests for DevOps folder structure
  - Test all required directories exist
  - Test README.md files exist in key directories
  - Verify folder structure follows best practices
  - _Requirements: 8.1, 8.7_

- [x] 2. Create Docker multi-stage build configuration
  - Create devops/docker/Dockerfile with build stage using gradle:7.6-jdk11
  - Create devops/docker/.dockerignore to exclude unnecessary files
  - Configure runtime stage using eclipse-temurin:11-jre-alpine
  - Configure non-root user for security
  - Pin all base image versions to specific tags
  - Expose port 8080 for Spring Boot application
  - Add HEALTHCHECK instruction for container health monitoring
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7_

- [x] 2.1 Write property test for container image determinism
  - **Property 3: Container Image Determinism**
  - **Validates: Requirements 1.1, 1.2**
  - Build same source code multiple times and verify content hash consistency
  - _Requirements: 1.1, 1.2_

- [x] 2.2 Write unit tests for Dockerfile best practices
  - Test multi-stage build structure exists
  - Test non-root user configuration
  - Test base image version pinning
  - Test port 8080 exposure
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [x] 3. Create Helm Chart structure
  - Create devops/helm/persons-finder/Chart.yaml with metadata (name, version, appVersion, description)
  - Create devops/helm/persons-finder/values.yaml with default configuration parameters
  - Create devops/helm/persons-finder/values-dev.yaml for development environment
  - Create devops/helm/persons-finder/values-prod.yaml for production environment
  - Create devops/helm/persons-finder/templates/_helpers.tpl for template helper functions
  - Create devops/helm/persons-finder/templates/NOTES.txt for post-installation instructions
  - Create devops/helm/persons-finder/.helmignore to exclude unnecessary files
  - Create devops/helm/persons-finder/README.md with Chart documentation
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.7_

- [x] 3.1 Write unit tests for Helm Chart structure
  - Test Chart.yaml exists and has required fields
  - Test values files exist for different environments
  - Test templates directory structure
  - Validate Chart with helm lint
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.8_

- [x] 4. Create Kubernetes Secret template for API key management
  - Create devops/helm/persons-finder/templates/secret.yaml
  - Use Helm templating for conditional secret creation
  - Configure OPENAI_API_KEY from values or external secret
  - Add documentation for manual secret creation process
  - Support both inline secrets and external secret references
  - _Requirements: 2.1, 2.2, 2.5_

- [x] 4.1 Write unit tests for Secret template
  - Test Secret template renders correctly
  - Test OPENAI_API_KEY key exists in rendered output
  - Verify Secret type is Opaque
  - Test conditional rendering logic
  - _Requirements: 2.1, 2.5_

- [x] 5. Create Kubernetes Deployment template
  - Create devops/helm/persons-finder/templates/deployment.yaml
  - Parameterize replica count from values.yaml
  - Parameterize image repository and tag from values.yaml
  - Configure resource requests and limits from values.yaml
  - Configure environment variable injection from Secret
  - Parameterize readiness probe settings from values.yaml
  - Parameterize liveness probe settings from values.yaml
  - Configure rolling update strategy from values.yaml
  - Set security context to run as non-root user
  - Use Helm template helpers for labels and selectors
  - _Requirements: 2.2, 2.3, 3.1, 3.2, 3.3, 3.4, 3.5, 8.2, 9.4, 9.5_

- [x] 5.1 Write unit tests for Deployment template
  - Test template renders with default values
  - Test replica count parameterization
  - Test resource requests and limits parameterization
  - Test environment variable injection
  - Test probe configuration parameterization
  - Test security context configuration
  - Use helm template command for validation
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 8.2_

- [x] 6. Create Kubernetes Service template
  - Create devops/helm/persons-finder/templates/service.yaml
  - Parameterize service type from values.yaml (ClusterIP/LoadBalancer/NodePort)
  - Parameterize port configuration from values.yaml
  - Use Helm template helpers for selectors
  - Add service annotations support from values.yaml
  - _Requirements: 3.6, 8.2_

- [x] 6.1 Write unit tests for Service template
  - Test Service template renders correctly
  - Test service type parameterization
  - Test port mapping configuration
  - Test selector matches Deployment labels
  - _Requirements: 3.6, 8.2_

- [x] 7. Create Kubernetes Ingress template
  - Create devops/helm/persons-finder/templates/ingress.yaml
  - Add conditional rendering based on ingress.enabled in values.yaml
  - Parameterize host configuration from values.yaml
  - Parameterize path routing from values.yaml
  - Support multiple ingress controllers via annotations
  - Parameterize TLS configuration from values.yaml
  - _Requirements: 3.7, 8.2_

- [x] 7.1 Write unit tests for Ingress template
  - Test Ingress renders when enabled
  - Test Ingress is not rendered when disabled
  - Test host and path parameterization
  - Test TLS configuration
  - _Requirements: 3.7, 8.2_

- [x] 8. Create Kubernetes HorizontalPodAutoscaler template
  - Create devops/helm/persons-finder/templates/hpa.yaml
  - Add conditional rendering based on autoscaling.enabled in values.yaml
  - Parameterize minReplicas and maxReplicas from values.yaml
  - Parameterize CPU target utilization from values.yaml
  - Configure stabilization windows from values.yaml
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 8.2_

- [x] 8.1 Write unit tests for HPA template
  - Test HPA renders when autoscaling is enabled
  - Test HPA is not rendered when disabled
  - Test minReplicas and maxReplicas parameterization
  - Test CPU target utilization configuration
  - Test stabilization windows
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 8.2_

- [x] 9. Checkpoint - Verify Helm Chart
  - Run helm lint on the Chart
  - Run helm template to verify all templates render correctly
  - Test with different values files (dev, prod)
  - Validate rendered YAML with kubeval
  - Ensure all tests pass, ask the user if questions arise

- [x] 10. Implement PII redaction sidecar container
    - Create new Kotlin module for PII redaction service
    - Implement HTTP proxy server that intercepts outbound LLM requests
    - Implement PII detection using regex patterns for names and coordinates
    - Implement redaction/tokenization logic with reversible mapping
    - Add configuration for redaction rules and PII patterns
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

  - [x] 10.1 Implement audit logging for PII redaction
    - Create audit log data model (timestamp, request_id, pii_detected, redactions_applied, destination)
    - Implement audit log writer to stdout in JSON format
    - Log all external API calls with PII detection results
    - _Requirements: 5.5_

  - [x] 10.2 Write property test for PII redaction completeness
    - **Property 1: PII Redaction Completeness**
    - **Validates: Requirements 5.3**
    - Generate random requests with various PII types
    - Verify all PII is redacted before sending to external services
    - Verify redaction is reversible
    - _Requirements: 5.3_

  - [x] 10.3 Write property test for audit log completeness
    - **Property 2: Audit Log Completeness**
    - **Validates: Requirements 5.5**
    - Generate random sequences of external API calls
    - Verify each call has corresponding audit log entry
    - Verify log entries contain all required fields
    - Verify chronological ordering
    - _Requirements: 5.5_

  - [x] 10.4 Write unit tests for PII redaction service
    - Test regex pattern matching for person names
    - Test coordinate redaction
    - Test tokenization and de-tokenization
    - Test error handling for redaction failures
    - _Requirements: 5.2, 5.3_

- [x] 11. Update Deployment template with PII sidecar
  - Add sidecar container definition to devops/helm/persons-finder/templates/deployment.yaml
  - Parameterize sidecar image from values.yaml
  - Configure sidecar to listen on localhost proxy port
  - Update main application container to route LLM requests through sidecar
  - Configure shared volume for audit logs (if needed)
  - Add resource requests and limits for sidecar from values.yaml
  - Make sidecar optional via values.yaml flag
  - _Requirements: 5.4, 8.2_

- [x] 11.1 Write unit tests for sidecar configuration
  - Test sidecar container renders when enabled
  - Test sidecar is not rendered when disabled
  - Test sidecar resource configuration
  - Test localhost communication setup
  - _Requirements: 5.4, 8.2_

- [x] 12. Create GitHub Actions CI/CD workflow
  - [x] 12.1 Create workflow file structure
    - Create devops/ci/ci-cd.yml (to be copied to .github/workflows/)
    - Create .github/workflows/ directory if not exists
    - Copy devops/ci/ci-cd.yml to .github/workflows/ci-cd.yml
    - Configure trigger on push to main branch
    - Set up job for build, test, scan, and deploy stages
    - _Requirements: 6.7_

  - [x] 12.2 Implement build and test stage
    - Add checkout action
    - Set up Java 11 environment
    - Configure Gradle caching
    - Run Gradle build with tests
    - Upload test results as artifacts
    - _Requirements: 6.1, 6.2_

  - [x] 12.3 Implement AWS authentication stage
    - Configure AWS credentials using OIDC
    - Use aws-actions/configure-aws-credentials action
    - Assume IAM role for GitHub Actions
    - Verify AWS authentication
    - _Requirements: 6.3_

  - [x] 12.4 Implement Docker build stage
    - Set up Docker Buildx
    - Login to Amazon ECR using OIDC credentials
    - Build Docker image using devops/docker/Dockerfile
    - Tag image with commit SHA and latest
    - _Requirements: 6.3_

  - [x] 12.5 Implement security scanning stage
    - Add Trivy security scanner action
    - Scan Docker image for vulnerabilities
    - Configure to fail on HIGH or CRITICAL severity
    - Generate and upload security scan report
    - _Requirements: 6.4, 6.5, 7.1, 7.2, 7.3, 7.4, 7.5_

  - [x] 12.6 Implement container registry push stage
    - Configure container registry authentication using OIDC
    - Push Docker image with version tags to Amazon ECR
    - Push latest tag on successful build
    - _Requirements: 6.6_

  - [x] 12.7 Write unit tests for CI/CD workflow
    - Test workflow file YAML structure
    - Test all required stages are present
    - Test trigger configuration
    - Test security scanning is configured
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_

- [x] 13. Create Terraform configurations for AWS infrastructure
  - [x] 13.1 Create Terraform backend configuration
    - Create devops/terraform/backend.tf for S3 backend configuration
    - Create devops/terraform/versions.tf for Terraform and provider versions
    - Create devops/terraform/variables.tf for global variables
    - Create devops/terraform/outputs.tf for global outputs
    - _Requirements: 8.6_

  - [x] 13.2 Create IAM and OIDC module
    - Create devops/terraform/modules/iam/main.tf for IAM resources
    - Create devops/terraform/modules/iam/oidc.tf for GitHub OIDC Provider
    - Create devops/terraform/modules/iam/roles.tf for IAM roles (github-actions, eks-admin, eks-developer)
    - Create devops/terraform/modules/iam/variables.tf for IAM variables
    - Create devops/terraform/modules/iam/outputs.tf for IAM outputs
    - Create devops/terraform/modules/iam/policies/ecr-push.json for ECR push policy
    - Create devops/terraform/modules/iam/policies/eks-access.json for EKS access policy
    - Create devops/terraform/modules/iam/policies/deployer.json for deployer policy
    - Create devops/terraform/modules/iam/trust-policies/github-oidc.json for GitHub OIDC trust policy
    - Create devops/terraform/modules/iam/trust-policies/eks-nodes.json for EKS nodes trust policy
    - _Requirements: 8.6_

  - [x] 13.3 Create VPC module
    - Create devops/terraform/modules/vpc/main.tf for VPC resources
    - Create devops/terraform/modules/vpc/security-groups.tf for security groups
    - Create devops/terraform/modules/vpc/variables.tf for VPC variables
    - Create devops/terraform/modules/vpc/outputs.tf for VPC outputs
    - Configure public and private subnets across multiple AZs
    - Configure NAT gateways and internet gateway
    - _Requirements: 8.6_

  - [x] 13.4 Create EKS module
    - Create devops/terraform/modules/eks/main.tf for EKS cluster
    - Create devops/terraform/modules/eks/aws-auth.tf for aws-auth ConfigMap
    - Create devops/terraform/modules/eks/variables.tf for EKS variables
    - Create devops/terraform/modules/eks/outputs.tf for EKS outputs
    - Configure EKS cluster with managed node groups
    - Configure IAM roles and policies for EKS
    - Configure cluster add-ons (VPC CNI, CoreDNS, kube-proxy)
    - Map IAM roles to Kubernetes RBAC groups
    - _Requirements: 8.6_

  - [x] 13.5 Create ECR module
    - Create devops/terraform/modules/ecr/main.tf for ECR repository
    - Create devops/terraform/modules/ecr/variables.tf for ECR variables
    - Create devops/terraform/modules/ecr/outputs.tf for ECR outputs
    - Configure image scanning on push
    - Configure lifecycle policies for image retention
    - Configure repository access policies
    - _Requirements: 8.6_

  - [x] 13.6 Create Secrets Manager module
    - Create devops/terraform/modules/secrets-manager/main.tf for secrets
    - Create devops/terraform/modules/secrets-manager/variables.tf for secrets variables
    - Create devops/terraform/modules/secrets-manager/outputs.tf for secrets outputs
    - Configure secret rotation policies
    - Configure KMS encryption for secrets
    - _Requirements: 8.6_

  - [x] 13.7 Create environment-specific configurations
    - Create devops/terraform/environments/dev/main.tf for dev environment
    - Create devops/terraform/environments/dev/terraform.tfvars for dev variables
    - Create devops/terraform/environments/prod/main.tf for prod environment
    - Create devops/terraform/environments/prod/terraform.tfvars for prod variables
    - Configure environment-specific settings (instance types, node counts)
    - Compose all modules (IAM, VPC, EKS, ECR, Secrets Manager)
    - _Requirements: 8.6_

  - [x] 13.8 Create Terraform documentation
    - Create devops/terraform/README.md with usage instructions
    - Document prerequisites (AWS CLI, Terraform, kubectl)
    - Document deployment steps for each environment
    - Document variable configuration and customization
    - Document outputs and how to use them
    - _Requirements: 8.6_

- [x] 14. Create Kubernetes RBAC and Security resources
  - [x] 14.1 Create ServiceAccount template
    - Create devops/helm/persons-finder/templates/serviceaccount.yaml
    - Configure ServiceAccount for application pods
    - Add annotations for IAM role association (IRSA)
    - _Requirements: 3.1_

  - [x] 14.2 Create RBAC templates
    - Create devops/helm/persons-finder/templates/rbac.yaml
    - Define Role for application namespace permissions
    - Define RoleBinding to bind ServiceAccount to Role
    - Configure minimal permissions (read ConfigMaps, Secrets)
    - _Requirements: 3.1_

  - [x] 14.3 Create NetworkPolicy template
    - Create devops/helm/persons-finder/templates/networkpolicy.yaml
    - Configure ingress rules (allow from Ingress controller)
    - Configure egress rules (allow to external LLM, DNS)
    - Implement default deny policy
    - _Requirements: 3.1_

  - [x] 14.4 Create ImagePullSecret template
    - Create devops/helm/persons-finder/templates/imagepullsecret.yaml
    - Configure ECR authentication
    - Support automatic credential refresh
    - _Requirements: 1.1_

  - [x] 14.5 Write unit tests for security resources
    - Test ServiceAccount renders correctly
    - Test RBAC permissions are minimal
    - Test NetworkPolicy rules
    - Test ImagePullSecret configuration
    - _Requirements: 3.1_

- [x] 15. Create deployment documentation and scripts
  - [x] 15.1 Create deployment README
    - Create devops/docs/DEPLOYMENT.md with comprehensive deployment guide
    - Document deployment prerequisites (kubectl, helm, docker, terraform)
    - Document Helm Chart structure and values configuration
    - Document local testing with Kind/Minikube using Helm
    - Document secret creation process
    - Document helm install/upgrade commands for different environments
    - Document verification steps (helm status, kubectl get)
    - Document rollback procedures (helm rollback)
    - _Requirements: 8.1, 8.3, 8.5_

  - [x] 15.2 Create deployment helper scripts
    - Create devops/scripts/deploy.sh for automated Helm deployment
    - Create devops/scripts/verify.sh for deployment verification
    - Create devops/scripts/local-test.sh for Kind-based testing with Helm
    - Create devops/scripts/setup-eks.sh for EKS cluster setup with Terraform
    - Create devops/scripts/teardown-eks.sh for EKS cluster teardown
    - Make all scripts executable and add error handling
    - Add usage documentation in script headers
    - _Requirements: 8.1, 8.3, 8.5, 8.6_

  - [x] 15.3 Create quick start guide
    - Create devops/docs/QUICKSTART.md for getting started
    - Document fastest path to local deployment
    - Document fastest path to AWS EKS deployment
    - Include troubleshooting common issues
    - _Requirements: 8.1, 8.5_

- [x] 16. Implement application API endpoints
  - [x] 15.1 Implement Person entity and repository
    - Create Person entity with JPA annotations (id, name)
    - Create PersonRepository interface extending JpaRepository
    - Configure H2 database schema generation
    - _Requirements: Application data layer_

  - [x] 15.2 Implement Location entity and repository
    - Create Location entity with JPA annotations (referenceId, latitude, longitude)
    - Create LocationRepository interface extending JpaRepository
    - Add indexes for efficient location queries
    - _Requirements: Application data layer_

  - [x] 15.3 Implement PersonsService and LocationsService
    - Create PersonsService interface and implementation
    - Create LocationsService interface and implementation
    - Implement location distance calculation algorithm (Haversine formula)
    - Implement nearby person search logic
    - _Requirements: Application domain layer_

  - [x] 15.4 Implement POST /api/v1/persons endpoint
    - Create PersonController with POST endpoint
    - Accept person name in request body
    - Return created person with generated ID
    - Add input validation
    - _Requirements: Application presentation layer_

  - [x] 15.5 Implement PUT /api/v1/persons/{id}/location endpoint
    - Add PUT endpoint to PersonController
    - Accept latitude and longitude in request body
    - Update or create location for person
    - Validate coordinate ranges (-90 to 90 for latitude, -180 to 180 for longitude)
    - _Requirements: Application presentation layer_

  - [x] 15.6 Implement GET /api/v1/persons/{id}/nearby endpoint
    - Add GET endpoint to PersonController
    - Accept radius query parameter (in kilometers)
    - Return list of person IDs within specified radius
    - Use Haversine formula for distance calculation
    - _Requirements: Application presentation layer_

  - [x] 15.7 Implement GET /api/v1/persons endpoint
    - Add GET endpoint to PersonController
    - Accept comma-separated ids query parameter
    - Return list of person details
    - Handle missing persons gracefully
    - _Requirements: Application presentation layer_

  - [x] 15.8 Write unit tests for Person and Location entities
    - Test entity creation and validation
    - Test JPA persistence operations
    - Test repository query methods
    - _Requirements: Application data layer_

  - [x] 15.9 Write unit tests for services
    - Test PersonsService CRUD operations
    - Test LocationsService location updates
    - Test distance calculation algorithm
    - Test nearby person search logic
    - _Requirements: Application domain layer_

  - [x] 15.10 Write unit tests for API endpoints
    - Test POST /api/v1/persons endpoint
    - Test PUT /api/v1/persons/{id}/location endpoint
    - Test GET /api/v1/persons/{id}/nearby endpoint
    - Test GET /api/v1/persons endpoint
    - Test input validation and error handling
    - _Requirements: Application presentation layer_

- [x] 16. Update Spring Boot application for production readiness
  - [x] 16.1 Configure Spring Boot Actuator
    - Add Spring Boot Actuator dependency to build.gradle.kts
    - Configure /actuator/health endpoint
    - Implement readiness and liveness health indicators
    - Configure health endpoint to return 200 when ready, 503 when not ready
    - _Requirements: 9.1, 9.2, 9.3_

  - [x] 16.2 Configure application for environment variable injection
    - Update application configuration to read OPENAI_API_KEY from environment
    - Add validation to fail gracefully with clear error if API key missing
    - Implement startup check for required environment variables
    - _Requirements: 2.3, 2.4_

  - [x] 16.3 Write unit tests for health endpoints
    - Test /actuator/health returns 200 when application is ready
    - Test /actuator/health returns 503 when application is not ready
    - Test health endpoint response format
    - _Requirements: 9.1, 9.2, 9.3_

  - [x] 16.4 Write unit tests for environment variable handling
    - Test application reads API key from environment
    - Test application fails gracefully when API key is missing
    - Test error message clarity
    - _Requirements: 2.3, 2.4_

- [x] 18. Create AI usage documentation
  - Create AI_LOG.md in repository root
  - Document all AI-assisted work with original prompts
  - Document identified flaws or issues in AI-generated content
  - Document fixes applied to AI-generated content
  - Include sections for Dockerfile, Kubernetes manifests, CI/CD workflow, and PII redaction
  - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

- [x] 18.1 Write unit tests for AI documentation
  - Test AI_LOG.md file exists in repository root
  - Test file contains required sections
  - _Requirements: 10.1, 10.5_

- [x] 19. Final checkpoint - Integration testing and verification
  - Build Docker image locally and verify it runs
  - Deploy to local Kind/Minikube cluster using Helm
  - Test helm install with different values files
  - Verify all Kubernetes resources are created successfully
  - Test health endpoints are responding correctly
  - Test HPA is monitoring and can scale
  - Verify PII redaction sidecar is intercepting requests
  - Verify audit logs are being generated
  - Test helm upgrade and rollback functionality
  - Run all property-based tests and unit tests
  - Ensure all tests pass, ask the user if questions arise

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Property tests validate universal correctness properties with minimum 100 iterations
- Unit tests validate specific configuration examples and edge cases
- The implementation uses Kotlin 1.6.21 for the Spring Boot 2.7.0 application and PII redaction service
- The application follows a 3-layer architecture: Presentation (REST API), Domain (business logic), Data (entities)
- Four API endpoints are planned: create person, update location, find nearby, get person details
- H2 in-memory database is used for development (production should use external database)
- All Kubernetes resources are defined as Helm templates following IaC principles
- Helm provides parameterization for different environments (dev, staging, prod)
- CI/CD pipeline uses GitHub Actions with Trivy for security scanning
- PII protection uses sidecar pattern for intercepting and redacting outbound LLM requests
- Terraform is used for provisioning AWS EKS infrastructure
- Task 16 implements the core application API endpoints based on DEVELOPER.md specifications

