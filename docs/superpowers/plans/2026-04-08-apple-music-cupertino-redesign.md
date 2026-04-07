# Apple Music / Cupertino Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the app shell and core screens around an Apple Music / Cupertino design system while preserving existing playback, search, library, and settings capabilities.

**Architecture:** Introduce a shared Apple UI layer first: tokens, chrome state, scaffold, nav bar, search field, grouped list primitives, and overlay wrappers. Then migrate top-level screens and the player stack in waves so every phase ships on top of the same shell instead of page-local styling hacks. Keep business/view-model semantics intact by confining changes to UI composition, visual contracts, and presentation-only helper state.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation Compose, Hilt ViewModel, Kotlin Coroutines/StateFlow, JUnit4, Compose UI Test

---

## Spec Reference

- Spec: `docs/superpowers/specs/2026-04-08-apple-music-cupertino-redesign-design.md`

## File Structure

- Modify: `app/src/main/java/com/music/myapplication/ui/theme/Theme.kt`
  - Keep as the single theme entry point, but delegate colors/locals to Apple token providers instead of QQ-green-first defaults.
- Modify: `app/src/main/java/com/music/myapplication/ui/theme/UiTokens.kt`
  - Keep legacy spacing/shape helpers compiling while redirecting call sites toward Apple token aliases.
- Modify: `app/src/main/java/com/music/myapplication/ui/theme/GlassModifiers.kt`
  - Downgrade current glass/background helpers into optional accent utilities instead of primary app language.
- Create: `app/src/main/java/com/music/myapplication/ui/theme/AppleThemeTokens.kt`
  - Define light/dark token sets, grouped surfaces, chrome colors, separators, and type/spacing constants.
- Create: `app/src/main/java/com/music/myapplication/ui/theme/AppleSurfaceStyle.kt`
  - Hold shared surface semantics and resolver helpers for page/grouped/elevated/floating layers.
- Create: `app/src/main/java/com/music/myapplication/ui/apple/AppleScaffold.kt`
  - Own safe-area calculation, large-title collapse behavior, chrome slots, and content insets.
- Create: `app/src/main/java/com/music/myapplication/ui/apple/AppleNavigationBar.kt`
  - Implement large-title + compact-title navigation chrome.
- Create: `app/src/main/java/com/music/myapplication/ui/apple/AppleTabBar.kt`
  - Replace Material `NavigationBar` with the floating Cupertino tab bar.
- Create: `app/src/main/java/com/music/myapplication/ui/apple/AppleSearchField.kt`
  - Shared Apple-style search shell for `Search` and any fake-entry search surfaces.
- Create: `app/src/main/java/com/music/myapplication/ui/apple/AppleGroupedSection.kt`
  - Shared grouped container and section header primitives.
- Create: `app/src/main/java/com/music/myapplication/ui/apple/AppleListRow.kt`
  - Shared inset-row primitive for settings/library rows.
- Create: `app/src/main/java/com/music/myapplication/ui/apple/AppleSheet.kt`
  - Shared Cupertino bottom sheet wrapper.
- Create: `app/src/main/java/com/music/myapplication/ui/apple/AppleDialog.kt`
  - Shared Cupertino dialog wrapper.
- Modify: `app/src/main/java/com/music/myapplication/MainActivity.kt`
  - Keep `enableEdgeToEdge`, but align system bar styling with the new theme/chrome contract.
- Modify: `app/src/main/java/com/music/myapplication/app/AppRoot.kt`
  - Move root layout orchestration onto `AppleScaffold`, add four-tab shell, and centralize mini-player/snackbar chrome.
- Modify: `app/src/main/java/com/music/myapplication/app/navigation/AppNavGraph.kt`
  - Keep routes stable but align transitions and screen wrappers with the new shell.
- Modify: `app/src/main/java/com/music/myapplication/app/navigation/Routes.kt`
  - Only if helper metadata for top-level tabs becomes necessary; do not change route identity.
