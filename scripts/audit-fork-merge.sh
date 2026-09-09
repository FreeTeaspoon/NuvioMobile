#!/usr/bin/env bash
set -euo pipefail
upstream_ref="${1:-upstream/cmp-rewrite}"
fork_base="${2:-HEAD}"
cd "$(git rev-parse --show-toplevel)"
git rev-parse --verify "${upstream_ref}^{commit}" >/dev/null
git rev-parse --verify "${fork_base}^{commit}" >/dev/null
printf 'upstream=%s fork-base=%s\n' "$(git rev-parse --short "$upstream_ref")" "$(git rev-parse --short "$fork_base")"

python3 - "$upstream_ref" <<'PY'
from pathlib import Path
import subprocess,sys,xml.etree.ElementTree as ET
upstream=sys.argv[1]
paths=[p for p in Path('scripts/upstream-equivalent-paths.txt').read_text().splitlines() if p and not p.startswith('#')]
failed=[]
for path in paths:
    original=subprocess.run(['git','show',f'{upstream}:{path}'],capture_output=True)
    local=Path(path)
    if original.returncode == 0:
        if not local.is_file() or local.read_bytes()!=original.stdout:failed.append(path)
    elif local.exists():failed.append(path)
if failed:
    sys.exit('Upstream parity FAILED:\n'+'\n'.join(failed))
print(f'Upstream parity PASSED: {len(paths)} files identical or absent in both trees')
resources=Path('composeApp/src/commonMain/composeResources')
def keys(path):
    names=[item.attrib['name'] for item in ET.parse(path).getroot() if item.tag=='string']
    assert len(names)==len(set(names)),f'Duplicate resource names: {path}'
    return set(names)
default=keys(resources/'values/strings.xml')
for path in sorted(resources.glob('values-*/strings.xml')):
    assert keys(path)==default,f'Locale keys differ: {path}'
print('Locale keys PASSED')
PY

check() {
  local label="$1" pattern="$2"; shift 2
  if ! rg -q -e "$pattern" -- "$@"; then
    printf 'Missing retained behavior: %s\n' "$label" >&2
    exit 1
  fi
  printf 'present %s\n' "$label"
}
common=composeApp/src/commonMain/kotlin/com/nuvio/app
android=composeApp/src/androidMain/kotlin/com/nuvio/app
check 'poster overlay host' 'NuvioPosterZoomActionOverlay\(' "$common/App.kt" "$common/MainAppContent.kt"
check 'upstream loading indicator' 'NuvioLoadingIndicator' "$common/core/ui/LoadingIndicator.kt"
check 'upstream custom themes' 'AppearanceThemePicker' "$common/features/settings/AppearanceSettingsPage.kt"
check 'zoom modal host' 'VideoZoomModal\(' "$common/features/player/PlayerScreenModalHosts.kt"
check 'zoom runtime binding' 'BindVideoZoom\(' "$common/features/player/PlayerScreenRuntimeUi.kt"
check 'Media3 zoom wiring' 'applyVideoZoom\(videoZoom\)' "$android/features/player/PlayerEngine.android.kt"
check 'MPV zoom wiring' 'setPropertyDouble\("video-zoom"' "$android/features/player/PlayerEngine.android.kt"
check 'zoom persistence' 'VideoZoomStorage.save' "$common/features/player/RememberedVideoZoom.kt"
check 'offline Downloads switch' 'downloadsUiState.autoOpenOnOffline && hasPlayableDownload' "$common/MainAppContent.kt"
check 'custom speed chooser' 'onSpeedClick.*openSpeedModal' "$common/features/player/PlayerScreenRuntimeUi.kt"
check 'back buffer' 'setBackBuffer\(120_000, true\)' "$android/features/player/PlayerEngine.android.kt"
check 'buffered seek bar' 'bufferedFraction' "$common/features/player/PlayerControls.kt"
check 'horizontal seek feedback' 'liveHorizontalSeekTarget' "$common/features/player/PlayerScreenRuntimeGestureActions.kt"
check 'managed foreground downloads' 'setForeground\(ForegroundInfo' "$android/features/downloads/DownloadsTransferWorker.kt"
check 'download progress pacing' 'DownloadProgressUpdateTracker' "$android/features/downloads/AndroidDownloadScheduler.kt"
check 'download recovery' 'recover' "$common/features/downloads/DownloadsRepository.kt"
check 'system media controls' 'PlatformSystemMediaControls\(' "$common/features/player/PlayerScreenRuntimeUi.kt"
check 'backup UI' 'BackupRepository' "$common/features/settings/AccountSettingsPage.kt"
check 'watched confirmations' 'WatchedConfirmationAction' "$common/features/details/MetaDetailsScreen.kt"
check 'rating links' 'buildRatingProviderUrl' "$common/features/details/components/DetailMetaInfo.kt"
check 'Parents Guide' 'buildImdbParentsGuideUrl' "$common/features/details/components/DetailMetaInfo.kt"
check 'fork updater' 'FreeTeaspoon' "$common/features/updater/AppUpdater.kt"
check 'predictive back root ownership' 'PlatformBackHandler\(enabled = rootRouteActive' "$common/MainTabsDestination.kt"
check 'predictive back transition' 'predictivePopTransitionSpec = \{ nuvioPopTransition\(\)' "$common/MainAppContent.kt"
check 'Supabase local aliases' 'arrayOf\("NUVIO_SUPABASE_URL", "SUPABASE_URL"\)' composeApp/build.gradle.kts
check 'fork package identity' 'com.nuvio.app.freeteaspoon' androidApp/build.gradle.kts
check 'fork release workflow' 'gh release' .github/workflows/build-cmp-rewrite-release.yml
if [[ -n "$(git ls-files 'supabase/.temp/**')" ]]; then
  printf 'Generated Supabase state is tracked\n' >&2
  exit 1
fi
printf 'Fork merge audit PASSED\n'
