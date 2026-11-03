#!/usr/bin/env bash
# Publish the built app to the gh-pages branch (GitHub Pages), including public/data, which isn't in git.
# Appends a commit per deploy (never force-pushes); unchanged audio clips cost nothing to push again.
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/check-data.py
npm run build
rev=$(git rev-parse --short HEAD)
wt=$(mktemp -d)
trap 'git worktree remove --force "$wt" 2>/dev/null || true' EXIT
git fetch -q origin gh-pages 2>/dev/null || true
if git rev-parse -q --verify origin/gh-pages >/dev/null; then
  git worktree add -q -B gh-pages "$wt" origin/gh-pages
else
  git worktree add -q --orphan -b gh-pages "$wt"
fi
find "$wt" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
cp -r dist/. "$wt"/
touch "$wt/.nojekyll" # serve files as-is (no Jekyll processing)
git -C "$wt" add -A
if git -C "$wt" diff --cached --quiet; then
  echo "Nothing changed since the last deploy."
  exit 0
fi
git -C "$wt" commit -q -m "Deploy $rev"
git -C "$wt" push -q origin gh-pages
echo "Deployed $rev to gh-pages."