- Modify: `app/src/main/java/com/music/myapplication/feature/home/HomeScreen.kt`
  - Recompose the screen as an Apple Music-style editorial feed on the shared scaffold.
- Modify: `app/src/main/java/com/music/myapplication/feature/search/SearchScreen.kt`
  - Rebuild default/search-result states around Apple search presentation.
- Modify: `app/src/main/java/com/music/myapplication/feature/library/LibraryScreen.kt`
  - Rebuild as system-style category-first library sections.
- Modify: `app/src/main/java/com/music/myapplication/feature/more/MoreScreen.kt`
  - Rebuild as grouped settings hub.
- Modify: `app/src/main/java/com/music/myapplication/feature/player/MiniPlayerBar.kt`
  - Convert into the floating capsule player used by `AppleScaffold`.
- Modify: `app/src/main/java/com/music/myapplication/feature/player/FullScreenPlayer.kt`
  - Re-skin the full player to the restrained Apple layout.
- Modify: `app/src/main/java/com/music/myapplication/feature/player/PlayerLyricsScreen.kt`
  - Align lyric view chrome and spacing with the player system.
- Modify: `app/src/main/java/com/music/myapplication/feature/player/PlayerBottomSheet.kt`
  - Rewrap queue/controls sheet inside shared Apple sheet primitives.
- Modify: `app/src/main/java/com/music/myapplication/feature/player/VideoPlayerScreen.kt`
  - Bring video chrome into the same design system without touching playback flow.
- Test: `app/src/test/java/com/music/myapplication/ui/theme/AppleThemeTokensTest.kt`
  - Lock token semantics and accent/surface mappings.
- Test: `app/src/test/java/com/music/myapplication/app/AppRootTest.kt`
  - Lock root chrome state and top-level tab decisions.
- Test: `app/src/androidTest/java/com/music/myapplication/ui/apple/AppleChromeTest.kt`
  - Verify scaffold/tab bar/mini player visual structure with Compose UI tests.
- Test: `app/src/androidTest/java/com/music/myapplication/feature/home/HomeScreenAppleLayoutTest.kt`
  - Verify editorial-home structure on-device.
- Test: `app/src/androidTest/java/com/music/myapplication/feature/search/SearchScreenAppleLayoutTest.kt`
  - Verify default/search-result layout structure.

## Preflight

### Task 0: Prepare an Isolated Worktree

**Files:**
- None

- [ ] **Step 1: Create a dedicated worktree**

Run:

```powershell
git worktree add '..\music-apple-cupertino-redesign' -b 'feat/apple-cupertino-redesign'
```

Expected: a sibling worktree is created from `HEAD` on a fresh branch.

- [ ] **Step 2: Move into the worktree and confirm it is clean**

Run:

```powershell
Set-Location '..\music-apple-cupertino-redesign'
git status --short
```

Expected: no output.

- [ ] **Step 3: Re-open the approved spec and this plan before editing**

Read:

- `docs/superpowers/specs/2026-04-08-apple-music-cupertino-redesign-design.md`
- `docs/superpowers/plans/2026-04-08-apple-music-cupertino-redesign.md`

Expected: implementation starts from the written design, not memory.

## Implementation Tasks

### Task 1: Build the Apple Theme and Surface Foundations

**Files:**
- Create: `app/src/main/java/com/music/myapplication/ui/theme/AppleThemeTokens.kt`
- Create: `app/src/main/java/com/music/myapplication/ui/theme/AppleSurfaceStyle.kt`
- Modify: `app/src/main/java/com/music/myapplication/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/music/myapplication/ui/theme/UiTokens.kt`
- Modify: `app/src/main/java/com/music/myapplication/ui/theme/GlassModifiers.kt`
- Test: `app/src/test/java/com/music/myapplication/ui/theme/AppleThemeTokensTest.kt`

- [ ] **Step 1: Write failing token tests**

Add tests that lock the intended visual contract:

