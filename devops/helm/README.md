# Helm Charts

This directory contains Helm Charts for deploying the Persons Finder application to Kubernetes.

## Charts

- `persons-finder/` - Main application Helm Chart

## Usage

```bash
# Install the chart
helm install persons-finder ./devops/helm/persons-finder

# Install with custom values
helm install persons-finder ./devops/helm/persons-finder -f ./devops/helm/persons-finder/values-dev.yaml

# Upgrade
helm upgrade persons-finder ./devops/helm/persons-finder

# Uninstall
helm uninstall persons-finder
```

## Configuration

See `persons-finder/README.md` for detailed configuration options.
