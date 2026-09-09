# Changelog

Release notes are written for users first. Keep entries short, concrete, and grouped by release version.

## Unreleased

### Added

### Changed

### Fixed

## 0.4.15.2

### Changed

- Pages now use consistent scale and fade-through transitions based on Google's full-screen predictive back guidance.
- Settings, download show pages, and collection editor pages use the shared navigation stack.

### Fixed

- Reopening a settings option starts at the top instead of restoring its previous scroll position.
- Android predictive back works between navigation pages, including show details to home and streams to show details.
- Local builds accept both prefixed and unprefixed Supabase configuration keys.

## 0.4.14.2

### Changed

- Merged the latest upstream app updates, including custom themes, Russian localization, skeleton loading, startup optimizations, download fixes and subtitle rendering fixes.
- Restored upstream audio/subtitle preferences, original-language selection, external subtitles, playback stability, orientation, playback restoration and IntroDB behavior.
- Restored upstream profile/home catalog sync, Trakt credentials, metadata handling, stream selection, P2P magnets and Android minimum SDK requirements.
- Removed fork WebDAV authentication, desktop support, old floating navigation, the extra episode metadata row, old poster actions, MPV cache/startup helpers, unused support hooks, extra policy flags and bundled TorrServer files.
- Kept custom playback speed, buffered seeking, download recovery and speeds, encrypted backups, watched confirmations, rating links and the fork updater.

### Fixed

- Video zoom now scales the Media3 video around its center through layout changes and uses the same magnification as MPV. Subtitles keep their original size, and the label shows the actual magnification.
- Open Downloads when offline now respects the saved switch and opens Downloads only when playable local files are available.

## 0.4.14.1

### Changed

- Updated to upstream 0.4.14, including player controls, original-audio selection, subtitle picker fixes, and autoplay source loading.
- Android downloads now use persistent system scheduling while retaining WebDAV authentication, live speeds, and local file recovery.

### Fixed

- Remembered audio and subtitle choices remain selected when original-language metadata arrives during playback.
- Returning from playback refreshes the resume position, including after seeking backward.
- Recovered download titles recognize the new filename format.

## 0.4.5.4

### Changed

- Horizontal player seeking now works across the middle 80% of the video surface, while the outer 10% on each side remains a safety dead zone.

## 0.4.5.3

### Changed

- The full release APK is now published explicitly for 64-bit ARM devices as `arm64-v8a`.

## 0.4.5.2

### Fixed

- Horizontal seek feedback now stays synchronized with playback and keeps the correct +/- seconds value while a swipe remains active.

## 0.4.5.1

### Fixed

- Downloads made before signing in no longer disappear from the Downloads screen after logging out or switching account state, and existing local files are recovered after updating.
- Recovered episode downloads now return to Shows with season and episode sorting instead of appearing as Movies.

## 0.1.25.3

### Fixed

- MDBList ratings now load more reliably on newly opened uncached movie and show meta screens.
- Player playback now relocks to landscape after returning from other apps or picture-in-picture.

## 0.1.12.2

### Fixed

- Startup update checks now look at this fork's GitHub releases and show the update popup when a newer fork APK is available.

## 0.1.12.1

### Fixed

- Rating provider logos and numbers now open direct provider pages when available, with search fallback when a direct link cannot be found.

## 0.1.11.1

### Changed

- Fork release build for the current `cmp-rewrite` app version.
