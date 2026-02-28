# Docker Configuration

This directory contains Docker containerization configurations for the Persons Finder application.

## Files

- `Dockerfile` - Multi-stage Docker build configuration
- `.dockerignore` - Files to exclude from Docker build context

## Building the Image

```bash
docker build -f devops/docker/Dockerfile -t persons-finder:latest .
```

## Running Locally

```bash
docker run -p 8080:8080 -e OPENAI_API_KEY=your-key persons-finder:latest
```
