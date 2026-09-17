# Self-check quiz (with answers)

Use for onboarding quizzes—keep answers internal to instructors.

## Section A — Products

1. **Q:** Name two productivity apps in the RootRecord family.  
   **A:** Business Manager; Weather Manager (Account Hub is account-centric rather than “productivity” in the same sense).

2. **Q:** Which app is the non-custodial Solana mobile client?  
   **A:** Token Manager.

## Section B — Platform

3. **Q:** What is the production API hostname?  
   **A:** `https://api.rootrecord.info`

4. **Q:** What database does the primary Worker use?  
   **A:** Cloudflare D1 (SQLite).

## Section C — Solana

5. **Q:** Where is the canonical Solana Tools site source hosted?  
   **A:** GitHub repo `RootRecord/solana-rootrecord-site`, deployed from its root via `git push main`.

6. **Q:** Should engineers edit random copies under `Web/solana/…` to ship the site?  
   **A:** No—that workflow is explicitly forbidden for routine releases.

## Section D — Safety

7. **Q:** Name three file patterns that must never be committed.  
   **A:** Any three of: `.env`, `credentials.env`, keystores, Firebase JSON, Stripe secrets.

## Related reading

- [../09-reference/faq.md](../09-reference/faq.md)