```kotlin
@Test
fun lightTokens_useIosBlueAccentAndGroupedBackgrounds() {
    val tokens = appleThemeTokens(darkTheme = false)
    assertEquals(Color(0xFF007AFF), tokens.accent)
    assertEquals(Color(0xFFF2F2F7), tokens.groupedBackground)
}

@Test
fun darkTokens_keepFloatingChromeDistinctFromPageBackground() {
    val tokens = appleThemeTokens(darkTheme = true)
    assertNotEquals(tokens.pageBackground, tokens.floatingChrome)
}
```

- [ ] **Step 2: Run the token tests and confirm they fail**

Run:

```powershell
./gradlew.bat ':app:testDebugUnitTest' '--tests' 'com.music.myapplication.ui.theme.AppleThemeTokensTest'
```

Expected: FAIL because token providers do not exist yet.

- [ ] **Step 3: Implement the new token and surface files**

Create pure, testable structures:

```kotlin
data class AppleThemeTokens(
    val accent: Color,
    val pageBackground: Color,
    val groupedBackground: Color,
    val elevatedSurface: Color,
    val floatingChrome: Color,
    val separator: Color
)

enum class AppleSurfaceStyle {
    Page,
    Grouped,
    Elevated,
    Floating
}
```

Implementation rules:

- `Theme.kt` remains the only public theme entry.
- `MusicAppTheme` should expose Apple locals before Material consumers read colors.
- `UiTokens.kt` may keep `AppSpacing` and `AppShapes`, but must add Apple aliases instead of more hardcoded values.
- `GlassModifiers.kt` should keep helpers like `appPremiumBackground()`, but they must become optional accent layers, not the default chrome language.

- [ ] **Step 4: Re-run the token tests and compile the app**

Run:

```powershell
./gradlew.bat ':app:testDebugUnitTest' '--tests' 'com.music.myapplication.ui.theme.AppleThemeTokensTest'
./gradlew.bat ':app:compileDebugKotlin'
```

Expected: both PASS.

- [ ] **Step 5: Commit the theme foundation**

```powershell
git add 'app/src/main/java/com/music/myapplication/ui/theme/AppleThemeTokens.kt' 'app/src/main/java/com/music/myapplication/ui/theme/AppleSurfaceStyle.kt' 'app/src/main/java/com/music/myapplication/ui/theme/Theme.kt' 'app/src/main/java/com/music/myapplication/ui/theme/UiTokens.kt' 'app/src/main/java/com/music/myapplication/ui/theme/GlassModifiers.kt' 'app/src/test/java/com/music/myapplication/ui/theme/AppleThemeTokensTest.kt'
git commit -m 'feat(ui): add Apple theme foundation'
```

### Task 2: Replace the Root Chrome With Apple Scaffold + Four-Tab Shell

**Files:**
- Create: `app/src/main/java/com/music/myapplication/ui/apple/AppleScaffold.kt`
- Create: `app/src/main/java/com/music/myapplication/ui/apple/AppleNavigationBar.kt`
- Create: `app/src/main/java/com/music/myapplication/ui/apple/AppleTabBar.kt`
- Modify: `app/src/main/java/com/music/myapplication/MainActivity.kt`
- Modify: `app/src/main/java/com/music/myapplication/app/AppRoot.kt`
- Modify: `app/src/main/java/com/music/myapplication/app/navigation/AppNavGraph.kt`
- Modify: `app/src/test/java/com/music/myapplication/app/AppRootTest.kt`
- Test: `app/src/androidTest/java/com/music/myapplication/ui/apple/AppleChromeTest.kt`

- [ ] **Step 1: Add failing root-chrome tests**

Extend `AppRootTest.kt` with assertions for top-level shell policy:

