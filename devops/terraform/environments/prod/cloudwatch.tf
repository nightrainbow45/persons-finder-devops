# Layer 4: AI Firewall Observability — CloudWatch 日志组 + 指标过滤器 + 告警
# Layer 4: AI Firewall Observability — CloudWatch log group, metric filters, alarm
#
# 数据流 / Data flow:
#   AuditLogger (stdout) → Fluent Bit DaemonSet → CloudWatch Logs → Metric Filter → Alarm

# --- CloudWatch 日志组 / Log Group ---

resource "aws_cloudwatch_log_group" "pii_audit" {
  # Fluent Bit 将 PII 审计日志发送到此日志组
  # Fluent Bit ships PII audit logs here
  name              = "/eks/persons-finder/pii-audit"
  retention_in_days = 90

  tags = {
    Name        = "persons-finder-pii-audit"
    Description = "PII redaction audit logs from Layer 1 in-process and Layer 2 sidecar"
  }
}

# --- IAM: 授权 EC2 节点上的 Fluent Bit 写入 CloudWatch Logs ---
# Fluent Bit 运行在 EC2 节点上，使用节点 IAM role 访问 CloudWatch。
# 权限只授予特定日志组，遵循最小权限原则。
# Fluent Bit runs on EC2 nodes and uses the node IAM role.
# Permissions are scoped to the specific log group (least privilege).

resource "aws_iam_role_policy" "fluent_bit_cloudwatch" {
  name = "fluent-bit-cloudwatch"
  role = module.eks.node_role_name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "FluentBitCloudWatchLogs"
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",   # 创建日志流（每个 pod 一条）
          "logs:PutLogEvents",      # 写入日志事件
          "logs:DescribeLogStreams", # 检查已有日志流（避免重复创建）
          "logs:DescribeLogGroups"  # Fluent Bit 健康检查需要
        ]
        Resource = [
          aws_cloudwatch_log_group.pii_audit.arn,
          "${aws_cloudwatch_log_group.pii_audit.arn}:*"
        ]
      }
    ]
  })
}

# --- 指标过滤器 1：所有 PII 审计事件计数 / Metric Filter: PII audit event count ---
# 匹配 AuditLogger 输出的 JSON 格式：{"type":"PII_AUDIT",...}
# Matches AuditLogger JSON output: {"type":"PII_AUDIT",...}

resource "aws_cloudwatch_log_metric_filter" "pii_audit_total" {
  name           = "pii-audit-total"
  log_group_name = aws_cloudwatch_log_group.pii_audit.name

  # CloudWatch 过滤语法：匹配 JSON 字段
  # CloudWatch filter syntax: match JSON field
  pattern = "{ $.type = \"PII_AUDIT\" }"

  metric_transformation {
    name          = "PiiAuditTotal"
    namespace     = "PersonsFinder/PII"
    value         = "1"
    default_value = 0
    # unit: Count — 每次 LLM 调用 +1
  }
}

# --- 指标过滤器 2：零脱敏事件（潜在 PII 泄漏）/ Metric Filter: zero-redaction events ---
# 当 redactionsApplied=0 时，表明 LLM 请求未经任何脱敏，可能携带原始 PII。
# When redactionsApplied=0, the LLM request carried no detected PII — potential leak.

resource "aws_cloudwatch_log_metric_filter" "pii_zero_redactions" {
  name           = "pii-zero-redactions"
  log_group_name = aws_cloudwatch_log_group.pii_audit.name

  pattern = "{ $.type = \"PII_AUDIT\" && $.redactionsApplied = 0 }"

  metric_transformation {
    name          = "PiiZeroRedactions"
    namespace     = "PersonsFinder/PII"
    value         = "1"
    default_value = 0
  }
}

# --- CloudWatch 告警：零脱敏 LLM 请求（潜在 PII 泄漏风险）/ Alarm: potential PII leak ---
# 触发条件：5 分钟内出现 ≥1 次 redactionsApplied=0 的 LLM 调用
# Triggers when: ≥1 LLM call with zero redactions in a 5-minute window

resource "aws_cloudwatch_metric_alarm" "pii_leak_risk" {
  alarm_name          = "persons-finder-pii-leak-risk"
  alarm_description   = "LLM request with 0 PII redactions detected — potential unredacted PII sent to LLM provider. Check AuditLogger output for requestId."
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  metric_name         = "PiiZeroRedactions"
  namespace           = "PersonsFinder/PII"
  period              = 300 # 5 分钟窗口 / 5-minute window
  statistic           = "Sum"
  threshold           = 1

  # 无数据时视为"正常"（启动时还没有日志是正常的）
  # Treat missing data as "not breaching" (normal during startup)
  treat_missing_data = "notBreaching"

  # SNS 通知（可选，接入 PagerDuty / 邮件时取消注释）
  # SNS notification — uncomment to connect PagerDuty or email:
  # alarm_actions = [aws_sns_topic.pii_alerts.arn]

  tags = {
    Name = "persons-finder-pii-leak-risk"
  }
}

# --- 输出 / Outputs ---

output "cloudwatch_log_group" {
  value       = aws_cloudwatch_log_group.pii_audit.name
  description = "CloudWatch log group for PII audit logs (populated by Fluent Bit)"
}

output "cloudwatch_alarm_arn" {
  value       = aws_cloudwatch_metric_alarm.pii_leak_risk.arn
  description = "ARN of the PII leak risk CloudWatch alarm"
}
