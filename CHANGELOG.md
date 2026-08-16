# Changelog

Release notes are written for users first. Keep entries short, concrete, and grouped by release version.

## Unreleased

### Added

### Changed

### Fixed

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