```kotlin
@Test
fun topLevelTabs_includeSearchBetweenHomeAndLibrary() {
    val tabs = buildTopLevelTabs()
    assertEquals(listOf(Routes.Home, Routes.Search, Routes.Library, Routes.More), tabs.map { it.route })
}

@Test
fun videoRoute_hidesFloatingChrome() {
    val chrome = resolveAppChromeState(
        hasCurrentTrack = true,
        isSearchRoute = false,
        isPlayerLyricsRoute = false,
        isVideoPlayerRoute = true
    )
    assertFalse(chrome.showBottomBar)
    assertFalse(chrome.showMiniPlayer)
}
```

- [ ] **Step 2: Add a failing Compose chrome test**

Create `AppleChromeTest.kt` with a simple semantic assertion:

```kotlin
composeTestRule.onNodeWithContentDescription("Search").assertExists()
composeTestRule.onNodeWithTag("apple-mini-player").assertExists()
```

- [ ] **Step 3: Run the targeted tests and confirm they fail**

Run:

```powershell
./gradlew.bat ':app:testDebugUnitTest' '--tests' 'com.music.myapplication.app.AppRootTest'
./gradlew.bat ':app:connectedDebugAndroidTest' '-Pandroid.testInstrumentationRunnerArguments.class=com.music.myapplication.ui.apple.AppleChromeTest'
```

Expected: FAIL because the Apple scaffold and tab bar do not exist yet.

- [ ] **Step 4: Implement the new root shell**

Required behavior:

- `AppRoot.kt` stops rendering raw `NavigationBar`.
- `AppleScaffold` owns content slots for navigation bar, mini player, snackbar padding, and safe-area insets.
- Bottom tabs become `Home / Search / Library / More`.
- `MainActivity.kt` keeps `enableEdgeToEdge`, but nav/status icon appearance should flow from the new theme.
- `AppNavGraph.kt` keeps routes stable but uses softer Cupertino transitions.

Suggested scaffold contract:

```kotlin
@Composable
fun AppleScaffold(
    navigationSpec: AppleNavigationSpec,
    scaffoldState: AppleScaffoldState,
    bottomBar: @Composable (() -> Unit)?,
    floatingMiniPlayer: @Composable (() -> Unit)?,
    content: @Composable (PaddingValues) -> Unit
)
```

- [ ] **Step 5: Re-run the root tests and compile**

Run:

```powershell
./gradlew.bat ':app:testDebugUnitTest' '--tests' 'com.music.myapplication.app.AppRootTest'
./gradlew.bat ':app:connectedDebugAndroidTest' '-Pandroid.testInstrumentationRunnerArguments.class=com.music.myapplication.ui.apple.AppleChromeTest'
./gradlew.bat ':app:compileDebugKotlin'
```

Expected: all PASS.

- [ ] **Step 6: Commit the root chrome work**

```powershell
git add 'app/src/main/java/com/music/myapplication/ui/apple/AppleScaffold.kt' 'app/src/main/java/com/music/myapplication/ui/apple/AppleNavigationBar.kt' 'app/src/main/java/com/music/myapplication/ui/apple/AppleTabBar.kt' 'app/src/main/java/com/music/myapplication/MainActivity.kt' 'app/src/main/java/com/music/myapplication/app/AppRoot.kt' 'app/src/main/java/com/music/myapplication/app/navigation/AppNavGraph.kt' 'app/src/test/java/com/music/myapplication/app/AppRootTest.kt' 'app/src/androidTest/java/com/music/myapplication/ui/apple/AppleChromeTest.kt'
git commit -m 'feat(ui): add Apple app chrome'
```

### Task 3: Land Shared Search, Grouped Section, List Row, and Overlay Primitives

**Files:**
- Create: `app/src/main/java/com/music/myapplication/ui/apple/AppleSearchField.kt`
- Create: `app/src/main/java/com/music/myapplication/ui/apple/AppleGroupedSection.kt`
- Create: `app/src/main/java/com/music/myapplication/ui/apple/AppleListRow.kt`
- Create: `app/src/main/java/com/music/myapplication/ui/apple/AppleSheet.kt`
- Create: `app/src/main/java/com/music/myapplication/ui/apple/AppleDialog.kt`
- Modify: `app/src/main/java/com/music/myapplication/feature/player/PlayerBottomSheet.kt`
- Test: `app/src/androidTest/java/com/music/myapplication/ui/apple/AppleChromeTest.kt`

