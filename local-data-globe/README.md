# Network Globe — Hawaii Data Collector

This is the **data-only** Hawaii measurement point. It does not run a globe, browser UI, WebSocket server, or visualization.

It polls the real local machine with `ss`, captures packet metadata with `tcpdump` when started as root, normalizes each observation to NDJSON, and streams the records over a persistent SSH stdin connection to the live AWS Network Globe collector.

## Install

Place this folder at:

`/home/rootrecord/.ollama/skills/local-data-globe`

Then run:

```bash
cd /home/rootrecord/.ollama/skills/local-data-globe
./start.sh
```

## AWS destination

Defaults:

- user: `ubuntu`
- host: `3.139.100.162`
- remote directory: `/home/ubuntu/network-globe/network-globe`
- remote stream file: `data/hawaii.ndjson`

Override them without editing code:

```bash
AWS_HOST=YOUR_CURRENT_AWS_IP ./start.sh
AWS_USER=ubuntu AWS_HOST=YOUR_CURRENT_AWS_IP AWS_REMOTE_DIR=/home/ubuntu/network-globe/network-globe ./start.sh
```

If a non-default SSH key is required:

```bash
SSH_KEY=/path/to/key AWS_HOST=YOUR_CURRENT_AWS_IP ./start.sh
```

The SSH user must be able to log into the AWS instance and write the remote `data/` directory.

## Origin

The collector first uses the public IP geolocation returned by `ipapi.co`. For a fixed measurement point, set:

```bash
ORIGIN_LAT=21.3069 ORIGIN_LNG=-157.8583 ORIGIN_LABEL=Hawaii ./start.sh
```

The location is an approximate origin for visualization, not a claim about an exact physical endpoint.

## Local buffering

If SSH is temporarily unavailable, the collector keeps an in-memory reconnect buffer and also appends unsent records to `data/outbox.ndjson`.

`outbox.ndjson` is a safety spool; it is not the AWS source of truth. The live AWS merge path is the SSH stream into `data/hawaii.ndjson`.

## Data sent

Each line is one JSON observation:

```json
{
  "type": "network-globe-telemetry",
  "version": 1,
  "timestamp": 0,
  "sourceNode": "HawaiiRoot",
  "sourceRegion": "local-hawaii",
  "source": { "latitude": 0, "longitude": 0, "label": "Hawaii" },
  "destination": { "type": "public-ip", "ip": "203.0.113.10", "port": 443 },
  "protocol": "tcp",
  "process": "brave",
  "packets": 0,
  "bytes": 0
}
```

No packet payloads are collected or transmitted.
