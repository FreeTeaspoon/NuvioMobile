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
| Player compatibility | Media3 120-second back buffer, MPV cache/back-cache, custom speed chooser, source filename/size hints, per-source external subtitles, and P2P magnet/cache metadata | `features/player/PlayerEngine*.kt`, `PlayerScreenRuntime*.kt`, `features/p2p/**`, `PlaybackMediaItems.android.kt`, matching player/P2P tests |
| Downloads | Foreground service, partial/recovered downloads, direct-download performance, grouping recovery, live download speeds | `composeApp/src/**/features/downloads/**`, `DownloadProgressUpdateTrackerTest.kt` |
| Player integrations | Player engine selection, system media controls, launch/track preference storage, MPV cache/loading behavior | `composeApp/src/**/features/player/**` |
| Data workflows | Fork backup/profile/desktop implementations and their platform-specific tests | `features/backup/**`, `features/profiles/**`, `desktopMain/**`, matching tests |
| Release automation | Fork release/version workflows and fork-specific release notes | `.github/workflows/*fork*`, `.github/workflows/build-cmp-rewrite-release.yml`, `FORK_RELEASE_VERSIONING.md` |

The shared player merge boundary is intentional: `androidMain` contains the
upstream player implementation, `androidPlaystore` delegates to it, and
`androidFull` adapts only the fork's bundled MPV/AAR path. If upstream changes
the common player surface, update the two flavor adapters first; do not copy
the entire upstream player into the full flavor or delete the full-only AAR.

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

## Upstream changes to absorb

Upstream is the presentation base for shared UI. Keep these updates when they
appear, while reconnecting fork behavior through the current callbacks and
runtime contracts:

| Upstream update | Keep the upstream part | Reconnect the fork part |
| --- | --- | --- |
| Long-press overlay | Host, layout, animation, and new actions | Fork zoom/list/watch/download actions and confirmations |
| Loading/spinner wheel | Indicator, transitions, and new loading states | Download recovery/live speed and player loading/cancellation behavior |
| Subtitle/audio/voice menus | Menu layout, labels, track APIs, forced-subtitle/delay behavior | Remembered tracks, addon/external subtitles, preferred languages, and custom speed chooser |
| Player runtime | Common surface, snapshot/resume, seek, and Media3 changes | Full MPV/AAR selection, cache/back-buffer, source metadata, system controls, and crash guards |
| Details/library/search/settings | Navigation, cards, labels, and new upstream actions | Fork watch/list/download/IMDb/rating/profile/desktop behavior through callbacks/adapters |

Do not resolve these areas by taking a complete fork or upstream file. A
successful merge must preserve upstream presentation symbols and fork behavior
symbols/call sites in the same worktree.

## Retention contract

`scripts/audit-fork-merge.sh` checks the working tree, not only `HEAD`, and
accepts the upstream ref plus the pre-merge fork baseline:

```bash
bash scripts/audit-fork-merge.sh upstream/cmp-rewrite "$fork_base"
```

Run it before and after every merge. A missing feature file, behavior symbol,
or host/action call site is a merge failure. Review deletions relative to the
baseline, excluding only generated `supabase/.temp/**` state. For shared
high-churn files, compare the fork baseline, upstream ref, and merged result;
file presence and a green compile are not sufficient.

## Merge checklist

1. Fetch and pin the intended upstream ref. Use the release tag for an exact
   version; use `upstream/cmp-rewrite` only when “newest upstream” is intended.
2. Save the pre-merge fork commit and run `scripts/audit-fork-merge.sh <upstream-ref> <fork-base>` before resolving conflicts.
3. Resolve shared structural files by adapting the upstream version, not by
   choosing all of ours or all of theirs.
4. Rerun the audit after resolving conflicts and verify the MPV, preference,
   player-compatibility, download, long-press, release, and test contracts
   listed above.
5. Confirm upstream loading/long-press/subtitle/audio UI symbols and their
   host call sites remain present alongside the fork adapters.
6. Run the Android compile, host tests, debug APK build, and `git diff --check`.

Generated Supabase CLI state under `supabase/.temp/` is local-only and must
remain ignored and untracked.
