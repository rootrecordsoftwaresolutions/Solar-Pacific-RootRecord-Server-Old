# Ava Ivy Cloud

Vercel project for **avaivy.cloud**.

Next.js (App Router) Vercel app for the Ava Ivy public web experience.

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

This repo owns the Ava Ivy public web experience, identity pages, status surfaces, and frontend integrations with documented RootRecord APIs. Long-running processing remains on the AVA Processor behind `AVA_ORIGIN_URL`.
