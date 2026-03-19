#!/usr/bin/env bash
# Update reactor version, README.md, docs, and examples to the specified version.
# Usage: ./scripts/update-version.sh <version>
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <version>" >&2
  exit 1
fi

VERSION="$1"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

# 1. Update reactor version via Maven
echo "Running mvn versions:set -DnewVersion=$VERSION ..."
mvn -f "$ROOT_DIR/pom.xml" versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false
echo "Reactor version updated -> $VERSION"

# 2. Update <enkan.version> in root pom.xml
sed -i '' "s|<enkan\.version>[^<]*</enkan\.version>|<enkan.version>$VERSION</enkan.version>|g" "$ROOT_DIR/pom.xml"
echo "root pom.xml enkan.version updated -> $VERSION"

# 3. Update benchmark enkan.version
sed -i '' "s|<enkan\.version>[^<]*</enkan\.version>|<enkan.version>$VERSION</enkan.version>|g" "$ROOT_DIR/benchmark/enkan-app/pom.xml"
echo "benchmark/enkan-app/pom.xml enkan.version updated -> $VERSION"

# 4. Update <version> in README.md
sed -i '' "s|<version>[^<]*</version>|<version>$VERSION</version>|g" "$ROOT_DIR/README.md"
echo "README.md updated -> $VERSION"

# 5. Update <version> in docs
find "$ROOT_DIR/docs/src/content" -name '*.md' -exec \
  sed -i '' "s|<version>[^<]*</version>|<version>$VERSION</version>|g" {} +
echo "docs/src/content/**/*.md updated -> $VERSION"

# 6. Update version string in CLAUDE.md
sed -i '' "s|version \`[^']*\`)|version \`$VERSION\`)|g" "$ROOT_DIR/CLAUDE.md"
echo "CLAUDE.md updated -> $VERSION"

# 7. Update supported version in SECURITY.md (major.minor.x)
MINOR_VERSION="$(echo "$VERSION" | sed 's/\([0-9]*\.[0-9]*\).*/\1/')"
sed -i '' "s#[0-9]*\.[0-9]*\.x  | Yes#${MINOR_VERSION}.x  | Yes#g" "$ROOT_DIR/SECURITY.md"
echo "SECURITY.md updated -> ${MINOR_VERSION}.x"

echo ""
echo "Done. Verify changes with: git diff"
