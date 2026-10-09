param location string = resourceGroup().location
@minLength(3)
@maxLength(12)
param prefix string = 'opsflow'
@secure()
param databasePassword string
var suffix = uniqueString(resourceGroup().id)
resource registry 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: '${prefix}${suffix}'
  location: location
  sku: { name: 'Basic' }
  properties: { adminUserEnabled: false }
}
resource identity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: '${prefix}-app-identity'
  location: location
}
resource pull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(registry.id, identity.id, 'AcrPull')
  scope: registry
  properties: {
    principalId: identity.properties.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '7f951dda-4ed3-4680-a7ca-43fe172d538d')
  }
}
resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: '${prefix}-logs'
  location: location
  properties: { sku: { name: 'PerGB2018' }, retentionInDays: 30 }
}
resource environment 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: '${prefix}-environment'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logs.properties.customerId
        sharedKey: logs.listKeys().primarySharedKey
      }
    }
  }
}
resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: '${prefix}-${suffix}-db'
  location: location
  sku: { name: 'Standard_B1ms', tier: 'Burstable' }
  properties: {
    version: '17'
    administratorLogin: 'opsflow'
    administratorLoginPassword: databasePassword
    storage: { storageSizeGB: 32 }
    backup: { backupRetentionDays: 7, geoRedundantBackup: 'Disabled' }
    network: { publicNetworkAccess: 'Enabled' }
    highAvailability: { mode: 'Disabled' }
  }
}
resource database 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
  parent: postgres
  name: 'opsflow'
  properties: { charset: 'UTF8', collation: 'en_US.utf8' }
}
// Demo-sized deployment: allow Azure-origin connections, authenticate with TLS + password.
// See deployment guide before using sensitive data; production should use private networking.
resource firewall 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2024-08-01' = {
  parent: postgres
  name: 'AllowAzureServices'
  properties: { startIpAddress: '0.0.0.0', endIpAddress: '0.0.0.0' }
}
resource redis 'Microsoft.Cache/redisEnterprise@2025-07-01' = {
  name: '${prefix}-${suffix}-cache'
  location: location
  sku: { name: 'Balanced_B0' }
  properties: { minimumTlsVersion: '1.2', highAvailability: 'Disabled', publicNetworkAccess: 'Enabled' }
}
resource cache 'Microsoft.Cache/redisEnterprise/databases@2025-07-01' = {
  parent: redis
  name: 'default'
  properties: {
    clientProtocol: 'Encrypted'
    clusteringPolicy: 'EnterpriseCluster'
    evictionPolicy: 'AllKeysLRU'
    port: 10000
    accessKeysAuthentication: 'Enabled'
  }
}
resource bus 'Microsoft.ServiceBus/namespaces@2024-01-01' = {
  name: '${prefix}-${suffix}-bus'
  location: location
  sku: { name: 'Standard', tier: 'Standard' }
  properties: { minimumTlsVersion: '1.2', disableLocalAuth: true }
}
resource queue 'Microsoft.ServiceBus/namespaces/queues@2024-01-01' = {
  parent: bus
  name: 'incident-events'
  properties: {
    maxDeliveryCount: 5
    lockDuration: 'PT1M'
    defaultMessageTimeToLive: 'P7D'
    deadLetteringOnMessageExpiration: true
    requiresDuplicateDetection: true
    duplicateDetectionHistoryTimeWindow: 'PT10M'
  }
}
resource send 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(bus.id, identity.id, 'send')
  scope: bus
  properties: {
    principalId: identity.properties.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '69a216fc-b8fb-44d8-bc22-1f3c2cd27a39')
  }
}
resource receive 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(bus.id, identity.id, 'receive')
  scope: bus
  properties: {
    principalId: identity.properties.principalId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '4f6d3b9b-027b-4f4c-9142-0e3f5e9679cc')
  }
}
output registryName string = registry.name
output registryHost string = registry.properties.loginServer
output environmentName string = environment.name
output identityName string = identity.name
output postgresName string = postgres.name
output redisName string = redis.name
output serviceBusName string = bus.name
output appName string = '${prefix}-app'
output appUrl string = 'https://${prefix}-app.${environment.properties.defaultDomain}'
