# Kafka-Connect Operator

Lar apper konfigurere Connectors i Kafka-Connect via Kubernetes sine ConfigMaps.

Lytter på ConfigMaps som inneholder konfigurasjon for Connectors og sørger for å opprette,
oppdatere, og slette Connectors.

## Eksempel på en Connector i ConfigMap

Dette er et eksempel på en connector. Operatoren fanger opp alle ConfigMaps som har `destination: connect` som label.
Flagget `enabled` kan brukes til å fort skru av/på en connector.

Et ConfigMap kan inneholde flere connector-konfigurasjoner, hver i sin egen nøkkel.

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: dp-connect-data-inntekt-v27
  namespace: teamdagpenger
  labels:
    team: teamdagpenger
    destination: connect
    enabled: "false"
data:
  data-inntekt-v27.json: |-
    {
      "name": "data-inntekt-v27",
      "config": {
        "connector.class": "com.wepay.kafka.connect.bigquery.BigQuerySinkConnector",
        "autoCreateTables": "true",
        "sanitizeTopics": "true",
        "topics": "teamdagpenger.data-inntekt-v27",
        "tasks.max": "1",
        "project": "",
        "defaultDataset": "dataprodukt",
        "transforms": "dropPrefix",
        "transforms.dropPrefix.type": "org.apache.kafka.connect.transforms.RegexRouter",
        "transforms.dropPrefix.regex": "teamdagpenger\\.(.*)",
        "transforms.dropPrefix.replacement": "$1"
      }
    }
```