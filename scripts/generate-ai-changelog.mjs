#!/usr/bin/env node
/**
 * Generate a release changelog with Cloudflare Workers AI in CI.
 * Invoked from build.yml on tag releases via `bun run changelog:ai`.
 */

import { execFileSync } from 'node:child_process';
import { appendFileSync } from 'node:fs';
import {
  buildAiModelCandidates,
  resolveReleaseTags,
  shouldRetryWithFallbackModel,
} from './changelog-release-tags.mjs';

export class ChangelogAiRequestError extends Error {
  /**
   * @param {number} status
   * @param {string} message
   */
  constructor(status, message) {
    super(message);
    this.name = 'ChangelogAiRequestError';
    this.status = status;
  }
}

const DEFAULT_MODEL = '@cf/zai-org/glm-4.7-flash';
const FALLBACK_MODELS = ['@cf/moonshotai/kimi-k2.6', '@cf/google/gemma-4-26b-a4b-it'];

function parseArgs(argv) {
  const args = { fromTag: process.env.FROM_TAG, toTag: process.env.TO_TAG };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--from-tag') args.fromTag = argv[++i];
    if (argv[i] === '--to-tag') args.toTag = argv[++i];
    if (argv[i] === '--model') args.model = argv[++i];
  }
  return args;
}

function git(args) {
  return execFileSync('/usr/bin/git', args, { encoding: 'utf8' }).trim();
}

function listReleaseTags() {
  return git(['tag', '--list', '--sort=v:refname'])
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean);
}

function resolveTags({ fromTag, toTag }) {
  return resolveReleaseTags({
    fromTag,
    toTag,
    githubRef: process.env.GITHUB_REF,
    git,
    listTags: listReleaseTags,
  });
}

function buildPrompt(previousTag, currentTag) {
  const commits = git(['log', `${previousTag}..${currentTag}`, '--pretty=format:%s']);
  const diffLines = git(['diff', '--stat', `${previousTag}..${currentTag}`]).split('\n');
  const diffStat = diffLines.at(-1)?.trim() ?? '';

  return `You are a technical writer creating a changelog for a software project.

There are changes in the git repository between two points:
- From tag: ${previousTag}
- To tag: ${currentTag}
- Commits:
${commits || '(none)'}
- Diff summary: ${diffStat || '(no file changes)'}

## Instructions:
1. **Categorize changes** using standard changelog categories:
   - **Added**: New features
   - **Changed**: Changes in existing functionality
   - **Deprecated**: Soon-to-be removed features
   - **Removed**: Removed features
   - **Fixed**: Bug fixes
   - **Security**: Security improvements

2. **Write clear, user-focused descriptions** that explain:
   - What changed from a user's perspective
   - Why it matters
   - Any breaking changes or migration notes

3. **Use consistent formatting**:
   - Each item should be a concise bullet point
   - Start with an action verb when possible
   - Group related changes together

4. **Focus on semantic meaning** rather than technical implementation details

Output ONLY the changelog content in markdown format. Do NOT include any explanatory text, metadata, introductory phrases like "Based on the git analysis" or "Here's the changelog", or concluding remarks. Start IMMEDIATELY with the changelog categories and items (e.g., "## Added"). Do not include version numbers or dates. Do not preface your response with any text whatsoever.`;
}

async function generateChangelog(prompt, model) {
  const accountId = process.env.CLOUDFLARE_ACCOUNT_ID;
  const apiToken = process.env.CLOUDFLARE_API_TOKEN;

  if (!accountId || !apiToken) {
    throw new Error('CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN are required.');
  }

  const configuredTimeoutMs = Number(process.env.CHANGELOG_AI_TIMEOUT_MS || 60000);
  const timeoutMs = Number.isFinite(configuredTimeoutMs)
    ? Math.min(600000, Math.max(1000, Math.trunc(configuredTimeoutMs)))
    : 60000;
  const url = `https://api.cloudflare.com/client/v4/accounts/${accountId}/ai/run/${model}`;
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${apiToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      messages: [{ role: 'user', content: prompt }],
    }),
    signal: AbortSignal.timeout(timeoutMs),
  });

  const bodyText = await response.text();
  let payload = {};
  try {
    payload = bodyText ? JSON.parse(bodyText) : {};
  } catch {
    payload = {};
  }
  if (!response.ok || !payload.success) {
    const detail = Array.isArray(payload.errors)
      ? payload.errors.map((entry) => entry?.message).filter(Boolean).join('; ')
      : '';
    const suffix = detail ? ` (${detail})` : bodyText && !detail ? ` (${bodyText.slice(0, 200)})` : '';
    throw new ChangelogAiRequestError(
      response.status,
      `Cloudflare AI request failed with status ${response.status}${suffix}`,
    );
  }

  const content = payload.result?.choices?.[0]?.message?.content ?? payload.result?.response;
  if (!content || typeof content !== 'string') {
    throw new Error('Cloudflare AI returned an empty changelog response');
  }

  return content.trim();
}

async function generateChangelogWithFallback(prompt, models) {
  let lastError;
  for (const model of models) {
    console.error(`Trying Cloudflare AI model ${model}`);
    try {
      return await generateChangelog(prompt, model);
    } catch (error) {
      lastError = error;
      const status =
        error instanceof ChangelogAiRequestError
          ? error.status
          : Number(error instanceof Error ? error.message.match(/status (\d+)/)?.[1] : 0);
      if (!shouldRetryWithFallbackModel(status)) {
        throw error;
      }
      console.error(
        `Model ${model} failed (${error instanceof Error ? error.message : 'Unknown error'}); trying next fallback.`,
      );
    }
  }
  throw lastError ?? new Error('Cloudflare AI request failed');
}

function writeGithubOutput({ result, fromTag, toTag }) {
  const outputFile = process.env.GITHUB_OUTPUT;
  if (!outputFile) return;

  if (fromTag) {
    appendFileSync(outputFile, `from_tag=${fromTag}\n`);
  }
  if (toTag) {
    appendFileSync(outputFile, `to_tag=${toTag}\n`);
  }
  if (result) {
    const delimiter = `changelog_${Date.now()}`;
    appendFileSync(outputFile, `result<<${delimiter}\n${result}\n${delimiter}\n`);
  }
}

const args = parseArgs(process.argv.slice(2));
const envFallbacks =
  process.env.CHANGELOG_AI_FALLBACK_MODELS?.split(',')
    .map((entry) => entry.trim())
    .filter(Boolean) ?? [];
const models = buildAiModelCandidates({
  explicitModel: args.model,
  defaultModel: DEFAULT_MODEL,
  fallbackModels: envFallbacks.length ? envFallbacks : FALLBACK_MODELS,
  envFallback: process.env.CLOUDFLARE_AI_MODEL,
});
const { previousTag, currentTag } = resolveTags(args);
const prompt = buildPrompt(previousTag, currentTag);

console.error(`Generating changelog (models: ${models.join(', ')})`);
console.error(`Range: ${previousTag}..${currentTag}`);
writeGithubOutput({ fromTag: previousTag, toTag: currentTag });

try {
  const result = await generateChangelogWithFallback(prompt, models);
  writeGithubOutput({ result });
} catch (error) {
  const message = error instanceof Error ? error.message : 'Unknown error';
  console.error(`Changelog generation failed: ${message}`);
  process.exit(1);
}