import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import {
  buildAiModelCandidates,
  compareReleaseTags,
  findPreviousReleaseTag,
  parseReleaseTag,
  resolveCurrentTag,
  resolveReleaseTags,
  shouldRetryWithFallbackModel,
} from './changelog-release-tags.mjs';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const changelogScript = join(scriptDir, 'generate-ai-changelog.mjs');

test('parseReleaseTag accepts bare and v-prefixed semver tags', () => {
  assert.deepEqual(parseReleaseTag('8.10.0'), {
    major: 8,
    minor: 10,
    patch: 0,
    prerelease: null,
    raw: '8.10.0',
  });
  assert.deepEqual(parseReleaseTag('v8.9.17-alpha.3')?.prerelease, 'alpha.3');
  assert.equal(parseReleaseTag('not-a-tag'), null);
});

test('compareReleaseTags orders numeric semver segments', () => {
  assert.ok(compareReleaseTags('8.9.17', '8.10.0') < 0);
  assert.ok(compareReleaseTags('8.10.0', '8.9.17') > 0);
  assert.equal(compareReleaseTags('8.10.0', '8.10.0'), 0);
  assert.ok(compareReleaseTags('8.10.0-alpha.1', '8.10.0') < 0);
  assert.ok(compareReleaseTags('1.0.0-alpha', '1.0.0-Beta') > 0);
});

test('findPreviousReleaseTag returns latest tag before current', () => {
  const tags = ['8.8.0', '8.9.17', '8.10.0', '7.9.0', 'not-a-tag', '8.10.0-alpha.1'];
  assert.equal(findPreviousReleaseTag('8.10.0', tags), '8.9.17');
  assert.equal(findPreviousReleaseTag('8.9.17', tags), '8.8.0');
  assert.equal(findPreviousReleaseTag('7.9.0', tags), null);
});

test('resolveCurrentTag reads GITHUB_REF and explicit overrides', () => {
  assert.equal(resolveCurrentTag({ toTag: '8.10.0', githubRef: 'refs/tags/8.9.17' }), '8.10.0');
  assert.equal(resolveCurrentTag({ githubRef: 'refs/tags/8.10.0' }), '8.10.0');
  assert.throws(() => resolveCurrentTag({ githubRef: 'refs/heads/main' }), /Missing target tag/);
});

test('resolveReleaseTags prefers explicit fromTag and same-major git describe', () => {
  const resolved = resolveReleaseTags({
    githubRef: 'refs/tags/8.10.0',
    git: (args) => {
      if (args.includes('8.10.0^')) {
        return '8.9.17';
      }
      throw new Error(`unexpected git args: ${args.join(' ')}`);
    },
    listTags: () => ['8.8.0', '8.9.17', '8.10.0'],
  });
  assert.deepEqual(resolved, { previousTag: '8.9.17', currentTag: '8.10.0' });

  assert.deepEqual(
    resolveReleaseTags({
      fromTag: '7.0.0',
      githubRef: 'refs/tags/8.10.0',
      git: () => {
        throw new Error('git should not run when fromTag is set');
      },
      listTags: () => [],
    }),
    { previousTag: '7.0.0', currentTag: '8.10.0' },
  );
});

test('resolveReleaseTags falls back to sorted tag list when describe fails', () => {
  const resolved = resolveReleaseTags({
    githubRef: 'refs/tags/8.10.0',
    git: () => {
      throw new Error('describe failed');
    },
    listTags: () => ['8.8.0', '8.9.17', '8.10.0'],
  });
  assert.deepEqual(resolved, { previousTag: '8.9.17', currentTag: '8.10.0' });
});

test('buildAiModelCandidates deduplicates models in order', () => {
  const models = buildAiModelCandidates({
    explicitModel: '@cf/custom/model',
    defaultModel: '@cf/default/model',
    fallbackModels: ['@cf/fallback/model', '@cf/default/model'],
    envFallback: '@cf/env/model',
  });
  assert.deepEqual(models, ['@cf/custom/model', '@cf/fallback/model', '@cf/default/model']);

  const envFirst = buildAiModelCandidates({
    defaultModel: '@cf/default/model',
    fallbackModels: ['@cf/fallback/model'],
    envFallback: '@cf/env/model',
  });
  assert.deepEqual(envFirst, ['@cf/env/model', '@cf/fallback/model']);
});

test('shouldRetryWithFallbackModel covers transient and removed models', () => {
  assert.equal(shouldRetryWithFallbackModel(404), true);
  assert.equal(shouldRetryWithFallbackModel(410), true);
  assert.equal(shouldRetryWithFallbackModel(503), true);
  assert.equal(shouldRetryWithFallbackModel(401), false);
});

test('generate-ai-changelog writes compare tags before AI credentials failure', () => {
  const dir = mkdtempSync(join(tmpdir(), 'changelog-ai-'));
  try {
    execFileSync('git', ['init', '-b', 'main'], { cwd: dir });
    execFileSync('git', ['config', 'user.email', 'agent@capgo.app'], { cwd: dir });
    execFileSync('git', ['config', 'user.name', 'Agent'], { cwd: dir });
    writeFileSync(join(dir, 'README.md'), 'one\n');
    execFileSync('git', ['add', 'README.md'], { cwd: dir });
    execFileSync('git', ['commit', '-m', 'one'], { cwd: dir });
    execFileSync('git', ['tag', '1.0.0'], { cwd: dir });
    writeFileSync(join(dir, 'README.md'), 'two\n');
    execFileSync('git', ['add', 'README.md'], { cwd: dir });
    execFileSync('git', ['commit', '-m', 'two'], { cwd: dir });
    execFileSync('git', ['tag', '1.1.0'], { cwd: dir });

    const outputPath = join(dir, 'github-output.txt');
    const result = spawnSync(process.execPath, [changelogScript], {
      cwd: dir,
      env: {
        ...process.env,
        GITHUB_REF: 'refs/tags/1.1.0',
        GITHUB_OUTPUT: outputPath,
        CLOUDFLARE_ACCOUNT_ID: '',
        CLOUDFLARE_API_TOKEN: '',
      },
    });

    assert.notEqual(result.status, 0);
    const output = readFileSync(outputPath, 'utf8');
    assert.match(output, /from_tag=1\.0\.0/);
    assert.match(output, /to_tag=1\.1\.0/);
    assert.doesNotMatch(output, /^result<</m);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});