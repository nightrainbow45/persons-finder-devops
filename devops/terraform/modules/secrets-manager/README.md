# Secrets Manager Module / Secrets Manager 模块

This module creates AWS Secrets Manager secrets for application configuration.
本模块为应用配置创建 AWS Secrets Manager 密钥。

## Resources Created / 创建的资源

- Secrets Manager secrets
  Secrets Manager 密钥
- KMS encryption keys
  KMS 加密密钥
- Secret rotation configuration
  密钥轮换配置

## Usage / 使用方式

```hcl
module "secrets" {
  source = "../../modules/secrets-manager"

  secret_name = "persons-finder/openai-api-key"
  description = "OpenAI API key for Persons Finder application"
}
```

## Outputs / 输出

- `secret_arn` — ARN of the secret / 密钥的 ARN
- `secret_name` — Name of the secret / 密钥名称
