#!/usr/bin/env bash
# Sync the Bruno collection from the repository into the Bruno desktop app.
#
# The collection is copied over the existing one instead of being deleted and
# recreated: the Bruno app watches the collection directory, and removing it
# while the app is running makes it report "No collection configuration found".

set -euo pipefail

SOURCE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/bruno"
TARGET="${BRUNO_COLLECTION_PATH:-$HOME/Documents/Bruno/postgresql-event-sourcing}"

if [ ! -d "$SOURCE" ]; then
  echo "Source collection not found: $SOURCE" >&2
  exit 1
fi

mkdir -p "$TARGET"

# Copy the files over the existing ones so that the watcher of the Bruno app
# only sees the files that actually changed.
cp -r "$SOURCE/." "$TARGET/"

# The environment file is rewritten by the Bruno app on every run, so the
# runtime variables it appends are removed to keep the file in sync.
if git -C "$(dirname "$SOURCE")" rev-parse --git-dir >/dev/null 2>&1; then
  git -C "$(dirname "$SOURCE")" checkout -- bruno/environments/Local.bru 2>/dev/null || true
  cp "$SOURCE/environments/Local.bru" "$TARGET/environments/Local.bru"
fi

echo "Synced $(find "$TARGET" -type f | wc -l) files to $TARGET"
