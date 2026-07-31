#!/usr/bin/env bash
set -euo pipefail

upstream_ref="${1:-upstream/cmp-rewrite}"
repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

git rev-parse --verify "${upstream_ref}^{commit}" >/dev/null
merge_base="$(git merge-base HEAD "$upstream_ref")"

printf 'fork=%s\n' "$(git rev-parse --short HEAD)"
printf 'upstream=%s\n' "$(git rev-parse --short "$upstream_ref")"
printf 'merge-base=%s\n' "$(git rev-parse --short "$merge_base")"

printf '%s\n' '--- conflict paths ---'
conflicts="$({
  git merge-tree --write-tree --no-messages HEAD "$upstream_ref" 2>/dev/null \
    | awk -F '\t' 'NF >= 2 { n=split($1,a," "); if (a[n] ~ /^[123]$/) print $2 }' \
    | sort -u
} || true)"
if [[ -n "$conflicts" ]]; then
  printf '%s\n' "$conflicts"
  conflict_count="$(printf '%s\n' "$conflicts" | sed '/^$/d' | wc -l | tr -d ' ')"
  printf 'conflict-count=%s\n' "$conflict_count"
else
  printf '%s\n' '(none)'
  printf 'conflict-count=0\n'
fi

printf '%s\n' '--- fork feature files ---'
required_paths=(
  "FORK_FEATURES.md"
  "composeApp/full-libs/libmpv-release.aar"
  "composeApp/src/androidFull/kotlin/com/nuvio/app/features/player/AndroidMpvPlayerSurface.kt"
  "composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/VideoZoomModal.kt"
  "composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/RememberedVideoZoom.kt"
  "composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/RememberedAudioSelection.kt"
  "composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/RememberedSubtitleSelection.kt"
  "composeApp/src/androidMain/kotlin/com/nuvio/app/features/downloads/DownloadsForegroundService.kt"
  "composeApp/src/commonMain/kotlin/com/nuvio/app/features/downloads/DownloadProgressUpdateTracker.kt"
)
audit_failed=0
for required_path in "${required_paths[@]}"; do
  if git cat-file -e "HEAD:${required_path}" 2>/dev/null; then
    printf 'present %s\n' "$required_path"
  else
    printf 'missing %s\n' "$required_path"
    audit_failed=1
  fi
done

printf '%s\n' '--- long-press wiring ---'
if git grep -n -E 'NuvioPosterZoomActionOverlay|onPosterLongClick' -- \
  composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt \
  composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/PosterZoomActionOverlay.kt >/dev/null; then
  git grep -n -E 'NuvioPosterZoomActionOverlay|onPosterLongClick' -- \
    composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt \
    composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/PosterZoomActionOverlay.kt | head -n 20
else
  printf '%s\n' 'missing long-press host wiring'
  audit_failed=1
fi

printf '%s\n' '--- generated files ---'
generated_files="$(git ls-files 'supabase/.temp/**')"
if [[ -n "$generated_files" ]]; then
  printf '%s\n' "$generated_files"
  printf '%s\n' 'generated Supabase files are still tracked'
  audit_failed=1
else
  printf '%s\n' '(none tracked)'
fi

if [[ "$audit_failed" -ne 0 ]]; then
  exit 1
fi
