# RootRecord Cloud

Vercel project for **rootrecord.cloud**.

Next.js (App Router) Vercel app for the RootRecord public dashboard.

## Stack

- Next.js 15 (App Router)
- React 19
- TypeScript

## Local development

```bash
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

## Vercel

Import this repository as its own Vercel project.

- Framework preset: **Next.js**
- Root directory: repository root
- Keep secrets in Vercel environment variables (never commit them)

Long-running processing does **not** belong in this repo.

## Scope

This repo owns the RootRecord public web experience: the live dashboard, discovery, public guides, and Vercel serverless handlers for documented read-only APIs. Long-running processing remains on the AVA Processor behind `AVA_ORIGIN_URL`.
