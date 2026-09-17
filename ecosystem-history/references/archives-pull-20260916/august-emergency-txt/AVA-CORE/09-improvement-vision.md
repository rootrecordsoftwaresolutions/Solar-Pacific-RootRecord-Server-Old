# Improvement vision — one persistent Ava

Distilled from operator rebuild notes. **No API keys. No secrets.**

## Vision

Ava is not just a chatbot. She is the persistent AI developer, project manager, community assistant, and knowledge system for RootMC.

Goals: assist the community, help develop RootMC, learn from project history, improve code/docs, keep one personality across platforms, never lose project context.

## Core principles

### One brain

Discord, Telegram, CLI, web dashboard, and future interfaces talk to the **same** Ava Core backend. A conversation started on Telegram should continue on Discord without a second personality.

```
Discord / Telegram / Dashboard / CLI
              │
         Gateway Layer
              │
       Conversation API
              │
           Ava Core
     (Memory · Planner · Knowledge · Scheduler · Plugins)
              │
          Root Server
```

### Personality vs capabilities

Keep separate. Personality owns tone, humor, memory use, community identity. Capabilities are modular plugins (Git, Discord, Telegram, RootMC, DB, docs, search, schedule).

### Continuous improvement without rogue deploys

Nightly review loop: conversations → mistakes → repeated questions → doc gaps → proposals → code suggestions → tests → benchmarks → report. **Nothing deploys automatically.** Human oversight for impactful changes. Version-control everything.

## Daily improvement cycle

Review → identify mistakes / repeated questions / doc gaps → generate improvement proposals → write suggestions → run tests → benchmark → report.

## Conversation engine (lightweight)

1. Understand intent  
2. Search memory  
3. Search project knowledge if needed  
4. Gather plugin data  
5. Plan response  
6. Respond  
7. Record useful notes  

## Security & monitoring

Role-based permissions (community read → mods → developers → owner). Confirm high-impact ops. Track uptime, response time, plugin failures, queue length — concise dashboards, **not** public ops dumps.

## Phased goals

| Phase | Focus |
|-------|--------|
| 1 | Unified Ava Core, Discord + Telegram, persistent memory, plugins |
| 2 | Developer assistant, knowledge index, auto docs, proposal workflow |
| 3 | Project-wide reasoning, repo analysis, architecture suggestions |
| 4 | Voice, web dashboard, mobile companion, visual monitoring |

## Guiding rules

- One identity across platforms  
- Modular plugins  
- Persistent organized memory  
- Concise, context-aware replies  
- Treat Ava as a long-lived collaborator — reliable, explainable, maintainable  

## Free / multi-opinion APIs (operator intent)

Add more free/data APIs later for multi-opinion before a final plan — budget-capped, never hard-coded keys in docs. Secrets live only in `.env` / `.credentials`.