- [ ] **Step 1: Add failing Compose assertions for shared primitives**

Extend `AppleChromeTest.kt` with basic visibility/behavior checks:

```kotlin
composeTestRule.onNodeWithTag("apple-search-field").assertExists()
composeTestRule.onNodeWithTag("apple-grouped-section").assertExists()
```

- [ ] **Step 2: Run the Compose test and confirm failure**

Run:

```powershell
./gradlew.bat ':app:connectedDebugAndroidTest' '-Pandroid.testInstrumentationRunnerArguments.class=com.music.myapplication.ui.apple.AppleChromeTest'
```

Expected: FAIL because the shared primitives are missing.

- [ ] **Step 3: Implement the shared primitives and rewrap the player sheet**

Requirements:

- `AppleSearchField` supports passive entry mode and active text-entry mode.
- `AppleGroupedSection` owns grouped padding, title layout, separator rhythm, and background shape.
- `AppleListRow` supports icon, title, subtitle/value, trailing switch/chevron.
- `AppleSheet` and `AppleDialog` wrap Material implementations so page code stops reaching for raw `ModalBottomSheet` and `AlertDialog`.
- `PlayerBottomSheet.kt` becomes the first production consumer of `AppleSheet`.

- [ ] **Step 4: Re-run Compose test and compile**

Run:

```powershell
./gradlew.bat ':app:connectedDebugAndroidTest' '-Pandroid.testInstrumentationRunnerArguments.class=com.music.myapplication.ui.apple.AppleChromeTest'
./gradlew.bat ':app:compileDebugKotlin'
```

Expected: both PASS.

- [ ] **Step 5: Commit the shared primitive layer**

```powershell
git add 'app/src/main/java/com/music/myapplication/ui/apple/AppleSearchField.kt' 'app/src/main/java/com/music/myapplication/ui/apple/AppleGroupedSection.kt' 'app/src/main/java/com/music/myapplication/ui/apple/AppleListRow.kt' 'app/src/main/java/com/music/myapplication/ui/apple/AppleSheet.kt' 'app/src/main/java/com/music/myapplication/ui/apple/AppleDialog.kt' 'app/src/main/java/com/music/myapplication/feature/player/PlayerBottomSheet.kt' 'app/src/androidTest/java/com/music/myapplication/ui/apple/AppleChromeTest.kt'
git commit -m 'feat(ui): add shared Apple primitives'
```

### Task 4: Rebuild Home as an Editorial Apple Music Feed

**Files:**
- Modify: `app/src/main/java/com/music/myapplication/feature/home/HomeScreen.kt`
- Test: `app/src/androidTest/java/com/music/myapplication/feature/home/HomeScreenAppleLayoutTest.kt`

- [ ] **Step 1: Add a failing home layout test**

Create a Compose UI test that locks the new structure:

```kotlin
composeTestRule.onNodeWithText("Home").assertExists()
composeTestRule.onNodeWithTag("home-hero-card").assertExists()
composeTestRule.onNodeWithTag("home-top-picks-section").assertExists()
```

- [ ] **Step 2: Run the home layout test and confirm it fails**

Run:

```powershell
./gradlew.bat ':app:connectedDebugAndroidTest' '-Pandroid.testInstrumentationRunnerArguments.class=com.music.myapplication.feature.home.HomeScreenAppleLayoutTest'
```

Expected: FAIL because the editorial structure does not exist yet.

- [ ] **Step 3: Recompose `HomeScreen.kt` around the shared scaffold**

Implementation rules:

- remove remaining obvious Material/QQ shell cues from the page.
- use large-title navigation behavior from `AppleScaffold`.
- top of the page is a hero recommendation card, not a toolbar row.
- group content into editorial sections with clear titles and horizontal or inset-row rhythms.
- preserve existing callbacks and `HomeViewModel` semantics unless the page needs presentation-only helper state.

