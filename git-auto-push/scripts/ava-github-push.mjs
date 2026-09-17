#!/usr/bin/env node
/**
 * Ava multi-repo GitHub push (canonical).
 *
 * Covers Ava-Core-Dev repos on this desk:
 *   - ava-core           (public runtime; also mirrors HEAD → branch `dev`)
 *   - ava-core-private   (private handoff + plugins/workstations sync; main + `dev`)
 *   - all-connections    (agent map; main + `dev`)
 *   - web-files          (aggregated web sources; main + `dev`)
 *
 * Safety:
 *   - never force-pushes main/master
 *   - never stages .env / credentials / keys
 *   - only Ava-owned paths (see SYNC_SPECS / SKIP patterns)
 *
 * Usage:
 *   node scripts/ava-github-push.mjs [--dry-run] ["optional commit message"]
 *   AVA_GITHUB_PUSH_ONLY=ava-core,all-connections node scripts/ava-github-push.mjs
 *
 * Timer: user systemd `ava-auto-push.timer` → scripts/auto-push.sh → this script.
 * Manual: same command from ava-core-v2, or `bash scripts/ava-github-push.sh`
 */
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const AVA_CORE_V2 = path.join(os.homedir(), ".ollama", "skills", "origin", "repo");
const HANDOFF = path.join(os.homedir(), ".ollama", "skills");
const MIRRORS = HANDOFF;
const SKILLS = path.join(os.homedir(), ".ollama", "skills");

