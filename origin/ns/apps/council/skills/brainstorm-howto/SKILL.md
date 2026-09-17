# Brainstorm

Owner starts a timed thought session: `/brainstorm` or “let’s brainstorm”.
Ava asks topic and duration first (unless they already said e.g. `/brainstorm 30 NPU soak`).
Minimum 10 minutes. No maximum. Rounds continue until time is up, then they conclude.
Say `stop` in chat to wrap early.
Maps FastFlowLM `llama3.2:3b` on the NPU for the session; wrap unmaps it. Voices stay on 3B — no 7B/8B swap.
Each voice adds one new useful point on the owner's topic (a named feature, visitor sentence, rule, file, or constraint), or PASS. Already-said points are listed in the prompt — repeating them is a failure. If someone leaves the topic, the next voice does not follow. Continue rounds do not file a proposal; wrap files once. No weather, volcano, EcoFlow, or leftover plan items unless that is the topic. No Cursor, no secrets, no invented host numbers.