Suggested ordering:

1. hero recommendation
2. top picks / made for you
3. playlist or chart preview
4. secondary grouped rows

- [ ] **Step 4: Re-run the home layout test and compile**

Run:

```powershell
./gradlew.bat ':app:connectedDebugAndroidTest' '-Pandroid.testInstrumentationRunnerArguments.class=com.music.myapplication.feature.home.HomeScreenAppleLayoutTest'
./gradlew.bat ':app:compileDebugKotlin'
```

Expected: both PASS.

- [ ] **Step 5: Commit the home redesign**

```powershell
git add 'app/src/main/java/com/music/myapplication/feature/home/HomeScreen.kt' 'app/src/androidTest/java/com/music/myapplication/feature/home/HomeScreenAppleLayoutTest.kt'
git commit -m 'feat(home): redesign home as editorial Apple feed'
```

### Task 5: Rebuild Search, Library, and More on Shared Primitives

**Files:**
- Modify: `app/src/main/java/com/music/myapplication/feature/search/SearchScreen.kt`
- Modify: `app/src/main/java/com/music/myapplication/feature/library/LibraryScreen.kt`
- Modify: `app/src/main/java/com/music/myapplication/feature/more/MoreScreen.kt`
- Test: `app/src/androidTest/java/com/music/myapplication/feature/search/SearchScreenAppleLayoutTest.kt`
- Create: `app/src/androidTest/java/com/music/myapplication/feature/library/LibraryScreenAppleLayoutTest.kt`
- Create: `app/src/androidTest/java/com/music/myapplication/feature/more/MoreScreenAppleLayoutTest.kt`

- [ ] **Step 1: Add failing screen-layout tests**

Examples:

```kotlin
composeTestRule.onNodeWithTag("apple-search-field").assertExists()
composeTestRule.onNodeWithText("Library").assertExists()
composeTestRule.onNodeWithText("Settings").assertExists()
```

Targets:

- `Search` default state shows large title + grouped suggestions.
- `Library` shows category-first grouped rows.
- `More` shows settings sections, not an icon grid or utility pile.

- [ ] **Step 2: Run the screen-layout tests and confirm failure**

Run:

```powershell
./gradlew.bat ':app:connectedDebugAndroidTest' '-Pandroid.testInstrumentationRunnerArguments.class=com.music.myapplication.feature.search.SearchScreenAppleLayoutTest,com.music.myapplication.feature.library.LibraryScreenAppleLayoutTest,com.music.myapplication.feature.more.MoreScreenAppleLayoutTest'
```

Expected: FAIL.

- [ ] **Step 3: Rebuild `SearchScreen.kt`**

Requirements:

- default state uses `AppleSearchField` + grouped recent/suggestion content.
- active state groups results by type instead of one undifferentiated list.
- existing search, debounce, paging, and platform semantics remain intact.

- [ ] **Step 4: Rebuild `LibraryScreen.kt` and `MoreScreen.kt`**

Requirements:

- `LibraryScreen.kt` becomes category-first, using `AppleGroupedSection` and `AppleListRow`.
- `MoreScreen.kt` becomes a settings hub with grouped rows, switches, values, and chevrons.
- no business route disappears; entries just move into grouped settings structure.

- [ ] **Step 5: Re-run screen-layout tests and compile**

Run:

```powershell
./gradlew.bat ':app:connectedDebugAndroidTest' '-Pandroid.testInstrumentationRunnerArguments.class=com.music.myapplication.feature.search.SearchScreenAppleLayoutTest,com.music.myapplication.feature.library.LibraryScreenAppleLayoutTest,com.music.myapplication.feature.more.MoreScreenAppleLayoutTest'
./gradlew.bat ':app:compileDebugKotlin'
```

Expected: all PASS.

- [ ] **Step 6: Commit the top-level screen migration**

