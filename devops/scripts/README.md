# Deployment Scripts

This directory contains helper scripts for deployment and testing.

## Scripts

- `deploy.sh` - Automated Helm deployment script
- `verify.sh` - Deployment verification script
- `local-test.sh` - Local testing with Kind/Minikube
- `setup-eks.sh` - EKS cluster setup with Terraform
- `teardown-eks.sh` - EKS cluster teardown
- `setup-github-oidc.sh` - GitHub OIDC configuration helper

## Usage

Make scripts executable:
```bash
chmod +x devops/scripts/*.sh
```

Run a script:
```bash
./devops/scripts/deploy.sh dev
```

See individual script headers for detailed usage instructions.
