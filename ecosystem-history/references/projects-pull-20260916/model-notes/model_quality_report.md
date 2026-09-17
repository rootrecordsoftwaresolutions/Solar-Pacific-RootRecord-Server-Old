# RootRecord Model Stress Test — Full Quality Report
**Run:** `run_20260911-053041` · 23 models · prompt: `merge_sorted_lists` (merge two sorted lists, no built-in `sort()`/`sorted()`, type hints + docstring + 3 asserts + complexity explanation)

The run finished clean overnight, no crashes, no swap thrashing. Speed data came straight from the results JSON; **correctness is new** — I extracted every model's function, ran it against an independent 10-case test suite (empty lists, negatives, duplicates, unequal lengths, etc.), and separately checked whether the model's *own* embedded tests actually passed. Those two numbers disagree for a couple of models, which turns out to be one of the more interesting findings below.

## Headline numbers
- **22/23 ran successfully.** `nomic-embed-text` failed instantly (400 error) — expected, it's an embedding-only model and was never going to answer a generation prompt. Not a bug.
- **18/22 completions are algorithmically correct** against the independent test suite.
- **The GPU was not used.** `gpu_percent` sat at effectively 0% (max 6%, average 0.01%) for the entire 87-minute run — every model ran on CPU, including the ones ROCm can now see. More on this below.
- **CPU hit 97°C peak.** Worth keeping an eye on if you're running these back-to-back.
- **RAM peaked at 12.58GB, swap never touched 0.0GB** — no memory pressure this run.

## Speed + correctness leaderboard
"Own tests" = did the model's *own* embedded assert/unittest block pass. "Independent" = did it pass my 10-case suite that doesn't depend on the model having written a valid test itself.

| Model | Status | Time (s) | Tok/s | Own tests | Independent check | Notes |
|---|---|---|---|---|---|---|
| moondream:latest | PASS | 5.3 | 44.2 | — | ❌ 9/10 | Vision model, not built for this; uses forbidden `sort()`, fails on negatives |
| llama3.2:1b | PASS | 18.4 | 29.2 | ✅ passed | ❌ 6/10 | Invents a fake "lists must be equal length" restriction |
| **llama3.2:3b-instruct-q4_K_M** | **PASS** | **21.3** | **19.2** | — | ✅ **10/10** | **Best fast+correct pick** |
| llama3.2:latest | PASS | 23.3 | 19.2 | — | ❌ 0/10 | Heap-based merge, index-tracking bug — broken from the first test case |
| qwen2.5:1.5b-instruct-q8_0 | SLOW | 33.6 | 23.0 | ❌ failed | ✅ 10/10 | Algorithm is correct — its *own* test case used an already-unsorted input |
| **deepseek-coder-v2:16b** | SLOW | 38.1 | 23.7 | ✅ passed | ✅ 10/10 | **Best correctness-per-second among the larger models** |
| mistral:7b-instruct-v0.3-q4_K_M | SLOW | 57.6 | 9.2 | ✅ passed | ✅ 10/10 | Solid |
| qwen2.5-coder:7b-instruct-q4_K_M | SLOW | 57.7 | 9.0 | ✅ passed | ✅ 10/10 | Solid |
| qwen2.5-coder:7b | SLOW | 61.7 | 9.0 | ✅ passed | ✅ 10/10 | Solid |
| codegemma:7b | SLOW | 66.0 | 7.4 | ✅ passed | ✅ 10/10 | Solid |
| llama3.1:8b-instruct-q4_K_M | SLOW | 72.3 | 8.6 | ✅ passed | ✅ 10/10 | Solid |
| qwen3:4b | SLOW | 72.5 | 14.4 | ❌ failed | ❌ 7/10 | Own test throws `TypeError` on float indices; genuinely buggy |
| qwen2.5:7b-instruct-q4_K_M | SLOW | 76.4 | 9.0 | ✅ passed | ✅ 10/10 | Solid |
| llama3.1:8b | SLOW | 77.4 | 8.5 | ✅ passed | ✅ 10/10 | Solid |
| gemma2:9b | SLOW | 104.5 | 6.8 | ✅ passed | ✅ 10/10 | Solid, slower |
| gemma2:9b-instruct-q4_K_M | SLOW | 112.3 | 6.5 | ✅ passed | ✅ 10/10 | Solid, slower |
| gemma4:e4b | SLOW | 120.9 | 11.9 | ✅ passed | ✅ 10/10 | Solid |
| qwen2.5:14b | SLOW | 131.0 | 4.6 | ✅ passed | ✅ 10/10 | Correct, slow |
| phi4:14b | SLOW | 132.7 | 4.6 | ✅ passed | ✅ 10/10 | Correct, slow |
| qwen2.5-coder:14b | SLOW | 142.9 | 4.6 | ✅ passed | ✅ 10/10 | Correct, slow |
| qwen3:8b | SLOW | 396.9 | 7.2 | ✅ passed | ✅ 10/10 | Correct, but 6.6 minutes is a long wait |
| starcoder2:15b | SLOW | 2257.2 | 3.6 | — | ✅ 10/10 | See below — technically correct, practically unusable as-is |
| nomic-embed-text:latest | FAIL (error) | 0.0 | — | — | — | Embedding model, expected to fail on `/api/generate` |

