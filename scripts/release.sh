#!/usr/bin/env bash
# Perform a full release: merge develop→master, set version, tag, push.
# Usage: ./scripts/release.sh <release-version> <next-snapshot-version>
# Example: ./scripts/release.sh 0.15.0 0.15.1-SNAPSHOT
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <release-version> <next-snapshot-version>" >&2
  echo "Example: $0 0.15.0 0.15.1-SNAPSHOT" >&2
  exit 1
fi

RELEASE_VERSION="$1"
NEXT_VERSION="$2"

# Validate: release version must not contain SNAPSHOT
if [[ "$RELEASE_VERSION" == *SNAPSHOT* ]]; then
  echo "ERROR: release version must not be a SNAPSHOT: $RELEASE_VERSION" >&2
  exit 1
fi

# Validate: next version must contain SNAPSHOT
if [[ "$NEXT_VERSION" != *SNAPSHOT* ]]; then
  echo "ERROR: next version must be a SNAPSHOT: $NEXT_VERSION" >&2
  exit 1
fi

echo "=== Step 1: Verify working tree is clean ==="
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "ERROR: working tree has uncommitted changes. Commit or stash them first." >&2
  exit 1
fi

echo "=== Step 2: Merge develop → master ==="
git checkout master
git pull origin master
git merge origin/develop --no-edit

echo "=== Step 3: Set release version $RELEASE_VERSION ==="
./scripts/update-version.sh "$RELEASE_VERSION"

# Verify no SNAPSHOT remains in pom files
if grep -r "SNAPSHOT" "$ROOT_DIR" --include="pom.xml" -l | grep -v "target/"; then
  echo "ERROR: SNAPSHOT version still found in pom.xml files after update." >&2
  exit 1
fi
echo "  ✓ No SNAPSHOT versions remain"

echo "=== Step 4: Commit and tag ==="
git add -A
git commit -m "release: v${RELEASE_VERSION}"
git tag "v${RELEASE_VERSION}"

echo "=== Step 5: Push master + tag ==="
git push origin master --tags

echo "=== Step 6: Bump develop to $NEXT_VERSION ==="
git checkout develop
git pull origin develop
git merge master --no-edit

git checkout -b "feature/next-snapshot-${NEXT_VERSION%-SNAPSHOT}"
./scripts/update-version.sh "$NEXT_VERSION"
git add -A
git commit -m "chore: bump version to ${NEXT_VERSION}"
git push -u origin "feature/next-snapshot-${NEXT_VERSION%-SNAPSHOT}"
gh pr create --base develop \
  --head "feature/next-snapshot-${NEXT_VERSION%-SNAPSHOT}" \
  --title "chore: bump version to ${NEXT_VERSION}" \
  --body "Post-release version bump to \`${NEXT_VERSION}\`."

echo ""
echo "=== Release v${RELEASE_VERSION} complete ==="
echo "  - Tag v${RELEASE_VERSION} pushed → CI will publish to Maven Central"
echo "  - PR opened to bump develop to ${NEXT_VERSION}"
