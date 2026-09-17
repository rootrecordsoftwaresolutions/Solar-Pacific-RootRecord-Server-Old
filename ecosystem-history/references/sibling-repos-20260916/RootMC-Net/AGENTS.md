# Repository Guidance: RootMC Net

This repository owns the Vercel web project for `rootmc.net`.

## Scope

- Put public pages, components, static assets, and frontend tests here.
- Keep the app independently deployable by Vercel.
- Use documented public API contracts; do not embed private runtime state.
- Keep credentials in Vercel environment variables, never in source control.

## Stack

- Next.js (App Router) + TypeScript
- Deploy target: Vercel only

## Boundaries

- `RootRecord-RootMC`: all RootMC development and services
- `RootRecord-Core-Processor`: long-running processing
- `RootRecord-Core-Ops`: local operator desk

## License

No license. Public for transparency and Vercel deployment only.
