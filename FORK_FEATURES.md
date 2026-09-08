# Nuvio fork feature boundary

Updated for the user-requested cleanup on 2026-09-09, based on upstream
`a30bf5192c0aa56219c47dc964a9cbfd400d37a7` (`cmp-rewrite`). These choices supersede
older retention notes and historical changelog entries.

## Restored to upstream

`scripts/upstream-equivalent-paths.txt` records the exact file boundary. Each
listed file must match the chosen upstream ref byte for byte, or be absent
when upstream does not contain it. This covers:

- Audio/subtitle remembering, original-language selection, external subtitle
  handling, playback error guards, orientation and playback restoration.
- IntroDB, profile settings sync, Trakt credentials, home catalog sync,
  metadata fetching/parsing, stream selection and P2P magnets.
- Desktop sources/build setup, floating navigation, episode metadata rows,
  the old poster sheet, MPV cache helper and startup rules.
- Unused storage/UI/profile/sync hooks, download/notification policy flags,
  the standalone download service and bundled TorrServer binaries.

Shared files that also implement retained features keep only the additions
listed below. Android's minimum SDK and dependency versions use upstream's
requirements. App identity, signing and fork release packaging remain local.

## Fork behavior to preserve

| Area | Retained behavior |
| --- | --- |
| Video zoom | Zoom controls and per-title defaults; centered Media3 surface scaling, MPV video-zoom, and an actual magnification label. Persistence lives in separate VideoZoomStorage adapters. |
| Offline startup | The saved Open Downloads when offline switch gates upstream's launch redirect, which requires a playable local download. |
| Playback controls | Custom speed chooser, played/buffered/remaining seek bar, current scrub target and horizontal seeking through the middle 80% of the video. |
| Media3 buffering | 192 MiB target buffer and 120-second back buffer. |
| Android media controls | App-level system media session and notification. |
| Downloads | Local file and series recovery, buffered writes, live speed, progress pacing and specific deletion confirmation. Upstream owns transfer scheduling and foreground protection. |
| Backups | Encrypted current-profile export/import and authenticated cloud restore, including separate video zoom settings. Upstream track preference storage has no bulk backup API; legacy fork track payloads are ignored. |
| Detail actions | Watched confirmations, stale progress clearing, rating-provider links and IMDb Parents Guide. Provider links use available metadata with search fallback; metadata enrichment follows upstream. |
| Reset actions | Home and metadata settings reset confirmations. |
| Distribution | Fork app identity/updater, signing, arm64 release APKs, fourth version segment, release notes and GitHub workflows. |

Full and Play Store Android flavors use the shared upstream player directly.
MPV, addon subtitles, track preferences, downloads, custom themes and poster
long-press overlays are upstream features; preserve their current upstream
implementations when updating.

## Merge and verification

1. Fetch and pin upstream, save the starting fork commit, and review both sides.
2. Merge upstream; preserve the exact-file boundary above and adapt retained
   behavior through small, separate helpers where possible.
3. Run `bash scripts/audit-fork-merge.sh <upstream-ref> <fork-base>`. It checks
   restored files, retained call sites and locale key completeness.
4. Review the remaining diff, run Android compilation and host tests, and
   build both Play Store and full debug APKs. Run `git diff --check`.
5. Commit and push after checks pass. When a release is requested, run
   `build-cmp-rewrite-release.yml` and verify the published release and APK.

Generated `supabase/.temp/` state and local credentials remain untracked.