## Notable bugs, in order of "interesting"

**starcoder2:15b — correct answer buried in 37 minutes of garbage.** This is a base/completion model, not instruction-tuned, and it shows: it echoed the prompt back, then free-associated through unrelated LeetCode solutions (max-profit, binary search, number-of-islands, word-pattern...) for 26KB of text before I could even find the actual function. The `merge_sorted_lists` function it eventually wrote is correct and avoids the built-in sort — but it has no type hints, no assert-based tests (it used doctest-style `>>>` examples instead), and in several places it ran statements together with no newline between them at all, so my extractor had to specifically patch around that. Bottom line: right answer, wrong model for an assistant role. 2257 seconds for a merge function is not something you want in a loop.

**llama3.2:1b — invents a constraint that isn't in the prompt.** It added `if len(a) != len(b): raise ValueError(...)`, which isn't what was asked and actively breaks the "already-sorted lists" case whenever they're different lengths (which is the normal case). Its own unit test even includes an unequal-length case that would trigger this — meaning its own test suite is internally inconsistent with its own function.

**llama3.2:latest — broken from case one.** A heap-based merge implementation that sounds sophisticated but has an index-tracking bug (it never correctly tracks *which* list an index belongs to), so it fails all 10 independent test cases, including the trivial ones. Ironically the smaller llama3.2:3b-instruct did this correctly with a plain two-pointer approach.

**moondream — wrong tool for the job, and buggy regardless.** It's a vision-language model, not a coding model, so it doing anything coherent here is a bit of a bonus. But it explicitly violates the "no built-in sort" instruction (`merged.sort(key=str)`), sorts by *string* representation rather than numeric value (fails on negative numbers — `-5` sorts after `-1` lexically), and references `List` without importing `typing.List`.

**qwen2.5:1.5b-instruct — the one place "own tests failed" is misleading.** Its merge function is a completely standard, correct two-pointer implementation. But its own embedded test #2 uses `a = [-1, -3, 4]` as a supposedly *sorted* input — which it isn't. Feed an unsorted list into a merge algorithm that assumes sorted inputs and you get garbage out; that's a test-authoring bug, not an algorithm bug. This is exactly why I ran an independent check instead of trusting each model's self-report.

**qwen3:4b — a real bug, not a test-authoring artifact.** Fails both its own tests (`TypeError: list indices must be integers or slices, not float`) and 3 of my 10 independent cases. Something in its indexing logic breaks under specific inputs.

## Hardware / pipeline findings

- **GPU offload still isn't happening.** rocm-smi is correctly polling now (that part of the pipeline works), but `gpu_percent` never rose above 6% and averaged ~0% the entire run — Ollama ran every one of these 22 successful generations on CPU only, despite `rocminfo` seeing `gfx1153` natively. Getting ROCm *installed and detecting the chip* was step one; getting *Ollama itself* to actually dispatch to it is still unsolved. Worth checking whether your Ollama build was compiled with ROCm support at all (`ollama --version` / check for `libggml-hip` or similar in its install), since seeing the GPU in `rocminfo` doesn't guarantee Ollama's inference backend is linked against it.
- **Found a real parsing bug in the poller.** The raw rocm-smi JSON log (which is exactly why I asked to keep it) revealed the actual key is `"GPU Memory Allocated (VRAM%)"`, not something matching `"memory use"` — so `gpu_mem_percent` came back empty all run even though the data was right there (69% VRAM allocated, for reference — that's expected for a shared-memory iGPU). Easy one-line fix in `read_gpu_stats()` whenever you want it.
- **97°C peak CPU temp** — not dangerous on its own for this class of chip, but if you're going to run stress tests like this regularly, worth watching for thermal throttling affecting your speed numbers (a throttled CPU would make faster models look artificially slower).
- **No swap usage this run** (unlike the swap-thrashing seen on 14–16b models in earlier testing) — 12.58GB peak RAM usage on the box stayed comfortably under whatever's installed.

## Practical takeaways

- **If you want fast + reliably correct:** `llama3.2:3b-instruct-q4_K_M` (21s, 10/10) is the standout — nothing else in the PASS tier (<30s) is actually correct.
- **If you want best correctness for the time on a bigger model:** `deepseek-coder-v2:16b` (38s, 10/10) punches well above its weight class for the wait.
- **Avoid for coding tasks as-is:** `llama3.2:latest` (broken), `llama3.2:1b` (fabricates constraints), `moondream` (wrong tool, buggy), `qwen3:4b` (real bug), `starcoder2:15b` (correct but needs heavy prompt engineering or a chat template fix to stop rambling).
- **Everything in the 7b–14b instruct-tuned range came back correct** — the differentiator between them at that point is purely speed, which is entirely explained by parameter count and quantization, not quality.
