# CI/CD Configurations

This directory contains CI/CD pipeline configurations.

## Files

- `ci-cd.yml` - GitHub Actions workflow (to be copied to `.github/workflows/`)

## Setup

Copy the workflow file to the GitHub Actions directory:
```bash
mkdir -p .github/workflows
cp devops/ci/ci-cd.yml .github/workflows/ci-cd.yml
```

## Configuration

The workflow requires the following GitHub Secrets:
- `AWS_ACCOUNT_ID` - Your AWS account ID
- `AWS_REGION` - AWS region (e.g., ap-southeast-2)
- `ECR_REPOSITORY` - ECR repository name

The workflow uses OIDC for AWS authentication (no long-term credentials needed).