```powershell
git add 'app/src/main/java/com/music/myapplication/feature/search/SearchScreen.kt' 'app/src/main/java/com/music/myapplication/feature/library/LibraryScreen.kt' 'app/src/main/java/com/music/myapplication/feature/more/MoreScreen.kt' 'app/src/androidTest/java/com/music/myapplication/feature/search/SearchScreenAppleLayoutTest.kt' 'app/src/androidTest/java/com/music/myapplication/feature/library/LibraryScreenAppleLayoutTest.kt' 'app/src/androidTest/java/com/music/myapplication/feature/more/MoreScreenAppleLayoutTest.kt'
git commit -m 'feat(ui): migrate search library and more to Apple layout'
```

### Task 6: Rebuild the Mini Player and Full-Screen Player Stack

**Files:**
- Modify: `app/src/main/java/com/music/myapplication/feature/player/MiniPlayerBar.kt`
- Modify: `app/src/main/java/com/music/myapplication/feature/player/FullScreenPlayer.kt`
- Modify: `app/src/main/java/com/music/myapplication/feature/player/PlayerLyricsScreen.kt`
- Modify: `app/src/main/java/com/music/myapplication/feature/player/VideoPlayerScreen.kt`
- Test: `app/src/androidTest/java/com/music/myapplication/feature/player/PlayerChromeAppleLayoutTest.kt`

- [ ] **Step 1: Add a failing player chrome test**

Target assertions:

```kotlin
composeTestRule.onNodeWithTag("apple-mini-player").assertExists()
composeTestRule.onNodeWithTag("apple-player-artwork").assertExists()
composeTestRule.onNodeWithTag("apple-player-progress").assertExists()
```

- [ ] **Step 2: Run the player chrome test and confirm it fails**

Run:

```powershell
./gradlew.bat ':app:connectedDebugAndroidTest' '-Pandroid.testInstrumentationRunnerArguments.class=com.music.myapplication.feature.player.PlayerChromeAppleLayoutTest'
```

Expected: FAIL.

- [ ] **Step 3: Convert `MiniPlayerBar.kt` into the floating capsule player**

Requirements:

- use shared floating surface tokens.
- stop looking like a wide Material control bar.
- keep current track info, play/pause, and next intact.
- expose a stable test tag such as `apple-mini-player`.

- [ ] **Step 4: Rebuild `FullScreenPlayer.kt`, `PlayerLyricsScreen.kt`, and `VideoPlayerScreen.kt`**

Requirements:

- `FullScreenPlayer.kt`: restrained artwork-first layout, subtle dynamic background, thin progress bar, Apple-style control grouping.
- `PlayerLyricsScreen.kt`: typography, chrome, and motion aligned with the full player.
- `VideoPlayerScreen.kt`: same design language for top/bottom overlays without changing playback behavior.

- [ ] **Step 5: Re-run player chrome test and compile**

Run:

```powershell
./gradlew.bat ':app:connectedDebugAndroidTest' '-Pandroid.testInstrumentationRunnerArguments.class=com.music.myapplication.feature.player.PlayerChromeAppleLayoutTest'
./gradlew.bat ':app:compileDebugKotlin'
```

Expected: both PASS.

- [ ] **Step 6: Commit the player stack redesign**

```powershell
git add 'app/src/main/java/com/music/myapplication/feature/player/MiniPlayerBar.kt' 'app/src/main/java/com/music/myapplication/feature/player/FullScreenPlayer.kt' 'app/src/main/java/com/music/myapplication/feature/player/PlayerLyricsScreen.kt' 'app/src/main/java/com/music/myapplication/feature/player/VideoPlayerScreen.kt' 'app/src/androidTest/java/com/music/myapplication/feature/player/PlayerChromeAppleLayoutTest.kt'
git commit -m 'feat(player): migrate player stack to Apple chrome'
```

### Task 7: Overlay Cleanup, Regression Verification, and Final Polish