function loadGithubToken() {
  if (process.env.GH_TOKEN || process.env.GITHUB_TOKEN) return;
  const envPath = path.join(os.homedir(), ".ollama", "skills", "origin", ".env");
  if (!fs.existsSync(envPath)) return;
  const line = fs.readFileSync(envPath, "utf8").split(/\r?\n/).find((raw) => /^\s*GH_TOKEN\s*=/.test(raw));
  if (!line) return;
  process.env.GH_TOKEN = line.replace(/^\s*GH_TOKEN\s*=\s*/, "").trim().replace(/^['"]|['"]$/g, "");
}

loadGithubToken();

let githubAskpassPath = null;
if (process.env.GH_TOKEN || process.env.GITHUB_TOKEN) {
  githubAskpassPath = path.join(os.tmpdir(), `ava-github-askpass-${process.pid}.sh`);
  fs.writeFileSync(githubAskpassPath, '#!/bin/sh\ncase "$1" in\n  *[Uu]sername*) printf "%s\\n" x-access-token ;;\n  *) printf "%s\\n" "${GH_TOKEN:-${GITHUB_TOKEN}}" ;;\nesac\n', { mode: 0o700 });
  process.on("exit", () => { try { fs.unlinkSync(githubAskpassPath); } catch {} });
}

function authEnv() {
  if (!githubAskpassPath) return process.env;
  return { ...process.env, GIT_ASKPASS: githubAskpassPath, GIT_USERNAME: "x-access-token", GIT_TERMINAL_PROMPT: "0" };
}

const SECRET_BASENAMES = new Set([
  ".env",
  "credentials.env",
  "credentials.env.rootrecord",
  "google-services.json",
  "cloud.yml",
  "database.yml",
]);
const SECRET_EXT = [".pem", ".p12", ".jks", ".keystore", ".token"];
const SKIP_DIR_PARTS = [
  "/node_modules/",
  "/.venv/",
  "/dist/",
  "/build/",
  "/.gradle/",
  "/.wrangler/",
  "/uploads/",
  "/__pycache__/",
  "/.cache/",
  "/Server Handoffs/",
  "/Server Live Backups/",
];

/** Live git checkouts that already track Ava-Core-Dev remotes. No extra copies. */
const LIVE_REPOS = [
  {
    id: "ava-core",
    dir: AVA_CORE_V2,
    remoteUrl: "https://github.com/Ava-Core-Dev/ava-core.git",
    defaultBranch: "master",
    alsoDev: true,
  },
  {
    id: "all-connections",
    dir: path.join(HANDOFF, "All-Connections"),
    remoteUrl: "https://github.com/Ava-Core-Dev/all-connections.git",
    defaultBranch: "main",
    alsoDev: true,
  },
  {
    id: "ollama-skills",
    dir: SKILLS,
    remoteUrl: "https://github.com/Ava-Core-Dev/ollama-skills.git",
    defaultBranch: "main",
    alsoDev: false,
    createPrivate: true,
  },
  // Vercel sites: git lives in site/.git only. Never use a second work tree or GIT_DIR.
  {
    id: "avaivy-cloud",
    dir: path.join(SKILLS, "avaivy-cloud", "site"),
    remoteUrl: "https://github.com/Ava-Core-Dev/Ava-Ivy-Cloud.git",
    defaultBranch: "main",
    alsoDev: false,
  },
  {
    id: "rootrecord-online",
    dir: path.join(SKILLS, "rootrecord-online", "site"),
    remoteUrl: "https://github.com/Ava-Core-Dev/RootRecord-Cloud.git",
    defaultBranch: "main",
    alsoDev: false,
  },
  {
    id: "alexrs94-site",
    dir: path.join(SKILLS, "alexrs94-site", "site"),
    remoteUrl: "https://github.com/Ava-Core-Dev/alexrs94-site.git",
    defaultBranch: "main",
    alsoDev: false,
  },
  {
    id: "holding",
    dir: path.join(SKILLS, "holding", "site"),
    remoteUrl: "https://github.com/Ava-Core-Dev/holding.git",
    defaultBranch: "main",
    alsoDev: false,
  },
  {
    id: "freeltc-site",
    dir: path.join(SKILLS, "freeltc", "site"),
    remoteUrl: "https://github.com/Ava-Core-Dev/freeltc-site.git",
    defaultBranch: "main",
    alsoDev: false,
  },
  {
    id: "rootmc-mobile-web",
    dir: path.join(SKILLS, "rootmc-mobile-web", "site"),
    remoteUrl: "https://github.com/Ava-Core-Dev/rootmc-mobile.git",
    defaultBranch: "main",
    alsoDev: false,
  },
];

/**
 * Mirror specs intentionally empty: never rsync into a second checkout under
 * ~/.ollama/skills. Sites and desk code push from their live folders only.
 */
const MIRROR_REPOS = [];

function gitCwd(repoOrDir) {
  if (repoOrDir && typeof repoOrDir === "object" && repoOrDir.dir) return repoOrDir.dir;
  return repoOrDir;
}

function git(cwd, args, opts = {}) {
  const extraEnv = opts.env || {};
  const env = { ...authEnv(), ...extraEnv };
  const r = spawnSync("git", args, {
    cwd: gitCwd(cwd),
    encoding: "utf8",
    maxBuffer: 64 * 1024 * 1024,
    ...opts,
    env,
  });
  return {
    ok: r.status === 0,
    status: r.status,
    stdout: (r.stdout || "").trim(),
    stderr: (r.stderr || "").trim(),
  };
}

function sh(cwd, command) {
  const r = spawnSync("bash", ["-lc", command], {
    cwd,
    encoding: "utf8",
    maxBuffer: 64 * 1024 * 1024,
    env: authEnv(),
  });
  return {
    ok: r.status === 0,
    status: r.status,
    stdout: (r.stdout || "").trim(),
    stderr: (r.stderr || "").trim(),
  };
}

function toPosix(p) {
  return String(p).replace(/\\/g, "/");
}

function isSecretPath(relPosix) {
  const base = path.posix.basename(relPosix);
  if (SECRET_BASENAMES.has(base)) return true;
  if (SECRET_EXT.some((e) => base.toLowerCase().endsWith(e))) return true;
  if (/\.env(\.|$)/i.test(base)) return true;
  if (/credentials/i.test(base) && /\.(env|json|yml|yaml)$/i.test(base)) return true;
  const norm = `/${relPosix}/`;
  if (SKIP_DIR_PARTS.some((s) => norm.includes(s))) return true;
  return false;
}

function parseOnlyFilter() {
  const raw = String(process.env.AVA_GITHUB_PUSH_ONLY || "").trim();
  if (!raw) return null;
  return new Set(
    raw
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean),
  );
}

function githubApi(method, apiPath, body) {
  const token = process.env.GH_TOKEN || process.env.GITHUB_TOKEN || "";
  if (!token) return { ok: false, status: 0, json: {}, reason: "no_token" };
  const args = [
    "-sS",
    "-X",
    method,
    "-H",
    `Authorization: Bearer ${token}`,
    "-H",
    "Accept: application/vnd.github+json",
    "-H",
    "X-GitHub-Api-Version: 2022-11-28",
    "-w",
    "\n%{http_code}",
    `https://api.github.com${apiPath}`,
  ];
  if (body) {
    args.splice(args.length - 1, 0, "-H", "Content-Type: application/json", "-d", JSON.stringify(body));
  }
  const r = spawnSync("curl", args, { encoding: "utf8", maxBuffer: 8 * 1024 * 1024 });
  const raw = (r.stdout || "").trim();
  const nl = raw.lastIndexOf("\n");
  const status = Number(nl >= 0 ? raw.slice(nl + 1) : raw);
  let json = {};
  try {
    json = JSON.parse(nl >= 0 ? raw.slice(0, nl) : "{}");
  } catch {
    json = {};
  }
  return { ok: r.status === 0 && status >= 200 && status < 300, status, json };
}

function parseOwnerName(remoteUrl) {
  const m = String(remoteUrl).match(/github\.com[:/]+([^/]+)\/([^/.]+)(?:\.git)?$/i);
  if (!m) return null;
  return { owner: m[1], name: m[2] };
}

function ensureGithubRepo(repo) {
  const parsed = parseOwnerName(repo.remoteUrl);
  if (!parsed) return { ok: false, reason: "bad_remote" };
  const check = githubApi("GET", `/repos/${parsed.owner}/${parsed.name}`);
  if (check.ok) return { ok: true, existed: true };
  if (check.status && check.status !== 404) {
    return { ok: false, reason: "github_lookup_failed", status: check.status };
  }
  const payload = {
    name: parsed.name,
    private: !!repo.createPrivate,
    description: repo.createPrivate ? "OmniBook skills desk (private). Canonical tree ~/.ollama/skills." : parsed.name,
    auto_init: false,
  };
  let created = githubApi("POST", `/orgs/${parsed.owner}/repos`, payload);
  if (!created.ok) {
    created = githubApi("POST", "/user/repos", payload);
  }
  if (!created.ok) {
    return { ok: false, reason: "github_create_failed", status: created.status };
  }
  return { ok: true, created: true };
}

function hasGit(repo) {
  return fs.existsSync(path.join(repo.dir, ".git"));
}

function ensureLiveRepo(repo) {
  const realDir = fs.existsSync(repo.dir) ? fs.realpathSync(repo.dir) : repo.dir;
  repo.dir = realDir;
  if (!fs.existsSync(repo.dir)) return { ok: false, reason: "missing_dir" };
  if (!hasGit(repo)) {
    const init = git(repo, ["init"]);
    if (!init.ok && !hasGit(repo)) {
      return { ok: false, reason: "init_failed", detail: init.stderr || init.stdout };
    }
    git(repo, ["checkout", "-B", repo.defaultBranch || "main"]);
  }
  const remotes = git(repo, ["remote", "-v"]).stdout;
  if (!/origin\t/.test(remotes)) {
    const add = git(repo, ["remote", "add", "origin", repo.remoteUrl]);
    if (!add.ok) return { ok: false, reason: "remote_add_failed", detail: add.stderr };
  } else {
    git(repo, ["remote", "set-url", "origin", repo.remoteUrl]);
  }
  if (repo.createPrivate) {
    const gh = ensureGithubRepo(repo);
    if (!gh.ok && gh.status === 401) {
      // Local git still tracks the folder; push waits on a live GH_TOKEN.
    } else if (!gh.ok) {
      return gh;
    }
  }
  git(repo, ["fetch", "origin", repo.defaultBranch || "main"]);
  if (!repo.createPrivate) {
    const parsed = parseOwnerName(repo.remoteUrl);
    if (parsed) {
      const check = githubApi("GET", `/repos/${parsed.owner}/${parsed.name}`);
      if (!check.ok && check.status === 404) {
        return { ok: false, reason: "remote_missing" };
      }
    }
  }
  const head = git(repo, ["rev-parse", "--verify", "HEAD"]);
  if (!head.ok) {
    const remoteHead = git(repo, ["rev-parse", "--verify", `origin/${repo.defaultBranch || "main"}`]);
    if (remoteHead.ok) {
      git(repo, ["checkout", "-B", repo.defaultBranch || "main", `origin/${repo.defaultBranch || "main"}`]);
    } else {
      git(repo, ["checkout", "-B", repo.defaultBranch || "main"]);
    }
  }
  return { ok: true };
}

function ensureRemote(dir, url) {
  const remotes = git(dir, ["remote", "-v"]).stdout;
  if (!/origin\t/.test(remotes)) {
    const add = git(dir, ["remote", "add", "origin", url]);
    if (!add.ok) return { ok: false, reason: "remote_add_failed", detail: add.stderr };
  }
  return { ok: true };
}

function unstageSecrets(dir) {
  const staged = git(dir, ["diff", "--cached", "--name-only"]);
  if (!staged.ok || !staged.stdout) return [];
  const bad = [];
  for (const f of staged.stdout.split("\n")) {
    const file = f.trim();
    if (!file) continue;
    if (isSecretPath(toPosix(file))) bad.push(file);
  }
  if (bad.length) {
    git(dir, ["restore", "--staged", "--", ...bad]);
  }
  return bad;
}

function commitIfDirty(dir, message) {
  git(dir, ["add", "-A"]);
  const unstaged = unstageSecrets(dir);
  const dirty = !git(dir, ["diff", "--cached", "--quiet"]).ok;
  if (!dirty) {
    return { committed: false, unstagedSecrets: unstaged, fileCount: 0 };
  }
  const stat = git(dir, ["diff", "--cached", "--stat"]).stdout.split("\n").pop() || "";
  const msg =
    String(message || "").trim() ||
    `Ava: sync ${new Date().toISOString().slice(0, 16).replace("T", " ")} UTC\n\n${stat}`;
  const commit = git(dir, ["commit", "-m", msg], {
    env: {
      ...authEnv(),
      GIT_AUTHOR_NAME: process.env.GIT_AUTHOR_NAME || "Ava Ivy",
      GIT_AUTHOR_EMAIL: process.env.GIT_AUTHOR_EMAIL || "ava@rootrecord.info",
      GIT_COMMITTER_NAME: process.env.GIT_COMMITTER_NAME || "Ava Ivy",
      GIT_COMMITTER_EMAIL: process.env.GIT_COMMITTER_EMAIL || "ava@rootrecord.info",
    },
  });
  if (!commit.ok && !/nothing to commit/i.test(commit.stdout + commit.stderr)) {
    return {
      committed: false,
      ok: false,
      reason: "commit_failed",
      detail: commit.stderr || commit.stdout,
      unstagedSecrets: unstaged,
    };
  }
  const count = git(dir, ["diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD"]);
  return {
    committed: true,
    ok: true,
    message: msg,
    fileCount: count.stdout ? count.stdout.split("\n").filter(Boolean).length : 0,
    unstagedSecrets: unstaged,
  };
}

function pushBranch(dir, localRef, remoteBranch, { setUpstream = false } = {}) {
  const isProtected = /^(main|master)$/i.test(remoteBranch);
  const args = setUpstream
    ? ["push", "-u", "origin", `${localRef}:${remoteBranch}`]
    : ["push", "origin", `${localRef}:${remoteBranch}`];
  let push = git(dir, args);
  if (!push.ok && /rejected|non-fast-forward|behind/i.test(push.stderr + push.stdout)) {
    if (isProtected) {
      const pull = git(dir, [
        "pull",
        "--rebase",
        "--autostash",
        "origin",
        remoteBranch,
      ]);
      if (!pull.ok) {
        return {
          ok: false,
          reason: "pull_rebase_failed",
          detail: pull.stderr || pull.stdout,
          note: "refusing force-push on main/master",
        };
      }
      push = git(dir, args);
    } else {
      // Dev branch may diverge; still never --force on main. For `dev`, allow
      // a non-destructive update via +ref only when AVA_GITHUB_PUSH_FORCE_DEV=1.
      if (String(process.env.AVA_GITHUB_PUSH_FORCE_DEV || "") === "1") {
        push = git(dir, ["push", "origin", `+${localRef}:${remoteBranch}`]);
      } else {
        push = git(dir, args);
      }
    }
  }
  if (!push.ok) {
    return {
      ok: false,
      reason: "push_failed",
      detail: push.stderr || push.stdout,
      branch: remoteBranch,
    };
  }
  return { ok: true, branch: remoteBranch };
}

function pushDefaultAndDev(dir, defaultBranch, alsoDev) {
  const head = git(dir, ["rev-parse", "--abbrev-ref", "HEAD"]).stdout || defaultBranch;
  const results = [];
  const primary = pushBranch(dir, "HEAD", head, { setUpstream: true });
  results.push({ target: head, ...primary });
  if (alsoDev && !/^dev$/i.test(head)) {
    // Keep a rolling `dev` pointer at the same commit (do not set upstream).
    const dev = pushBranch(dir, "HEAD", "dev", { setUpstream: false });
    results.push({ target: "dev", ...dev });
  }
  const ok = results.every((r) => r.ok);
  return { ok, results };
}

function ensureMirrorClone(id, remoteUrl, defaultBranch) {
  const spec = MIRROR_REPOS.find((item) => item.id === id);
  const dir = spec?.checkoutDir || path.join(MIRRORS, id);
  fs.mkdirSync(MIRRORS, { recursive: true });
  if (!fs.existsSync(path.join(dir, ".git"))) {
    const clone = sh(MIRRORS, `git clone --branch ${defaultBranch} --single-branch ${JSON.stringify(remoteUrl)} ${JSON.stringify(id)}`);
    if (!clone.ok) {
      // Repo may be empty or branch name differs — try plain clone
      const clone2 = sh(MIRRORS, `git clone ${JSON.stringify(remoteUrl)} ${JSON.stringify(id)}`);
      if (!clone2.ok) {
        return { ok: false, dir, reason: "clone_failed", detail: clone2.stderr || clone.stderr };
      }
    }
  }
  git(dir, ["remote", "set-url", "origin", remoteUrl]);
  git(dir, ["fetch", "origin", defaultBranch]);
  const checkout = git(dir, ["checkout", defaultBranch]);
  if (!checkout.ok) {
    git(dir, ["checkout", "-B", defaultBranch, `origin/${defaultBranch}`]);
  }
  git(dir, ["pull", "--ff-only", "origin", defaultBranch]);
  return { ok: true, dir };
}

function rsyncInto(fromAbs, mirrorDir, relTo) {
  if (!fs.existsSync(fromAbs)) {
    return { ok: true, skipped: true, reason: "source_missing", from: fromAbs };
  }
  const dest = path.join(mirrorDir, relTo);
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  const excludes = [
    "--exclude=.env",
    "--exclude=.env.*",
    "--exclude=credentials.env",
    "--exclude=credentials.env.*",
    "--exclude=node_modules",
    "--exclude=.venv",
    "--exclude=dist",
    "--exclude=build",
    "--exclude=.gradle",
    "--exclude=.wrangler",
    "--exclude=__pycache__",
    "--exclude=*.pem",
    "--exclude=*.p12",
    "--exclude=*.jks",
    "--exclude=*.keystore",
    "--exclude=*.log",
    "--exclude=.git",
    "--exclude=data/ecoflow",
    "--exclude=data/billing",
    "--exclude=*.db",
    "--exclude=*.sqlite*",
  ].join(" ");
  const cmd = `rsync -a --delete ${excludes} ${JSON.stringify(fromAbs.replace(/\/?$/, "/"))} ${JSON.stringify(dest.replace(/\/?$/, "/"))}`;
  const r = sh(mirrorDir, cmd);
  return {
    ok: r.ok,
    from: fromAbs,
    to: relTo,
    detail: r.ok ? undefined : r.stderr || r.stdout,
  };
}

function syncMirror(spec) {
  const clone = ensureMirrorClone(spec.id, spec.remoteUrl, spec.defaultBranch);
  if (!clone.ok) return clone;
  const syncResults = [];
  for (const step of spec.sync) {
    syncResults.push(rsyncInto(step.from, clone.dir, step.to));
  }
  return { ok: true, dir: clone.dir, syncResults };
}

function aheadOfUpstream(dir) {
  const up = git(dir, ["rev-parse", "--abbrev-ref", "@{u}"]);
  if (!up.ok) return true;
  const counts = git(dir, ["rev-list", "--count", "@{u}..HEAD"]);
  return Number(counts.stdout || 0) > 0;
}

export async function runAvaGithubPush({
  message = "",
  dryRun = false,
  only = null,
} = {}) {
  const filter = only || parseOnlyFilter();
  const started = new Date().toISOString();
  const out = {
    ok: true,
    started,
    host: os.hostname(),
    dryRun: !!dryRun,
    repos: [],
  };

  for (const repo of LIVE_REPOS) {
    if (filter && !filter.has(repo.id)) continue;
    const entry = { id: repo.id, kind: "live", dir: repo.dir };
    const ready = ensureLiveRepo(repo);
    entry.dir = repo.dir;
    if (!ready.ok) {
      if (ready.reason === "remote_missing" || ready.reason === "missing_dir") {
        entry.ok = true;
        entry.reason = ready.reason === "missing_dir" ? "skipped_missing_dir" : "skipped_no_github";
        out.repos.push(entry);
        continue;
      }
      entry.ok = false;
      entry.reason = ready.reason;
      entry.detail = ready.detail || ready.status;
      out.repos.push(entry);
      out.ok = false;
      continue;
    }
    if (dryRun) {
      const st = git(repo, ["status", "--porcelain"]);
      entry.ok = true;
      entry.reason = "dry_run";
      entry.dirtyLines = st.stdout ? st.stdout.split("\n").filter(Boolean).length : 0;
      out.repos.push(entry);
      continue;
    }
    const committed = commitIfDirty(repo, message);
    entry.commit = committed;
    if (committed.ok === false) {
      entry.ok = false;
      out.ok = false;
      out.repos.push(entry);
      continue;
    }
    if (!committed.committed && !aheadOfUpstream(repo)) {
      if (repo.alsoDev) {
        const tip = git(repo, ["rev-parse", "HEAD"]).stdout;
        const remoteDev = git(repo, ["ls-remote", "--heads", "origin", "dev"]).stdout;
        if (tip && !remoteDev.includes(tip)) {
          entry.push = pushDefaultAndDev(repo, repo.defaultBranch, true);
          entry.ok = entry.push.ok;
          if (!entry.ok) out.ok = false;
        } else {
          entry.ok = true;
          entry.reason = "clean";
        }
      } else {
        entry.ok = true;
        entry.reason = "clean";
      }
      out.repos.push(entry);
      continue;
    }
    entry.push = pushDefaultAndDev(repo, repo.defaultBranch, repo.alsoDev);
    entry.ok = entry.push.ok;
    if (!entry.ok) out.ok = false;
    out.repos.push(entry);
  }

  for (const spec of MIRROR_REPOS) {
    if (filter && !filter.has(spec.id)) continue;
    const entry = { id: spec.id, kind: "mirror", remoteUrl: spec.remoteUrl };
    if (dryRun) {
      entry.ok = true;
      entry.reason = "dry_run";
      entry.wouldSync = spec.sync.map((s) => ({
        from: s.from,
        to: s.to,
        exists: fs.existsSync(s.from),
      }));
      out.repos.push(entry);
      continue;
    }
    const synced = syncMirror(spec);
    entry.sync = synced;
    if (!synced.ok) {
      entry.ok = false;
      out.ok = false;
      out.repos.push(entry);
      continue;
    }
    entry.dir = synced.dir;
    const committed = commitIfDirty(synced.dir, message);
    entry.commit = committed;
    if (committed.ok === false) {
      entry.ok = false;
      out.ok = false;
      out.repos.push(entry);
      continue;
    }
    if (!committed.committed && !aheadOfUpstream(synced.dir)) {
      if (spec.alsoDev) {
        const tip = git(synced.dir, ["rev-parse", "HEAD"]).stdout;
        const remoteDev = git(synced.dir, ["ls-remote", "--heads", "origin", "dev"]).stdout;
        if (tip && !remoteDev.includes(tip)) {
          entry.push = pushDefaultAndDev(synced.dir, spec.defaultBranch, true);
          entry.ok = entry.push.ok;
          if (!entry.ok) out.ok = false;
        } else {
          entry.ok = true;
          entry.reason = "clean";
        }
      } else {
        entry.ok = true;
        entry.reason = "clean";
      }
      out.repos.push(entry);
      continue;
    }
    entry.push = pushDefaultAndDev(synced.dir, spec.defaultBranch, spec.alsoDev);
    entry.ok = entry.push.ok;
    if (!entry.ok) out.ok = false;
    out.repos.push(entry);
  }

  out.finished = new Date().toISOString();
  return out;
}

const isMain =
  process.argv[1] &&
  path.normalize(process.argv[1]).includes("ava-github-push.mjs");

if (isMain) {
  const args = process.argv.slice(2);
  const dry = args.includes("--dry-run");
  const message = args.filter((a) => a !== "--dry-run").join(" ").trim();
  const result = await runAvaGithubPush({ message, dryRun: dry });
  console.log(JSON.stringify(result, null, 2));
  process.exit(result.ok ? 0 : 1);
}
