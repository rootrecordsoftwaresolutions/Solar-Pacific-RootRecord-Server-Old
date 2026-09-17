/**
 * PROP C: strip shop-competitive armor trims + enchanted books from redeem pool.
 */
import fs from "node:fs";

const path =
  "D:/.1 Work Stations/RootMC/Plugin Building/Minecraft/plugins/root-appreciation/src/main/resources/appreciation-rewards.yml";
const raw = fs.readFileSync(path, "utf8");
const blocks = raw.split(/\n(?=- id: )/);
const rewards = blocks.slice(1);

function isBlocked(text) {
  if (/kind: enchanted_book/.test(text)) return true;
  if (/ARMOR_TRIM_SMITHING_TEMPLATE/.test(text)) return true;
  return false;
}

const kept = rewards.filter((b) => !isBlocked(b));
console.log({
  total: rewards.length,
  kept: kept.length,
  removed: rewards.length - kept.length,
});

const gamma = 1.2;
const count = kept.length;
const rankMax = count + 1;
const weights = [];
for (let i = 0; i < count; i++) {
  const rank = i + 1;
  weights.push(Math.pow(rankMax - rank, gamma));
}
const sum = weights.reduce((a, b) => a + b, 0);
const target = 1_000_000;
const outWeights = weights.map((w) => Math.max(1, Math.round((w / sum) * target)));
let drift = target - outWeights.reduce((a, b) => a + b, 0);
outWeights[outWeights.length - 1] += drift;

function rewriteBlock(block, rank, weight) {
  let b = block
    .replace(/\brank: \d+/, `rank: ${rank}`)
    .replace(/\bweight: \d+/, `weight: ${weight}`);
  if (!b.startsWith("- id:")) b = `- id:${b.split("- id:")[1]}`;
  return b.trimEnd();
}

const lines = [
  "# Redeem catalog — PROP onboarding/shop guardrails 2026-08-03",
  "# Armor trims + enchanted books removed (shop-competitive). Auto-reweighted.",
  "version: 2",
  "gamma: 1.2",
  "weight-sum: 1000000",
  `formula: "(${rankMax}-rank)^1.2"`,
  `count: ${count}`,
  "rewards:",
];
for (let i = 0; i < kept.length; i++) {
  lines.push(rewriteBlock(kept[i], i + 1, outWeights[i]));
}
fs.writeFileSync(path, `${lines.join("\n")}\n`);
console.log("wrote", path, "weightSum", outWeights.reduce((a, b) => a + b, 0));