**Files:**
- Modify: `app/src/main/java/com/music/myapplication/app/AppRoot.kt`
- Modify: `app/src/main/java/com/music/myapplication/feature/player/PlayerBottomSheet.kt`
- Modify: `app/src/main/java/com/music/myapplication/feature/search/SearchScreen.kt`
- Modify: `app/src/main/java/com/music/myapplication/feature/library/LibraryScreen.kt`
- Modify: `app/src/main/java/com/music/myapplication/feature/more/MoreScreen.kt`
- Modify: any `AlertDialog` / `ModalBottomSheet` call sites found during implementation
- Test: `app/src/androidTest/java/com/music/myapplication/ui/apple/AppleChromeTest.kt`
- Test: `app/src/androidTest/java/com/music/myapplication/feature/player/PlayerChromeAppleLayoutTest.kt`

- [ ] **Step 1: Add a failing regression check for raw Material chrome leftovers**

Use exact text search before editing:

```powershell
rg -n 'NavigationBar\\(|OutlinedTextField\\(|ModalBottomSheet\\(|AlertDialog\\(' 'app/src/main/java/com/music/myapplication'
```

Expected: at least one remaining match in production UI files.

- [ ] **Step 2: Replace or justify the remaining raw Material chrome**

Rules:

- top-level shell must not use raw `NavigationBar`.
- reusable bottom sheets/dialogs should go through `AppleSheet` / `AppleDialog`.
- if a raw Material component remains for a justified reason, document it in code comments and in the final review note.

- [ ] **Step 3: Run full automated verification**

Run:

```powershell
./gradlew.bat ':app:testDebugUnitTest'
./gradlew.bat ':app:compileDebugKotlin'
./gradlew.bat ':app:connectedDebugAndroidTest'
```

Expected: all PASS.

- [ ] **Step 4: Run manual design QA**

Verify manually on device/emulator:

1. `Home / Search / Library / More` all read as one Apple-style product.
2. large title collapse works on scroll.
3. tab bar and mini player spacing stay stable with and without a current track.
4. search default state and result state both match the design language.
5. library rows and settings rows share consistent grouped styling.
6. full player, lyrics, and video player feel like one playback system.
7. dark theme remains restrained and readable.

- [ ] **Step 5: Commit the final polish**

```powershell
git add 'app/src/main/java/com/music/myapplication/app/AppRoot.kt' 'app/src/main/java/com/music/myapplication/feature/player/PlayerBottomSheet.kt' 'app/src/main/java/com/music/myapplication/feature/search/SearchScreen.kt' 'app/src/main/java/com/music/myapplication/feature/library/LibraryScreen.kt' 'app/src/main/java/com/music/myapplication/feature/more/MoreScreen.kt' 'app/src/main/java/com/music/myapplication/ui/apple' 'app/src/androidTest/java/com/music/myapplication/ui/apple/AppleChromeTest.kt' 'app/src/androidTest/java/com/music/myapplication/feature/player/PlayerChromeAppleLayoutTest.kt'
git commit -m 'feat(ui): finish Apple Cupertino redesign polish'
```

## Notes for the Implementer

- Do not change repository, playback, download, or routing semantics unless the plan explicitly asks for a UI-only helper.
- Keep `Routes` identities stable. `Search` is already a route; this project does not need a new top-level destination type.
- If a screen file starts growing uncontrollably, split screen-local layout helpers into same-feature files before adding more code.
- Prefer `Apple*` shared primitives over screen-local modifiers. If a page needs a special surface, teach the primitive a new style instead of forking it.
- Run Gradle tasks serially on Windows to avoid KSP/Hilt race weirdness.

## Review Checklist

Use this checklist before executing the plan:

- every phase leaves the app compiling and visually coherent on the shared shell.
- no step changes business semantics while claiming to be UI-only.
- four top-level tabs are present and stable: `Home / Search / Library / More`.
- grouped surfaces, list rows, search fields, sheets, and dialogs route through shared Apple primitives.
- verification commands are exact and runnable in this repository.
