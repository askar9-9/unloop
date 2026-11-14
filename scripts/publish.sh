#!/bin/sh
# Build and publish from a normal terminal with GitHub access. Never reads or prints tokens.
set -eu
ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
command -v gh >/dev/null || { echo 'GitHub CLI (gh) is required.' >&2; exit 1; }
command -v git >/dev/null || { echo 'Git is required.' >&2; exit 1; }

# Verify connectivity and authentication before spending time on the build.
OWNER=$(gh api user --jq .login)
REPO="$OWNER/unloop"
TAG=v0.1.1

if [ -z "${JAVA_HOME:-}" ]; then
    if [ -x /usr/libexec/java_home ]; then
        JAVA_HOME=$(/usr/libexec/java_home -v 17)
        export JAVA_HOME
    else
        echo 'Set JAVA_HOME to a JDK 17 installation.' >&2
        exit 1
    fi
fi
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug

if [ ! -d .git ]; then
    git init -b main
fi
git add .gitignore .github README.md TESTING.md RELEASE_NOTES.md scripts app build.gradle.kts settings.gradle.kts gradle.properties gradle gradlew gradlew.bat
if ! git diff --cached --quiet; then
    git commit -m 'Rename project to Unloop and prepare APK release'
fi

if git remote get-url origin >/dev/null 2>&1; then
    EXPECTED_HTTPS="https://github.com/$REPO.git"
    EXPECTED_SSH="git@github.com:$REPO.git"
    EXISTING=$(git remote get-url origin)
    if [ "$EXISTING" != "$EXPECTED_HTTPS" ] && [ "$EXISTING" != "$EXPECTED_SSH" ]; then
        echo 'Existing origin differs from the intended repository; leaving it unchanged.' >&2
        exit 1
    fi
    gh repo view "$REPO" --json isPrivate --jq .isPrivate | grep -qx true || {
        echo 'Expected a private repository; leaving it unchanged.' >&2; exit 1;
    }
    git push -u origin main
else
    gh repo create "$REPO" --private --source . --remote origin --push \
        --description 'Unloop — Android app to reduce endless Reels and Shorts scrolling'
fi

# The tag triggers tests, APK assembly and publication to GitHub Releases.
# Never move an existing tag to a different commit.
if git rev-parse "$TAG" >/dev/null 2>&1; then
    [ "$(git rev-parse "$TAG^{commit}")" = "$(git rev-parse HEAD)" ] || {
        echo 'Release tag already points to another commit.' >&2; exit 1;
    }
else
    git tag "$TAG"
fi
git push origin "$TAG"
echo "Repository: https://github.com/$REPO"
echo "APK will be published after successful CI: https://github.com/$REPO/releases/tag/$TAG"
