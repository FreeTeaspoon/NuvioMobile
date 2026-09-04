#!/usr/bin/env bash
set -euo pipefail

upstream_ref="${1:-upstream/cmp-rewrite}"
fork_base="${2:-HEAD}"
repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

git rev-parse --verify "${upstream_ref}^{commit}" >/dev/null
git rev-parse --verify "${fork_base}^{commit}" >/dev/null
merge_base="$(git merge-base "$fork_base" "$upstream_ref")"

printf 'fork-base=%s\n' "$(git rev-parse --short "$fork_base")"
printf 'worktree-head=%s\n' "$(git rev-parse --short HEAD)"
printf 'upstream=%s\n' "$(git rev-parse --short "$upstream_ref")"
printf 'merge-base=%s\n' "$(git rev-parse --short "$merge_base")"

printf '%s\n' '--- conflict paths ---'
conflicts="$({
  git merge-tree --write-tree --no-messages "$fork_base" "$upstream_ref" 2>/dev/null \
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

printf '%s\n' '--- upstream shared UI changes to review ---'
upstream_ui_paths=(
  "composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt"
  "composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/LoadingIndicator.kt"
  "composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/PosterZoomActionOverlay.kt"
  "composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerControls.kt"
  "composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerOverlays.kt"
  "composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt"
  "composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerSidePanel.kt"
  "composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/MetaDetailsScreen.kt"
)
for upstream_ui_path in "${upstream_ui_paths[@]}"; do
  if git cat-file -e "${upstream_ref}:${upstream_ui_path}" 2>/dev/null && \
    ! git diff --quiet "$fork_base" "$upstream_ref" -- "$upstream_ui_path"; then
    printf 'upstream-ui-changed %s\n' "$upstream_ui_path"
  fi
done

audit_failed=0

check_file() {
  local path="$1"
  if [[ -f "$repo_root/$path" ]]; then
    printf 'present %s\n' "$path"
  else
    printf 'missing %s\n' "$path"
    audit_failed=1
  fi
}

check_contract() {
  local label="$1"
  local pattern="$2"
  shift 2
  local hits
  hits="$(rg -n -m 5 -e "$pattern" -- "$@" 2>/dev/null || true)"
  if [[ -n "$hits" ]]; then
    printf 'present %s\n' "$label"
    printf '%s\n' "$hits"
  else
    printf 'missing %s: %s\n' "$label" "$pattern"
    audit_failed=1
  fi
}

printf '%s\n' '--- fork feature files ---'
required_paths=(
  "FORK_FEATURES.md"
  "composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/VideoZoomModal.kt"
  "composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/RememberedVideoZoom.kt"
  "composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/RememberedAudioSelection.kt"
  "composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/RememberedSubtitleSelection.kt"
  "composeApp/src/androidMain/kotlin/com/nuvio/app/features/downloads/DownloadsForegroundService.kt"
  "composeApp/src/commonMain/kotlin/com/nuvio/app/features/downloads/DownloadProgressUpdateTracker.kt"
)
for required_path in "${required_paths[@]}"; do
  check_file "$required_path"
done

printf '%s\n' '--- upstream UI host wiring ---'
check_contract \
  "long-press overlay component" \
  'fun NuvioPosterZoomActionOverlay|NuvioPosterZoomActionOverlay' \
  composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/PosterZoomActionOverlay.kt \
  composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt
check_contract \
  "long-press host callbacks" \
  'onPosterLongClick' \
  composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt
check_contract \
  "upstream loading indicator" \
  'NuvioLoadingIndicator' \
  composeApp/src/commonMain/kotlin

printf '%s\n' '--- fork behavior wiring ---'
check_contract \
  "video zoom behavior" \
  'VideoZoomModal|RememberedVideoZoom|showVideoZoomModal|cycleResizeMode' \
  composeApp/src/commonMain/kotlin/com/nuvio/app/features/player
check_contract \
  "remembered audio/subtitle behavior" \
  'RememberedAudioSelectionRepository|RememberedSubtitleSelectionRepository|persistAudioPreference|persistInternalSubtitlePreference|persistAddonSubtitlePreference' \
  composeApp/src/commonMain/kotlin/com/nuvio/app/features/player
check_contract \
  "custom speed chooser component" \
  'PlaybackSpeedModal' \
  composeApp/src/commonMain/kotlin/com/nuvio/app/features/player
check_contract \
  "custom speed chooser wiring" \
  'onSpeedClick.*openSpeedModal|selectPlaybackSpeed' \
  composeApp/src/commonMain/kotlin/com/nuvio/app/features/player
check_contract \
  "Media3 backward-seek buffer" \
  'setBackBuffer\(' \
  composeApp/src/androidMain/kotlin/com/nuvio/app/features/player/PlayerEngine.android.kt
check_contract \
  "MPV backward-seek cache" \
  'demuxer-max-back-bytes|cache-secs|demuxer-seekable-cache' \
  composeApp/src/androidMain/kotlin/com/nuvio/app/features/player
check_contract \
  "external subtitle propagation" \
  'activeExternalSubtitles|externalSubtitles' \
  composeApp/src/commonMain/kotlin/com/nuvio/app/features/player
check_contract \
  "upstream Android playback engine" \
  'AndroidPlaybackEngine|LibmpvPlayerSurface|setAndroidPlaybackEngine' \
  composeApp/src/commonMain/kotlin/com/nuvio/app/features/player \
  composeApp/src/androidMain/kotlin/com/nuvio/app/features/player \
  composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings
check_contract \
  "foreground/recovered downloads" \
  'DownloadsForegroundService|DownloadProgressUpdateTracker|recovered' \
  composeApp/src
check_contract \
  "player system/launch storage" \
  'PlayerSystemMediaControls|PlayerTrackPreferenceStorage|PlayerLaunchStorage' \
  composeApp/src

printf '%s\n' '--- data/UI integration boundaries ---'
check_contract \
  "Trakt credential synchronization" \
  'TraktCredentialSync|sync_(pull|push)_provider_credentials|pushCurrentToRemote|pullFromRemote' \
  composeApp/src/commonMain/kotlin/com/nuvio/app/core/sync \
  composeApp/src/commonMain/kotlin/com/nuvio/app/features/trakt
check_contract \
  "profile sync safety" \
  'ProfileSettingsSync|isCurrent|pending|outbox' \
  composeApp/src/commonMain/kotlin/com/nuvio/app/core/sync
check_contract \
  "watched confirmations" \
  'WatchedConfirmationAction|watched_confirm_|WatchedActionSheet' \
  composeApp/src/commonMain/kotlin/com/nuvio/app/features/details \
  composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt
check_contract \
  "IMDb/rating links" \
  'buildRatingProviderUrl|buildImdbParentsGuideUrl|DetailRatingLinks' \
  composeApp/src/commonMain/kotlin/com/nuvio/app/features/details
check_contract \
  "desktop navigation consumer" \
  'DesktopNavigationLayout|DesktopHoverSidebar|settings_appearance_desktop_navigation' \
  composeApp/src/commonMain/kotlin/com/nuvio/app

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
  printf '%s\n' 'fork merge audit FAILED: inspect missing contracts before committing'
  exit 1
fi

printf '%s\n' 'fork merge audit PASSED'
