# Test Registry Configuration

This directory contains Docker Compose configuration for testing Docker registry operations.

## Test Registries

### Basic Registry (localhost:5000)
- **Service**: `registry`
- **Port**: 5000
- **Authentication**: None
- **Purpose**: Test basic push/pull operations without authentication

### Authenticated Registry (localhost:5001)
- **Service**: `registry-auth` 
- **Port**: 5001
- **Authentication**: htpasswd
- **Test Credentials**:
  - Username: `testuser`
  - Password: `testpass`
- **Purpose**: Test authenticated push/pull operations

## Usage

### Start test registries
```bash
cd src/test/resources
docker-compose -f docker-compose-registry.yml up -d
```

### Stop test registries
```bash
cd src/test/resources
docker-compose -f docker-compose-registry.yml down -v
```

### Clean registry data
```bash
cd src/test/resources
docker-compose -f docker-compose-registry.yml down -v
docker volume prune -f
```

## Integration Test Usage

The registries are automatically managed by integration tests using Docker Compose lifecycle hooks.

Registry endpoints:
- Basic: `http://localhost:5000`
- Authenticated: `http://localhost:5001`