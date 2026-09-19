# Extending the real collector

## Add additional machines

For a multi-node deployment, run one collector on each machine and forward normalized flow records to a central WebSocket/API service.

Recommended event shape:

```json
{
  "sourceNode": "ava-core",
  "sourceLat": 21.3,
  "sourceLng": -157.8,
  "remoteIp": "203.0.113.10",
  "protocol": "tcp",
  "remotePort": 443,
  "process": "python",
  "packets": 120,
  "bytes": 88442,
  "ts": 1789782620123
}
```

## AWS/VPC

The next production collector can consume VPC Flow Logs, load balancer flow data, CloudWatch metrics, or an observability pipeline and emit the same normalized event shape.

## Offline geolocation

For environments that must avoid an external geolocation API, replace `lookupIp()` with a local MaxMind/DB-IP-style database reader and retain the same `{ lat, lng, city, country, asn, org }` record shape.
