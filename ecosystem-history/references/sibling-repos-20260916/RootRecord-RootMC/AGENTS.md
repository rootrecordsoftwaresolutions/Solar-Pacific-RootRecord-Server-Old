# Repository Guidance: RootRecord RootMC

This repository owns **all RootMC development**.

## Scope

- Put RootMC server, plugins, APIs, web surfaces, mobile clients, schemas, tools, tests, and documentation here.
- Keep RootMC changes out of the Node, Ops, and Processor repositories unless a narrowly documented integration contract requires a change there.
- Preserve the distinction between RootMC production infrastructure and AVA/RootRecord operational services.

## License and Publication

This repository has no license. It is public for transparency, coordination, and review only. Do not add a license without an explicit project decision.

## Safety

Never commit credentials, tokens, private keys, live databases, player personal data, server backups, or generated runtime state. Use configuration examples and document integration endpoints without secrets.
