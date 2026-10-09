param location string = resourceGroup().location
param prefix string = 'opsflow'
param logsName string = '${prefix}-logs'

resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' existing = {
  name: logsName
}

resource environment 'Microsoft.App/managedEnvironments@2025-10-02-preview' = {
  name: '${prefix}-standard-environment'
  location: location
  properties: {
    // The current Azure CLI supports this preview field before Bicep's type catalog does.
    #disable-next-line BCP037
    environmentMode: 'WorkloadProfiles'
    workloadProfiles: [{ name: 'Consumption', workloadProfileType: 'Consumption' }]
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logs.properties.customerId
        sharedKey: logs.listKeys().primarySharedKey
      }
    }
  }
}

output environmentName string = environment.name
output defaultDomain string = environment.properties.defaultDomain
