# Hawaii local-data-globe collector

Headless Hawaii telemetry exporter for the Network Globe project.

Project location on the Hawaii machine:
`/home/rootrecord/.ollama/skills/local-data-globe`

The collector observes real local network sockets (`ss`) and optional packet metadata (`tcpdump`), normalizes observations as NDJSON, and streams them to the existing AWS Network Globe project over SSH.

It does not contain a visualizer or a second poller.

## AWS transport

The default AWS endpoint is the current RootRecord public IP `3.139.100.162` and the default SSH key is `/home/rootrecord/.ssh/rootrecordkey.pem`. The key path is explicit because `start.sh` runs the collector with sudo, so the collector cannot rely on the user's `~/.ssh/config` or SSH agent.

Remote destination:
`/home/ubuntu/network-globe/network-globe/data/hawaii.ndjson`

If AWS is temporarily unavailable, records are appended to:
`data/outbox.ndjson`

The collector reconnects and sends buffered records when SSH becomes available.
