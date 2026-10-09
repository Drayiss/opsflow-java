param location string = resourceGroup().location
param registryName string
param environmentName string
param identityName string
param postgresName string
param redisName string
param serviceBusName string
param appName string
param imageTag string
param oidcIssuer string
param oidcJwks string
param oidcAudience string
@secure()
param databasePassword string
resource registry 'Microsoft.ContainerRegistry/registries@2023-07-01' existing = { name: registryName }
resource environment 'Microsoft.App/managedEnvironments@2024-03-01' existing = { name: environmentName }
resource identity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' existing = { name: identityName }
resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' existing = { name: postgresName }
resource redis 'Microsoft.Cache/redisEnterprise@2025-07-01' existing = { name: redisName }
resource cache 'Microsoft.Cache/redisEnterprise/databases@2025-07-01' existing = { parent: redis, name: 'default' }
resource app 'Microsoft.App/containerApps@2024-03-01' = {
  name: appName
  location: location
  identity: { type: 'UserAssigned', userAssignedIdentities: { '${identity.id}': {} } }
  properties: {
    managedEnvironmentId: environment.id
    workloadProfileName: 'Consumption'
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: { external: true, targetPort: 8081, allowInsecure: false }
      registries: [{ server: registry.properties.loginServer, identity: identity.id }]
      secrets: [
        { name: 'database-password', value: databasePassword }
        { name: 'redis-password', value: cache.listKeys().primaryKey }
      ]
    }
    template: {
      containers: [
        {
          name: 'api'
          image: '${registry.properties.loginServer}/opsflow-api:${imageTag}'
          resources: { cpu: json('1.0'), memory: '2Gi' }
          env: [
            { name: 'DATABASE_URL', value: 'jdbc:postgresql://${postgres.properties.fullyQualifiedDomainName}:5432/opsflow?sslmode=require' }
            { name: 'DATABASE_USER', value: 'opsflow' }
            { name: 'DATABASE_POOL_SIZE', value: '10' }
            { name: 'DATABASE_PASSWORD', secretRef: 'database-password' }
            { name: 'REDIS_HOST', value: redis.properties.hostName }
            { name: 'REDIS_PORT', value: '10000' }
            { name: 'REDIS_SSL', value: 'true' }
            { name: 'REDIS_PASSWORD', secretRef: 'redis-password' }
            { name: 'OIDC_ISSUER', value: oidcIssuer }
            { name: 'OIDC_JWKS', value: oidcJwks }
            { name: 'OIDC_AUDIENCE', value: oidcAudience }
            { name: 'CORS_ORIGIN', value: 'https://${appName}.${environment.properties.defaultDomain}' }
            { name: 'NOTIFICATION_TRANSPORT', value: 'azure' }
            { name: 'SERVICEBUS_NAMESPACE', value: '${serviceBusName}.servicebus.windows.net' }
            { name: 'AZURE_CLIENT_ID', value: identity.properties.clientId }
          ]
          probes: [
            { type: 'Startup', httpGet: { path: '/actuator/health/liveness', port: 8080 }, periodSeconds: 10, failureThreshold: 30 }
            { type: 'Liveness', httpGet: { path: '/actuator/health/liveness', port: 8080 }, periodSeconds: 30 }
            { type: 'Readiness', httpGet: { path: '/actuator/health/readiness', port: 8080 }, periodSeconds: 10 }
          ]
        }
        {
          name: 'web'
          image: '${registry.properties.loginServer}/opsflow-web:${imageTag}'
          resources: { cpu: json('0.25'), memory: '0.5Gi' }
          env: [{ name: 'API_UPSTREAM', value: 'localhost:8080' }]
          probes: [{ type: 'Readiness', httpGet: { path: '/', port: 8081 }, periodSeconds: 10 }]
        }
      ]
      // One replica keeps the background consumer running and bounds demo compute costs.
      scale: { minReplicas: 1, maxReplicas: 1 }
    }
  }
}
output url string = 'https://${app.properties.configuration.ingress.fqdn}'
