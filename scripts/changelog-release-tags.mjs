/**
 * Pure helpers for release tag resolution used by generate-ai-changelog.mjs.
 */

const RELEASE_TAG_RE = /^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$/;

export function parseReleaseTag(tag) {
  const match = tag.match(RELEASE_TAG_RE);
  if (!match) {
    return null;
  }
  return {
    major: Number(match[1]),
    minor: Number(match[2]),
    patch: Number(match[3]),
    prerelease: match[4] ?? null,
    raw: tag,
  };
}

function compareIdentifiers(a, b) {
  const aParts = a.split('.');
  const bParts = b.split('.');
  const length = Math.max(aParts.length, bParts.length);
  for (let i = 0; i < length; i++) {
    const aPart = aParts[i];
    const bPart = bParts[i];
    if (aPart === undefined) {
      return -1;
    }
    if (bPart === undefined) {
      return 1;
    }
    const aNum = /^\d+$/.test(aPart) ? Number(aPart) : null;
    const bNum = /^\d+$/.test(bPart) ? Number(bPart) : null;
    if (aNum !== null && bNum !== null) {
      if (aNum !== bNum) {
        return aNum - bNum;
      }
      continue;
    }
    if (aNum !== null) {
      return -1;
    }
    if (bNum !== null) {
      return 1;
    }
    if (aPart < bPart) {
      return -1;
    }
    if (aPart > bPart) {
      return 1;
    }
  }
  return 0;
}

/**
 * @returns {number} negative if a < b, positive if a > b, 0 if equal
 */
export function compareReleaseTags(a, b) {
  const left = typeof a === 'string' ? parseReleaseTag(a) : a;
  const right = typeof b === 'string' ? parseReleaseTag(b) : b;
  if (!left || !right) {
    throw new Error('compareReleaseTags requires valid semver release tags');
  }
  if (left.major !== right.major) {
    return left.major - right.major;
  }
  if (left.minor !== right.minor) {
    return left.minor - right.minor;
  }
  if (left.patch !== right.patch) {
    return left.patch - right.patch;
  }
  if (left.prerelease === right.prerelease) {
    return 0;
  }
  if (left.prerelease === null) {
    return 1;
  }
  if (right.prerelease === null) {
    return -1;
  }
  return compareIdentifiers(left.prerelease, right.prerelease);
}

export function resolveCurrentTag({ toTag, githubRef }) {
  const currentTag =
    toTag ?? (githubRef?.startsWith('refs/tags/') ? githubRef.replace('refs/tags/', '') : null);
  if (!currentTag) {
    throw new Error('Missing target tag. Set GITHUB_REF to refs/tags/<tag> or pass --to-tag.');
  }
  return currentTag;
}

/**
 * Pick the latest release tag strictly before currentTag from a sorted candidate list.
 */
export function findPreviousReleaseTag(currentTag, allTags) {
  const current = parseReleaseTag(currentTag);
  if (!current) {
    return null;
  }

  const sorted = allTags
    .filter((tag) => tag !== currentTag)
    .map(parseReleaseTag)
    .filter(Boolean)
    .filter((parsed) => {
      if (current.prerelease !== null) {
        return true;
      }
      return parsed.prerelease === null;
    })
    .sort(compareReleaseTags);

  let previous = null;
  for (const parsed of sorted) {
    if (compareReleaseTags(parsed, current) < 0) {
      previous = parsed;
      continue;
    }
    break;
  }
  return previous?.raw ?? null;
}

export function buildAiModelCandidates({ explicitModel, defaultModel, fallbackModels, envFallback }) {
  const list = [explicitModel ?? envFallback ?? defaultModel];
  for (const model of fallbackModels) {
    list.push(model);
  }
  return [...new Set(list.filter(Boolean))];
}

export function shouldRetryWithFallbackModel(status) {
  return status === 404 || status === 410 || status === 503;
}

/**
 * Resolve the previous and current release tags using git describe, with list fallback.
 *
 * @param {{ fromTag?: string, toTag?: string, githubRef?: string, git: (args: string[]) => string, listTags: () => string[] }} options
 */
export function resolveReleaseTags({ fromTag, toTag, githubRef, git, listTags }) {
  const currentTag = resolveCurrentTag({ toTag, githubRef });

  if (fromTag) {
    return { previousTag: fromTag, currentTag };
  }

  const major = currentTag.match(/^v?(\d+)/)?.[1];
  const describeArgs = ['describe', '--tags', '--abbrev=0'];
  if (major) {
    try {
      return {
        previousTag: git([...describeArgs, `--match=${major}.*`, `--match=v${major}.*`, `${currentTag}^`]),
        currentTag,
      };
    } catch {
      // First release of a new major has no same-major predecessor.
    }
  }

  try {
    return { previousTag: git([...describeArgs, `${currentTag}^`]), currentTag };
  } catch {
    const previousTag = findPreviousReleaseTag(currentTag, listTags());
    if (!previousTag) {
      throw new Error(`Could not resolve a previous release tag before ${currentTag}.`);
    }
    return { previousTag, currentTag };
  }
}