import assert from 'node:assert/strict';
import test from 'node:test';
import {
  buildAiModelCandidates,
  compareReleaseTags,
  findPreviousReleaseTag,
  parseReleaseTag,
  resolveCurrentTag,
  shouldRetryWithFallbackModel,
} from './changelog-release-tags.mjs';

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

test('buildAiModelCandidates deduplicates models in order', () => {
  const models = buildAiModelCandidates({
    explicitModel: '@cf/custom/model',
    defaultModel: '@cf/default/model',
    fallbackModels: ['@cf/fallback/model', '@cf/default/model'],
    envFallback: '@cf/env/model',
  });
  assert.deepEqual(models, ['@cf/custom/model', '@cf/fallback/model', '@cf/default/model']);
});

test('shouldRetryWithFallbackModel covers transient and removed models', () => {
  assert.equal(shouldRetryWithFallbackModel(410), true);
  assert.equal(shouldRetryWithFallbackModel(401), false);
});