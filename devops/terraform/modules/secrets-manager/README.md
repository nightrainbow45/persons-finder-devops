# Secrets Manager Module

This module creates AWS Secrets Manager secrets for application configuration.

## Resources Created

- Secrets Manager secrets
- KMS encryption keys
- Secret rotation configuration

## Usage

```hcl
module "secrets" {
  source = "../../modules/secrets-manager"
  
  secret_name = "persons-finder/openai-api-key"
  description = "OpenAI API key for Persons Finder application"
}
```

## Outputs

- `secret_arn` - ARN of the secret
- `secret_name` - Name of the secret
