# Repository Guidance: RootRecord Core Node

This repository is the public, MIT-licensed RootRecord node.

## Scope

- Put self-hostable node code, public protocols, examples, tests, and documentation here.
- Keep the node runnable by users without RootRecord-operated credentials or private infrastructure.
- Attribute public data sources and preserve user ownership of local data.

## Boundaries

- RootMC development belongs only in `RootRecord-RootMC`.
- Operator controls, local backups, OBS, and private workflows belong in `RootRecord-Core-Ops`.
- Hosted automation and private operational runtime belong in `RootRecord-Core-Processor`.

## Safety

Never commit credentials, tokens, private keys, personal data, live databases, or generated runtime state. Keep public defaults safe and document configuration through examples.
