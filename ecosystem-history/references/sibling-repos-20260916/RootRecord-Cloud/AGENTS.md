# Repository Guidance: RootRecord Cloud

This repository owns the Vercel web project for `rootrecord.cloud`.

## Scope

- Put public pages, components, static assets, and frontend tests here.
- Keep the app independently deployable by Vercel.
- Use documented public API contracts; do not embed private runtime state.
- Keep credentials in Vercel environment variables, never in source control.

## Stack

- Next.js (App Router) + TypeScript
- Deploy target: Vercel only

## Boundaries

- `RootRecord-Core-Processor`: hosted processing and APIs
- `RootRecord-Core-Ops`: operator controls
- `RootRecord-RootMC`: RootMC development

## License

No license. Public for transparency and Vercel deployment only.
