# Nuvio fork feature boundary

This file records behavior that must survive an upstream sync. The upstream
implementation remains the structural base for shared screens and runtime
files; fork behavior should be added through the smallest possible adapter,
callback, flavor source set, or new file.

## Fork behavior to preserve

| Area | Fork behavior | Primary files and tests |
| --- | --- | --- |
| Full Android player | MPV engine, full flavor policy, bundled `libmpv-release.aar`, MPV surface, downloaded-file playback | `composeApp/src/androidFull/**/player/**`, `composeApp/full-libs/libmpv-release.aar`, player engine tests |
| Player preferences | Video zoom plus remembered audio, subtitle, and video zoom selections | `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/VideoZoomModal.kt`, `Remembered*.kt`, matching `*Test.kt` files |
| Downloads | Foreground service, partial/recovered downloads, direct-download performance, grouping recovery, live download speeds | `composeApp/src/**/features/downloads/**`, `DownloadProgressUpdateTrackerTest.kt` |
| Player integrations | Player engine selection, system media controls, launch/track preference storage, MPV cache/loading behavior | `composeApp/src/**/features/player/**` |
| Data workflows | Fork backup/profile/desktop implementations and their platform-specific tests | `features/backup/**`, `features/profiles/**`, `desktopMain/**`, matching tests |
| Release automation | Fork release/version workflows and fork-specific release notes | `.github/workflows/*fork*`, `.github/workflows/build-cmp-rewrite-release.yml`, `FORK_RELEASE_VERSIONING.md` |

## Upstream-shaped shared files

Keep these files close to upstream and port fork behavior around the upstream
interfaces instead of copying whole implementations:

- `composeApp/src/commonMain/kotlin/com/nuvio/app/App.kt`
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreen.kt` and split runtime files
- `LibraryScreen.kt`, `MetaDetailsScreen.kt`, `SearchScreen.kt`, and `StreamsScreen.kt`
- `composeApp/build.gradle.kts` and `androidApp/build.gradle.kts`
- settings roots and locale XML files

The upstream long-press host must remain wired through
`NuvioPosterZoomActionOverlay`. Fork library/watch/list actions belong in its
action callbacks; a fork-only bottom sheet must not replace the host.

## Merge checklist

1. Fetch and pin the intended upstream ref. Use the release tag for an exact
   version; use `upstream/cmp-rewrite` only when “newest upstream” is intended.
2. Run `scripts/audit-fork-merge.sh <upstream-ref>` before resolving conflicts.
3. Resolve shared structural files by adapting the upstream version, not by
   choosing all of ours or all of theirs.
4. Verify the MPV, preference, download, long-press, release, and test files
   listed above after the merge.
5. Run the Android compile, host tests, debug APK build, and `git diff --check`.

Generated Supabase CLI state under `supabase/.temp/` is local-only and must
remain ignored and untracked.
