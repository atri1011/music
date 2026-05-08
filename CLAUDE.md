# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Single-Activity Android music player (Kotlin 100%, Jetpack Compose + Material 3, MVVM + Clean Architecture, Hilt). Aggregates NetEase / QQ / KuWo via the **TuneHub V3** backend (`tunehub.sayqz.com`). Supports search, toplists, playlists, favorites, recent plays, daily recommendations, lyrics, equalizer, downloads, and update-self.

- `compileSdk` 36, `minSdk` 26, `targetSdk` 36, JVM target 11
- `applicationId = com.music.myapplication`, `versionName = 1.8.1` (`versionCode = 22`)

## Build & Test Commands

API key is required at build time (`TUNEHUB_API_KEY` in `gradle.properties` or `local.properties`). It is also editable at runtime via the **More** screen.

Bash (Git Bash / Linux / macOS):

```bash
./gradlew assembleDebug              # build debug APK
./gradlew installDebug               # install on connected device
./gradlew testDebugUnitTest          # run all unit tests
./gradlew lintDebug                  # Android lint
./gradlew :app:assembleRelease       # release build (signs with debug key if RELEASE_* not set)
```

PowerShell uses `.\gradlew.bat` instead.

Run a single unit test class / method:

```bash
./gradlew :app:testDebugUnitTest --tests "com.music.myapplication.feature.search.SearchViewModelTest"
./gradlew :app:testDebugUnitTest --tests "*.SearchViewModelTest.search_emitsResults"
```

Signed release build (requires keystore; `RELEASE_SIGNING_REQUIRED=true` makes it fail loudly when a key is missing):

```bash
./gradlew :app:assembleRelease \
  -PRELEASE_STORE_FILE=/abs/path/release-keystore.jks \
  -PRELEASE_STORE_PASSWORD=*** -PRELEASE_KEY_ALIAS=*** -PRELEASE_KEY_PASSWORD=*** \
  -PRELEASE_SIGNING_REQUIRED=true
```

Optional Gradle properties: `APP_UPDATE_REPO=owner/repo` (powers in-app update check), `RELEASE_*` (release signing). The CI workflow `release-update-manifest.yml` consumes the matching `RELEASE_*` repo secrets plus `RELEASE_TOKEN`, `CHECK_UPDATE_PAT`, and `RELEASE_KEYSTORE_BASE64`.

## High-Level Architecture

Layering (depend in this direction only):

```
feature  →  domain  ←  data  →  core
                              ↗
                         media (uses core + domain)
                              ↗
                            di  (wires everything)
```

- `domain/` — pure Kotlin; models (`Track`, `Playlist`, `Platform`, `AudioSource`, `PlaybackState`) and Repository **interfaces**. No Android, no framework deps.
- `data/repository/` — Repository implementations + parsers + playable resolvers.
- `core/` — infrastructure: `common` (`Result`, `AppError`, `DispatchersProvider`), `database` (Room v2), `datastore` (preferences + per-day home cache), `network` (Retrofit + Dispatch engine + interceptors), `download`, `local` (local file scanner).
- `feature/` — Compose screens + ViewModels: `home`, `search`, `library`, `discover`, `player`, `playlist`, `more`, `album`, `artist`, `ecosystem`, `update`, `components`.
- `media/` — Media3 ExoPlayer engine: `playback`, `player`, `service`, `session`, `state`, `equalizer`, `video`.
- `di/` — Hilt modules.

Detailed per-module documentation lives under `app/src/main/java/com/music/myapplication/{core,data,domain,feature,media}/CLAUDE.md`. Read those before changing module internals.

## Code Organization Rules

Keep new code modular by default. Prefer extracting responsibilities early instead of letting files become "god files" that mix UI, state, networking, persistence, and business rules.

- **Modularization is required.** Split work by feature, layer, and responsibility. If a class or file starts handling multiple concerns, extract delegates, helpers, mappers, state holders, or subcomponents.
- **One component, one file.** Reusable Compose UI components should live in their own file. Screens may orchestrate multiple components, but significant subviews should be extracted instead of being kept inline in one large screen file.
- **Control file size.** Treat roughly **300 lines** as the preferred upper bound for handwritten source files. Once a file grows beyond that, consider whether it should be split. Files approaching **500 lines** should normally be refactored unless there is a clear reason not to.
- **Keep ViewModels and repositories focused.** When logic grows, move parsing, orchestration, reducer-like state updates, or platform-specific behavior into dedicated collaborators instead of extending a single large class.
- **Prefer colocated small support files over giant umbrella files.** It is better to have several clearly named files in the same package than one oversized file containing unrelated components or utilities.
- **Exceptions should be rare and intentional.** Small preview functions, tightly coupled private helpers, or sealed type families may stay together when doing so clearly improves readability.

### Two cross-cutting engines

These are the two architectural ideas that need multiple files to fully understand. Internalize them before touching online music or playback flows.

**1. Method Dispatch Engine** (`core/network/dispatch/`)
The client does **not** hard-code per-platform search/toplist/playlist endpoints. Instead it asks TuneHub for a per-method template (`/v1/methods/{platform}/{function}`), then runs it locally:

```
DispatchExecutor → TemplateRenderer (fills {{vars}}, supports || fallback + helpers like parseInt())
                 → OkHttp (request the upstream platform directly)
                 → TransformEngine (rules-based JSON → List<Track>; falls back to alias+scoring heuristics)
                 → DispatchTemplateCache (in-memory)
```

Implication: search/toplist/playlist features do **not** consume TuneHub credits — only `/v1/parse` (playable URL + lyrics) does. Add a new platform method by shipping a server-side template, not by editing the client.

**2. PlaybackSourceRouter** (`data/repository/PlaybackSourceRouter.kt`)
A `Track` knows its `platform` (NetEase/QQ/KuWo) but its **playable URL** can come from several sources, picked from `PlayerPreferences.audioSource`:

```
AudioSource.TUNEHUB        → TuneHubPlayableResolver        (no fallback)
AudioSource.LX_CUSTOM      → LxCustomSourcePlayableResolver → falls back to TuneHub
AudioSource.METING_BAKA    → MetingPlayableResolver         → falls back to TuneHub
AudioSource.JKAPI          → JkApiPlayableResolver          → falls back to TuneHub
AudioSource.NETEASE_CLOUD  → NeteaseCloudApiPlayableResolver→ falls back to TuneHub
```

When adding a new resolver, follow this contract: implement a resolver that returns `Result<PlaybackSourceResolution>`, register it in DI, and add a branch + fallback policy in `PlaybackSourceRouter.resolve`.

### Track-centric data flow

Almost every async pipeline (search, toplists, playlists, recommendations, parsers, resolvers) ends in a `List<Track>` or `Track`. **Changing fields on `Track` is a wide-blast-radius change** — audit every parser in `data/repository/{NeteaseParsers,QqParsers,KuwoParsers,OnlineCommonParsers}.kt` plus the Dispatch `TransformEngine` field-map heuristics.

### Player is one shared instance

`PlayerViewModel` is created at the navigation root (`AppRoot`) via `hiltViewModel()` and re-used by mini-bar, full-screen player, lyrics, and queue UIs. State is split into multiple slim UI states (`MiniPlayerUiState`, `PlayerStaticUiState`, `PlaybackProgressUiState`, `TrackActionUiState`, `TrackInfoUiState`) so that high-frequency progress updates do not recompose the whole player tree. Preserve this split when adding new state.

### Result + AppError contract

`core/common/Result.kt` is `sealed interface Result<T>` with `Success / Error / Loading`. All repository / use-case returns must use it; do not throw across layer boundaries. `AppError` categorizes failures (Network / Api / Template / Parse / Playback / Database / Unknown) — pick the right subtype so the UI can render appropriate messaging.

## Gotchas

- **QQ Music dual-ID system.** QQ tracks have both a numeric ID and a `songMid`. `PlayerViewModel.resolveTrackForPlayback` retries with the alternate ID when the first fails. Any new QQ-touching flow must accept either.
- **Cover URL enrichment is platform-specific.** `OnlineTrackCoverEnricher` (used by `OnlineMusicRepositoryImpl`) hydrates covers per platform; do not assume `Track.coverUrl` is populated at parse time.
- **NetEase playlist detail bypasses Dispatch.** `OnlineMusicRepositoryImpl` calls the native `getNeteasePlaylistDetailV6` because the dispatched template cannot do batched `trackIds` enrichment.
- **QQ playable URLs auto-upgrade HTTP→HTTPS** in `OnlineMusicRepositoryImpl`.
- **Adding a new `Platform` enum value** requires updating: `domain/model/Platform.kt`, all parsers in `data/repository/*Parsers.kt`, `OnlineMusicRepositoryImpl` enrichers, the platform-filter UI chips in `feature/components/PlatformFilterChips.kt`, and the search/toplist routes.
- **Room uses `fallbackToDestructiveMigration(dropAllTables = true)`** — schema changes wipe local data. If you need to preserve data on upgrade, switch to a real Migration before bumping the schema.
- **Most build/test logs and UI strings are Chinese.** Keep new user-facing copy in Chinese to match.

## Testing Notes

JUnit 4 + MockK + `kotlinx-coroutines-test`; instrumented tests use `HiltTestRunner`. Existing coverage clusters on parsers, dispatch engine, repositories (`OnlineMusicRepositoryImpl`, `LocalLibraryRepositoryImpl`, resolvers, lx custom-script runtime, app update), `SearchViewModel`, `PlaylistDetailViewModel`. **Untested high-risk hotspots:** `PlayerViewModel`, `HomeViewModel`, `LibraryViewModel`, `DispatchExecutor` end-to-end, `MusicPlaybackService`, all DAOs (no instrumented tests yet). Add tests there before non-trivial changes.

## Auto-Update

In-app update flow is documented in `docs/auto-update-2.0-local-guide.md`. The companion repo `owner/check-update` (configured via `APP_UPDATE_REPO`) hosts the version manifest written by `.github/workflows/release-update-manifest.yml`.

## Changelog

| Date | Change |
|------|--------|
| 2026-03-08 | Initial root-level documentation |
| 2026-04-30 | Refresh: new modules (album/artist/ecosystem/update, equalizer/video, download/local), PlaybackSourceRouter pattern, expanded test inventory, build/signing commands |
| 2026-04-30 | Add code organization rules: modularization required, one component per file, and file size guidance |
