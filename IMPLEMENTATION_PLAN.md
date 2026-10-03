# PebbleDetective — Implementation Plan

## Context

`PROJECT.md` describes a kids' Android app: a child points the phone at a pebble, taps to mark it, the app takes a "target captured" snapshot, runs a theatrical fake "Deep Research", announces which planet the pebble came from (derived from its dominant colour), and plays a 10–15 s schematic 3D animation of the pebble flying from that planet to Earth. Three languages switchable at any moment, sound effects per stage (off by default), and a persistent on-disk log of every pebble with date, time, location and photo.

Today the repository is a bare Kotlin/JVM Gradle scaffold — one `src/main/kotlin/Main.kt` hello-world, no Android plugin, no manifest, no `local.properties`. **Everything is new work.** The goal of this plan is a buildable, runnable Android app delivered in phases, each of which ends with something you can see on a device.

The whole experience is **offline by design**: the "Deep Research" is theatre, so the app declares no `INTERNET` permission at all. Nothing a child photographs ever leaves the phone. This is a deliberate, checkable property, not an afterthought.

### Decisions you confirmed
| Question | Decision |
|---|---|
| Camera | **Rear camera only** — the usable one for a pebble on the ground; no flip control. `PROJECT.md` originally said "frontal" and has since been corrected to match. |
| Languages | **English, Russian, Japanese** |
| Planet art | **Real NASA photos on the result screen**, procedural schematic drawing in the 3D journey |
| Sound | **Bundled royalty-free audio files** |

---

## Verified environment facts

| Fact | Value | Consequence |
|---|---|---|
| Android SDK platforms | started with only `android-36.1`; Phase 0 installed `android-36` and **`android-37.0`** | `compileSdk 37` needs `android-37.0` — there is no plain `platforms;android-37` |
| Build tools | 36.0.0, 36.1.0, 37.0.0 | fine |
| JDK | 17.0.12 | AGP 9.4's minimum is 17 — no upgrade needed |
| Gradle wrapper | started at 8.14; now **9.8.0** | AGP 9.4.1 needs ≥ 9.6; Kotlin 2.4.20 needs ≥ 8.14.4 |
| Existing AVD | `loop_test`: **96 MB RAM, GPU off, no front camera** | unusable for this app — Phase 0 creates a new AVD |
| System image installed | `android-35;google_apis;x86_64` | `android-36;google_apis;x86_64` is available to download and preferred |
| `ANDROID_HOME` | **unset** | `local.properties` must carry `sdk.dir` |
| Network | reachable; Google Maven + Maven Central OK | dependencies will resolve |

### Asset sources (both verified reachable today)
- **Planet photos** — NASA Image and Video Library, public domain, no API key:
  `https://images-api.nasa.gov/search?...&media_type=image` → take `nasa_id` → `https://images-api.nasa.gov/asset/<nasa_id>` → use the `~medium.jpg` variant. Confirmed working end to end.
  A naive `q=<body>` search **fails for Mercury, Venus, Jupiter and Uranus**, so the fetch script carries a curated per-body query table (e.g. `Mercury MESSENGER globe` → `PIA12051`, `Venus Magellan globe` → `PIA00257`, `Jupiter Cassini globe` → `PIA02864`, `Uranus Voyager 2` → `PIA18182`). All nine bodies confirmed obtainable this way.
- **Sound effects** — OpenGameArt **CC0** packs (confirmed present: *"60 CC0 Sci-Fi SFX"*, *"63 digital sound effects (lasers, phasers, space, etc)"*). CC0 only, so there is no attribution chain to maintain — credits are still recorded.
- Freesound needs an account and Pixabay blocks scripted access, so neither is used.

---

## Architecture

Single Gradle module `:app`. Single `Activity`, Jetpack Compose, `navigation-compose`. MVVM with one Activity-scoped `SessionViewModel` that owns the flow state machine, plus small per-screen view models where useful. Manual dependency injection through an `AppContainer` held by the `Application` — no Hilt, no KSP anywhere (this is why persistence is JSON rather than Room: it removes the KSP↔Kotlin version coupling entirely).

```
PebbleDetective/
  settings.gradle.kts            ← rewritten: pluginManagement + google(), include(":app")
  build.gradle.kts               ← rewritten: root, plugins `apply false`
  gradle/libs.versions.toml      ← new: version catalog
  local.properties               ← new (gitignored): sdk.dir
  src/main/kotlin/Main.kt        ← DELETED
  tools/fetch_assets.ps1         ← new: downloads + resizes NASA photos and CC0 sfx
  ASSETS.md                      ← new: manifest — file, source URL, licence, credit
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/res/values{,-ru,-ja}/strings.xml
    src/main/res/xml/locales_config.xml
    src/main/res/raw/*.ogg                  ← sound cues
    src/main/assets/planets/*.jpg           ← NASA photos
    src/main/kotlin/com/pebbledetective/
      PebbleApp.kt  MainActivity.kt  core/AppContainer.kt
      domain/   Planet.kt  PlanetPicker.kt  DominantColour.kt  SessionState.kt  Timeline.kt
      data/     PebbleEntry.kt  PebbleRepository.kt  PhotoStore.kt  SettingsStore.kt  LocationProvider.kt
      audio/    SoundCue.kt  SoundPlayer.kt
      ui/       theme/  nav/PebbleNavHost.kt  common/TopControls.kt
                capture/   CaptureScreen.kt  ReticleOverlay.kt  StabilityDetector.kt
                research/  ResearchScreen.kt  MatrixRain.kt  SatelliteLink.kt
                result/    ResultScreen.kt
                journey/   JourneyScreen.kt  Scene3d.kt  Projection.kt
                history/   HistoryScreen.kt  HistoryDetailScreen.kt
    src/test/kotlin/...                     ← JVM unit tests
```

### Flow state machine
`SessionState` (sealed interface in `domain/SessionState.kt`), owned by `SessionViewModel`:

`Framing` → `Captured(photo, tapPoint)` → `Asking` → `Researching(startedAt)` → `Result(entry)` → `Journey(entry)` → back to `Framing`

`History` and `HistoryDetail` are separate nav destinations that do not disturb the session.

### Why the language toggle shapes the architecture
The obvious implementation, `AppCompatDelegate.setApplicationLocales()`, **recreates the Activity**. The ViewModel would survive that, but the CameraX binding would not — the camera unbinds and reopens, giving 200–500 ms of black preview, and the viewfinder's `Surface` is destroyed. Doing that *mid-journey* is exactly the "any stage" the spec asks for.

So the language is applied **inside the composition instead**, with no recreation at all:

```kotlin
val cfg = remember(tag) { Configuration(ctx.resources.configuration)
    .apply { setLocales(LocaleList.forLanguageTags(tag)) } }
val localized = remember(cfg) { ctx.createConfigurationContext(cfg) }
CompositionLocalProvider(
    LocalContext provides localized,
    LocalResources provides localized.resources,   // which one stringResource reads has moved between Compose versions — override both
    LocalConfiguration provides cfg,
) { ... }
```

The camera stays bound, coroutines keep running, the animation does not blink. Two consequences are handled explicitly: `Locale.getDefault()` is unchanged, so **every date formatter is passed the selected locale explicitly** (the classic silent bug with this approach); and the manifest keeps `localeConfig` so a language chosen from Android Settings is still picked up as the initial value at startup.

Durable state lives in the Activity-scoped `SessionViewModel` anyway, and animations run off an elapsed-time clock rather than `remember`ed `Animatable` state — so even an unexpected recreation lands the child on the right frame rather than restarting the sequence.

---

## Technical decisions

**Build.** AGP **9.4.1**, Gradle **9.8.0**, JDK **17**, Kotlin **2.4.20** via AGP's **built-in Kotlin** (applying `org.jetbrains.kotlin.android` is a hard error under AGP 9's new DSL), Compose BOM **2026.09.00** (Compose 1.12.1, Material3 1.7.x), `compileSdk 37` (platform `android-37.0`), `targetSdk 36`, `minSdk 26`. See *Resolved during implementation* for why this is not the AGP 8.13 matrix originally planned.

**Camera.** CameraX 1.6.2 — `Preview` + `ImageCapture`, rear lens only (`CameraSelector.DEFAULT_BACK_CAMERA`). `PreviewView` inside `AndroidView`.

**Freeze frame.** `takePicture(executor, OnImageCapturedCallback)` — the **in-memory `ImageProxy`**, not the file variant. The file variant records orientation in EXIF rather than rotating pixels, and `BitmapFactory` ignores EXIF, so the displayed still and the analysed bitmap would silently disagree by 90° on most phones. Instead: `proxy.toBitmap()`, rotate once by `proxy.imageInfo.rotationDegrees`, and persist those already-upright pixels. One bitmap is both shown and analysed, so they cannot diverge.

Resolution is capped via `ResolutionSelector` at roughly 1440×1080 — a 50 MP still as ARGB_8888 is ~200 MB and will OOM.

Sequence, to avoid a visible gap: viewfinder and still live in the same `Box` with the still drawn second; on capture the still appears *over* the still-running preview, and only one frame later (`withFrameNanos` then unbind) is the camera released. A ~120 ms flash plus the reticle-lock animation masks the swap and doubles as the "target captured" effect.

Preview and capture are bound through a shared `ViewPort` in a `UseCaseGroup`, so the still's crop matches what the child saw and the tap point maps 1:1 onto the captured pixels. Without this, `FILL_CENTER` cropping makes the tap land on the wrong part of the photo.

**Stability.** `TYPE_GYROSCOPE` at `SENSOR_DELAY_GAME`, thresholded on angular-rate magnitude (drift-free, no gravity separation needed); `TYPE_ACCELEROMETER` with a complementary filter on the residual as fallback. Stable when the 90th percentile over a 500 ms window stays under ~0.20 rad/s, with hysteresis (exit at 0.40) so the reticle doesn't strobe.

A tap while unsteady **arms** rather than rejects: `IDLE → ARMED → stable → CAPTURING`, firing automatically the moment it settles. That satisfies "stabilised *and* tapped" while never dead-ending. Thresholds relax progressively after 2.5 s and 5 s and capture unconditionally at 7 s — kids' hands do not hold still. The reticle always shows a progress ring so the child can see *why* nothing has happened yet.

**Colour → planet.** Pure, unit-testable function over an `IntArray` of pixels (no `android.graphics` in the signature, so it runs on the JVM). Samples a disc of radius ≈12% of `min(w,h)` around the tap point, downscales to ~48×48, converts to HSV, then:

- **discards** pixels with `V < 0.12` (shadow) and `V > 0.96` — wet or shiny pebbles blow out to specular white, which would otherwise dominate the result;
- takes the **modal bin of a 24-bin saturation-weighted hue histogram**, never a mean. Hue is circular, so averaging 350° and 10° yields cyan instead of red — a classic and silent bug;
- treats median `S < 0.18` as grey/unknown. The cutoff is deliberately generous because outdoor shade is strongly blue-biased and would otherwise read grey pebbles as Neptune.

`androidx.palette` is deliberately **not** used: its quantizer is tuned for album art and biases hard toward vibrant colours, which is exactly wrong for grey-brown rocks.

Mapping, in order:

| Rule | Planet |
|---|---|
| very dark (V < 0.22) | Mercury |
| **low saturation (S < 0.12) — grey/unknown** | **random of all nine** (per spec) |
| pale tan/gold (S 0.12–0.22, H 25–60) | Saturn |
| red / rust (H 0–15, 330–360) | Mars |
| orange / brown (H 15–40) | Jupiter |
| bright yellow (H 40–65, V ≥ 0.80) | Sun |
| creamy yellow (H 40–65, V < 0.80) | Venus |
| green (H 65–165) | Earth |
| cyan / pale blue (H 165–205) | Uranus |
| deep blue (H 205–280) | Neptune |
| violet / pink (H 280–330) | Mars |

**Earth is excluded as a source** — the pebble flies *to* Earth, so the green bucket and the random pick both draw from the other eight bodies.

**The chosen planet is written into the record and never recomputed.** The random path is seeded from the entry's UUID and the result persisted; otherwise reopening a grey pebble in the logbook would show a different planet each time, which a child will absolutely notice and which reads as broken. `Random` is injected so tests are deterministic.

**Persistence.** `kotlinx-serialization-json` 1.11.0. Photos as JPEG in `filesDir/pebbles/<id>.jpg` plus a 256 px thumbnail (the history grid only ever decodes thumbs — fifty full-size JPEGs will jank or OOM).

The index is **JSONL, one record per line, append-only** rather than a single JSON array. Appending is O(1) and crash-tolerant: a process kill mid-write costs at most a trailing partial line, which is dropped on read. Rewriting a whole array on every capture is both O(n) and a window in which the entire history can be lost. Edits and deletes compact the file by writing `index.jsonl.tmp` and renaming. Reads are `Mutex`-guarded and exposed as a `StateFlow<List<PebbleEntry>>`.

**Process death.** The record is written the moment the JPEG lands, with `status = CAPTURED`, and updated to `RESEARCHED` once the planet is known — so a kill mid-research can never lose the photo. `currentPebbleId` and `phase` go in `SavedStateHandle` so a cold start returns to the reveal rather than to the camera. Mid-animation position is not restored across process death; the pebble is what matters.

**Location.** `LocationManagerCompat.getCurrentLocation` from `androidx.core` — **not** `play-services-location`. A one-shot fix from `FUSED_PROVIDER` (API 31+) falling back to GPS/network needs no GMS dependency, works on non-GMS devices, and keeps the dependency graph free of anything that could drag in networking. **Coarse** permission only, requested just before the first research, 10 s timeout, entirely optional — denial logs the entry without coordinates and blocks nothing.

**Settings.** Sound on/off in `SharedPreferences` behind a `SettingsStore` exposing a `StateFlow`; **off by default** per spec. The chosen language is persisted by AppCompat's own per-app-locale storage, so it needs no key of ours.

**Audio.** `SoundPool` for the short cues, a second `SoundPool`/`MediaPlayer` for the two loops, behind a `SoundPlayer` interface that is a no-op when sound is off. Cues: `ui_tap`, `reticle_lock`, `shutter`, `popup_open`, `satellite_ping`, `signal_acquired`, `matrix_hum` (loop), `progress_complete`, `launch_whoosh`, `space_drone` (loop), `entry_rumble`, `arrival_chime`.

**Timeline.** Both timed sequences run off one pattern: the ViewModel owns `(phase, startedAtElapsedRealtime, pausedAccumMs)` and the UI is a pure function `sceneAt(t): SceneState`. The frame driver is **`withFrameNanos`, not `withInfiniteAnimationFrameNanos`** — the sequences are finite, and the infinite variant cooperates with `InfiniteAnimationPolicy`, which makes Compose UI tests hang forever. A wall-clock timeline is also immune to `ANIMATOR_DURATION_SCALE = 0`, which would otherwise make the 15 s showpiece complete instantly on a developer's device.

Because `sceneAt` is pure, "at t = 4800 ms the phase is REVEAL and progress ≥ 0.95" is a plain JVM unit test — no Compose, no Robolectric.

**Sound cues are scheduled separately** from the frame loop, as a data-driven `List<Cue(atMs, sound)>` consumed by a `viewModelScope` coroutine. Frames may drop; audio should not. With an injected dispatcher this is testable under `runTest` virtual time — `advanceTimeBy(5_000)` and assert the cue order. The progress bar derives from the same `t` as everything else, so it cannot desync from what it reports.

**Skip is mandatory on both the 5 s research and the 10–15 s journey**, and both auto-skip when `ANIMATOR_DURATION_SCALE` is 0. By the third pebble an unskippable cutscene is hostile.

**Journey rendering.** Hand-rolled perspective projection in a Compose `Canvas`: camera at origin, `screen = (f·x/z + cx, f·y/z + cy)`, radius scaled by `1/z`, painter's-algorithm depth sort. Starfield of ~400 recycled points. Compose drawing has a few specific traps that decide 60 fps versus 25 fps:

- the frame clock writes a `mutableLongStateOf` that is read **only inside the draw lambda**, so each frame invalidates the draw phase alone and skips composition and layout entirely;
- stars are a pre-allocated `FloatArray(n*2)` mutated in place and drawn through `drawIntoCanvas { it.nativeCanvas.drawPoints(array, paint) }` — the `drawPoints(List<Offset>)` overload would allocate a list every frame;
- `Paint`, `Path`, gradients and `TextMeasurer` results are all `remember`ed; `path.rewind()` rather than a fresh `Path()`. A `Brush.radialGradient` built inside draw recompiles a shader every frame;
- depth sorting reuses an `IntArray` of indices with an in-place insertion sort (the data is near-sorted frame to frame, so this is effectively free and allocation-free);
- the pebble itself rides `Modifier.graphicsLayer { ... }` in its **lambda form**, so transforms invalidate only the layer.

OpenGL via `GLSurfaceView` is explicitly rejected: a few hundred points and some schematic primitives are far below where Skia struggles, and it would cost EGL lifecycle management, a second render thread, and broken Compose overlay composition for no gain.

---

## Phases

**All phases are complete.** Each was built, run on the emulator and pushed
before the next began. Findings are recorded under *Resolved during
implementation*.

Each phase ends with something runnable.

### Phase 0 — Make it an Android project that builds and runs
- `sdkmanager "platforms;android-36"` and **`"platforms;android-37.0"`** (`compileSdk 37` resolves to the latter). Also `system-images;android-36;google_apis;x86_64` so the test device matches `targetSdk 36`; the already-installed API-35 image is the fallback.
- Create AVD `pebble_test` (via `avdmanager.bat`, confirmed present) with ≥2 GB RAM, GPU auto, `hw.camera.back=webcam0` so a real webcam can be pointed at a real pebble. The existing `loop_test` AVD is left untouched.
- Write `local.properties` (`sdk.dir`), rewrite `settings.gradle.kts` (pluginManagement + `google()`, `include(":app")`) and the root `build.gradle.kts`, add `gradle/libs.versions.toml`, create `app/` with manifest, theme, icons and a placeholder Compose screen. Delete `src/main/kotlin/Main.kt`.
- Extend `.gitignore` for `local.properties` and `app/build/`.
- **Done when** `./gradlew :app:assembleDebug` succeeds and the placeholder screen shows on the AVD.

### Phase 1 — Shell: localisation, settings, sound, navigation
- `strings.xml` for `values/` (en), `values-ru/`, `values-ja/`; `locales_config.xml`; `AppCompatDelegate.setApplicationLocales` wired to a globe button.
- `TopControls` (globe + speaker + history) overlaid on every screen, so both toggles are reachable at any stage.
- `SettingsStore`, `SoundPlayer` with the cue enum (silent stubs until Phase 1b), `PebbleNavHost`, `SessionViewModel` skeleton, theme.
- **Verify** switching EN→RU→JA relabels everything live and the choice survives a restart.

### Phase 1b — Assets
- `tools/fetch_assets.ps1` downloads the nine NASA photos (search → asset → `~medium.jpg`, downscaled to ~1024 px) and the CC0 sound cues, and writes `ASSETS.md` listing every file with its source URL, licence and credit.
- **Checkpoint:** I will show you `ASSETS.md` before the files are committed, so you can approve what gets bundled into your app.
- An in-app credits screen reachable from history.

### Phase 2 — Capture
- Camera permission flow with a friendly kid-facing rationale. On permanent denial the app offers a settings shortcut **and falls back to the Android Photo Picker** (`ActivityResultContracts.PickVisualMedia`), which needs no permission at all — the whole Deep Research flow then runs on an existing photo. That turns the one dead end in the app into a feature.
- CameraX preview (rear), animated reticle overlay, `StabilityDetector`, tap-to-target, shutter, freeze frame, photo + thumbnail written via `PhotoStore`.
- **Verify** on the AVD with a webcam, and on a physical device for real sensor behaviour.

### Phase 3 — Deep Research
- Animated yes/no pop-up over the frozen still.
- 5 s sequence on a single elapsed-time clock: satellite acquisition (~0–1.3 s, sweeping dish + pings) → "signal acquired" → matrix rain with streaming localised analysis lines → "match found", with the progress bar spanning the full 5 s. Flash rates kept below 3 Hz for photosensitivity.
- Real work runs underneath: dominant-colour analysis, planet pick, optional location fix, log entry written.
- **Verify** language switch mid-research resumes at the same point.

### Phase 4 — Result
- NASA photo of the chosen body, localised name, a short localised fact, buttons for the journey and for a new pebble.

### Phase 5 — Journey
- 12 s schematic flight on the same clock pattern: departure → launch → cruise (starfield, tumbling pebble on a Bézier arc, shrinking origin planet, growing Earth, distance HUD) → atmospheric entry → landing. Sound cues per beat. Skip button throughout.
- **Verify** frame pacing on the physical device; language switch mid-flight resumes in place.

### Phase 6 — History
- List (thumbnail, date, time, planet, place) and detail (full photo, planet, coordinates, re-run journey). Empty state for a first-time user.

### Phase 7 — Polish and tests
- Tap targets ≥56 dp, `contentDescription` everywhere, reduced-motion respect, process-death restore, storage growth check, delete-entry affordance.
- Unit tests: `PlanetPickerTest` (every bucket, grey→seeded random, all nine reachable), `DominantColourTest` (synthetic pixel arrays), `PebbleRepositoryTest` (round-trip, atomic write, corrupt-index recovery), `TimelineTest` (research phases tile 0–5 s with no gap; journey within 10–15 s).

---

### Phase 2-7 — findings worth keeping

- **The in-composition locale override crashed the app.** Passing
  `createConfigurationContext()` to `LocalContext` detaches it from the
  Activity, so `rememberLauncherForActivityResult` threw
  "No ActivityResultRegistryOwner was provided" and took out both the camera
  permission request and the photo picker. `LocalizedContext` now wraps the
  real context and overrides only the resource accessors. The `Configuration`
  itself must come from `LocalConfiguration`, not `LocalContext.resources`,
  or lint flags it as not configuration-aware.
- **Location was on the critical path.** `analyse()` awaited a fix before
  publishing the result, so with no fix available the child watched a spinner
  for the full ten-second timeout with the planet already decided. Coordinates
  now attach afterwards.
- **A lint error caught a real crash.** Swapping `LocationManagerCompat` for
  the platform `LocationManager.getCurrentLocation` to silence a deprecation
  warning introduced an API-30 call on a minSdk-26 app — it would have thrown
  on Android 8 to 10. Reverted; the deprecation warning is the lesser evil.
- **Three journey rendering bugs** found by looking at frames on device:
  cloud bands spilling past the limb, a hard pie-wedge terminator, and
  perspective converging both bodies on screen centre while Earth flew off
  the edge at the end.
- **Tests:** 42 JVM unit tests covering the colour-to-planet mapping (including
  the circular-hue and specular-highlight traps, that Earth is never a source,
  and that a seed always yields the same planet), both timelines, the
  projection, the stability tracker, and the JSONL logbook including a torn
  trailing line. `PebbleRepository` takes a `File` rather than a `Context` so
  it tests without Robolectric.
- **Lint is clean** and the merged manifest holds exactly `CAMERA` and
  `ACCESS_COARSE_LOCATION`.

## Verification

```powershell
./gradlew :app:assembleDebug          # compiles
./gradlew :app:testDebugUnitTest      # pure-logic tests
./gradlew :app:lintDebug              # manifest, a11y, resource checks
./gradlew :app:installDebug           # onto the AVD or a USB device
```

Offline check: the **merged** manifest (`app/build/intermediates/merged_manifests/debug/AndroidManifest.xml`), not just the source one, must contain no `android.permission.INTERNET` — a dependency can inject it. `./gradlew :app:dependencies` guards the same property from the other side. This makes the Play Data-safety declaration a clean "no data collected", which is worth protecting deliberately.

End-to-end pass, run on both the AVD (`pebble_test`, webcam pointed at a pebble) and a physical device (real camera, real sensors, real GPS):
1. Grant camera, deny location — the whole flow must still complete.
2. Frame a reddish pebble → tap → confirm freeze frame, then Mars.
3. Frame a grey pebble twice → confirm the two results differ (random path).
4. Switch language during framing, during research, during the journey, and on the result screen — nothing restarts, everything relabels.
5. Toggle sound on; confirm a cue at each stage; confirm silence by default on a fresh install.
6. Run three pebbles, kill the app, reopen → all three in history with photo, date, time and (where granted) place.
7. Time the journey with a stopwatch — must land between 10 and 15 s.

---

## Open risks

- **The shutter sound cannot be silenced in some markets.** On Japanese- and Korean-market handsets the camera shutter tone is enforced at system level and an app cannot suppress it. Since the app promises "sound off by default", this will be visibly violated at the single most dramatic moment, on exactly the hardware most likely to be used here. The app should not claim silence it cannot deliver — Phase 2 decides whether to acknowledge it in the UI or soften the wording.
- **Emulator camera** — `webcam0` gives a real image to analyse; the synthetic `emulated` scene does not produce meaningful pebble colours. A physical device is the real test for Phases 2 and 5.
- **Asset curation** — CC0 sound cues need hand-picking for tone; the first pass may not sound right for a kids' app and may need a second round.
- **R8 and kotlinx.serialization** — a release build must be checked to round-trip the index file. A history that reads back empty only when minified is a miserable bug to find late.
- **Accessibility** — the Canvas scenes need `contentDescription`, the progress bar needs `progressSemantics`, phase changes need a polite live region, and matrix-green on black fails contrast at small sizes. Large-area flashes stay under 3 Hz; this is a genuine seizure risk in a children's app, not a checkbox.

## Resolved during implementation

### Phase 0 — build, toolchain, device

- **Version matrix — settled by building.** The planned AGP 8.13.2 did **not** work. Compose 1.12.1, `navigation-compose` 2.10.2 and `lifecycle` 2.11.0 each require **AGP ≥ 9.1 and `compileSdk 37`**, so the toolchain moved to AGP **9.4.1** / Gradle **9.8.0** / `compileSdk 37` / `targetSdk 36`. Keeping AGP 8.13.2 would have meant downgrading Compose, navigation and lifecycle together; not worth it on a project started today.
- **API 37 has no base platform.** `sdkmanager "platforms;android-37"` fails — API 37 ships only as minor versions (`android-37.0`, `37.1`, `37.2`). `platforms;android-37.0` is the one installed.
- **Kotlin under AGP 9.** AGP 9 enables built-in Kotlin and the new DSL, which makes applying `org.jetbrains.kotlin.android` a hard error (`not compatible with AGP's 9.0 new DSL`). Opting out via `android.builtInKotlin=false` was tried and abandoned; the build uses AGP's built-in Kotlin, keeping only the Compose and serialization compiler plugins.
- **Gradle floor.** Kotlin 2.4.20 requires Gradle ≥ 8.14.4 independently of AGP; 9.8.0 clears it.
- **`resourceConfigurations` is deprecated** in favour of `androidResources.localeFilters`.
- **`play-services-location` dropped** in favour of `LocationManagerCompat` — one fewer dependency and no GMS requirement.
- **The offline guarantee had already leaked, and is now enforced.** `androidx.camera:camera-view` pulls in `camera-video` → `media3` → `ACCESS_NETWORK_STATE`, which the manifest merger silently added. The app records no video, so `camera-video` is excluded; and the manifest now carries explicit `tools:node="remove"` entries for `INTERNET` and `ACCESS_NETWORK_STATE`, so no future dependency can reintroduce either without the build visibly fighting it. The merged manifest is down to exactly `CAMERA` and `ACCESS_COARSE_LOCATION`.
- **Coil 2 → Coil 3** (`io.coil-kt.coil3`), whose networking lives in a separate artifact we simply do not add. Coil 2 bundles OkHttp for no reason in an app that only ever decodes local files.
- **AVD.** No `pixel_7` profile exists in these command-line tools; `pebble_test` is built on `pixel_6` with the API-35 image, 2 GB RAM, GPU on and `hw.camera.back=webcam0`.

**Phase 0 verified on device:** APK installs and runs on `pebble_test`, merged manifest holds exactly `CAMERA` + `ACCESS_COARSE_LOCATION`, all three locales packaged.

### Phase 1 — shell, localisation, settings

- **`LocalResources` is in `androidx.compose.ui.platform`**, not `androidx.compose.ui.res`.
- **Edge-to-edge put the controls under the status bar, where it ate their taps.** `enableEdgeToEdge()` without inset handling left the language and sound buttons visually behind the system status bar and completely untappable — the screen looked right and simply did not respond. `TopControls` now applies `statusBarsPadding()` and screen content applies `navigationBarsPadding()`. Worth remembering for the camera screen, where the preview *should* be full-bleed but the controls must not be.
- **Verified on device:** EN → RU → JA relabels every string live with no Activity restart and no flicker; language and the sound setting both survive a `force-stop`; sound is off on first run; navigation to the logbook works and correctly hides its own icon.

**Still owed from Phase 1:** `Locale.getDefault()` is deliberately not changed by the in-composition override, so date formatting must take the locale explicitly once the logbook shows timestamps — see `AppLanguage.locale`.

### Post-release — the landing sequence

- **The Done button had never worked from the logbook.** `onFinished` called
  `popBackStack(Routes.RESULT, inclusive = false)`, but a journey started from
  a logbook entry has a stack of `[CAPTURE, HISTORY, HISTORY_DETAIL, JOURNEY]`
  with no RESULT on it, so the pop matched nothing and silently did nothing.
  A plain `popBackStack()` returns to whichever screen the journey was started
  from. The frame loop also used to call `onFinished()` itself the moment the
  timeline completed, so the button was only on screen for about a second even
  on the path where it did work; the landed scene now stays up and Done is the
  only way out of it.
- **The flight is twenty seconds, not twelve.** Atmospheric entry used to be
  the end of it. It now carries on: the fireball covers a cut from the
  starfield to a schematic map, which falls and closes in through Japan and
  Kanto to the streets of Koto. The old `LANDING` phase is split into
  `APPROACH`, `DESCENT` and `TOUCHDOWN`.
- **A linear zoom was wrong twice over.** The map scale is interpolated
  geometrically across a factor of about ninety, so a linear parameter spends
  almost the whole descent at the close end — Japan was visible for about half
  a second. Smoothstep on the parameter holds the wide shot. Separately, the
  *pan* had to be tied to the span rather than to the parameter: with a linear
  pan the scale collapses far faster than the camera travels, and Tokyo slid
  off the edge of the screen two thirds of the way down.
- **One set of coastlines cannot cover a ninety-fold zoom.** The outline of
  Honshu is unreadable blown up to 200km across. Three levels of detail now
  hand over as the scale drops: the islands of Japan, the coast of Kanto with
  Tokyo Bay cut into it as a single land polygon, then the city — block grid,
  loop line, radial roads and both rivers, all clipped to the land.
- **Verified on device** along both paths: Done from the post-research journey
  returns to the result screen, and Done from a logbook replay returns to that
  logbook entry. Skip still lands on the same final frame.

### Post-release — the title sequence

- **The splash is Compose, not the platform splash screen API.** That API
  gives you one centred icon on a flat colour, which cannot carry the solar
  system. The system splash still shows first on Android 12 and up and
  cannot be removed, so what the player actually sees is the launcher icon
  for a moment and then the sequence; `windowBackground` was already the
  same near-black, so there is no flash between them.
- **The planet photographs needed a circular mask as well as the luminance
  key.** Keying alone is what the share card and the banner script do, and
  it is enough for every body except the Sun, whose NASA frame is lit corner
  to corner: it came through as a bright square sitting on the starfield.
- **The discs are decoded subsampled.** Nine full-size frames on the way
  into the app is tens of megabytes and a visible stall. They decode at
  256px on a background dispatcher and are published one at a time, so the
  sky and the wordmark never wait for them.
- **The title column is deliberately narrower than the screen.** The
  pebble’s trail comes down the left-hand side, and a full-width subtitle
  ran straight across it.
- **Verified on device** in all three languages, that a tap skips without
  the tap reaching the camera screen underneath, and that back from the
  camera leaves the app rather than replaying the titles.

### Post-release — Planets around

- **The ephemeris is arithmetic, not data.** JPL’s fitted Keplerian elements
  for 1800–2050 - six numbers and six rates per body - plus Kepler’s
  equation and two coordinate transforms. No file to bundle, nothing to
  expire, nothing to fetch, and accurate to a few arcminutes across the
  whole span. A table of precomputed positions for ten years would have been
  larger, less accurate at the edges and would have run out.
- **Tested against the solar system rather than against a copied table.**
  The equinoxes and solstices pin the obliquity; the midday Sun on the
  Greenwich meridian pins sidereal time and the azimuth convention, which
  the declinations alone would not; Mercury and Venus never straying more
  than 29° and 48° from the Sun pins the geocentric vector; and the
  opposition cycle of each outer planet (Jupiter every 399 days, Saturn
  every 378) pins its mean longitude rate, which is the one error the other
  tests would have let through. Confirmed on device too: from Tokyo on the
  evening of 3 October 2026 it put Saturn 48° up in the south-east, which is
  where a planet at opposition the following day belongs, with Neptune five
  degrees off it - the two really are that close together in 2026.
- **Magnetic declination is not optional.** The rotation vector reports
  bearings from magnetic north; the sky is computed from true north. Up to
  twenty degrees apart depending on where you are, which is larger than
  every other error in the feature combined. `GeomagneticField` is in the
  platform and works offline.
- **The field of view has to be read off the lens.** Drawing the overlay at
  the wrong scale puts every planet at the right bearing and the wrong
  distance from the middle of the screen. The preview is pinned to four by
  three so the sensor’s long axis fills the height of the screen, and the
  focal length and physical sensor size give the angle that fills it.
- **Attitude is smoothed as a quaternion.** The radar smooths one angle the
  short way round, which does not generalise to three axes; interpolating
  the quaternion and renormalising is both correct and cheaper.
- **Waiting for a fix was the wrong default.** The first build asked for a
  single location and then subscribed to updates, so indoors it showed an
  empty sky for the full 25-second timeout. It now subscribes directly, and
  falls back to the place the last pebble was logged - which is in the
  logbook already, needs no permission prompt of its own, and is accurate to
  far better than a planet cares about.
- **The meteor falls through more sky than the camera can see** - fifty-odd
  degrees against a sixty-degree view - so it leaves the frame on the way
  down. Rather than cheat and pin it to the screen, which would stop it
  being augmented reality at all, an arrow says follow it down.
- **Verified on device** in all three languages, including the planet
  toolbar splitting above and below the horizon, focusing a single body, the
  guide arrow, and a meteor called down from Saturn and followed to the
  ground. The merged manifest still holds exactly CAMERA and the two
  location permissions: a planetarium that needs no network at all.

### Post-release — the three camera modes become one hunt

- **Planets around now hands over to Radar, not to Detection.** The three
  camera modes are a sequence - pick a world, go and find where its piece
  landed, photograph the stone - and the bottom button on each screen moves
  along it. Every mode also carries both mode buttons in the top bar, so a
  child who already has a stone in their hand can start at the end.
- **Pushed rather than swapped.** Going from the sky to the radar leaves the
  sky on the back stack, so back retraces the route; only Detection collapses
  it, because it is where the flow begins and ends. Verified that back out of
  Detection leaves the app rather than walking the chain in reverse.
- **The camera survives the handover** in both directions, which is the
  thing this app has got wrong before: each screen unbinds only the preview
  it bound itself, so the outgoing screen being disposed after the incoming
  one has bound cannot freeze it.

### Post-release — opening on the sky

- **The titles run half again as long**, 2.8 to 4.2 seconds, with every beat
  stretched rather than the final frame held: a longer hold would have read
  as the app being stuck rather than as a title sequence.
- **Planets around is the landing screen**, which moved two responsibilities
  onto it. It is now the screen that asks for the camera, not just for a
  location, and it asks for both in one dialog sequence; and its backdrop
  reads the camera permission as state rather than once, so the view appears
  when the answer arrives instead of the next time the screen is opened.
- **Popping back to Detection stopped being safe.** It used to be the root of
  the graph, so `popBackStack(CAPTURE)` always found it. Coming down the
  chain from the sky it is not on the stack at all, and a pop that matches
  nothing silently does nothing - the same shape as the Done button bug. The
  helper pops when Detection is behind you and pushes when it is not.
- **The top bar gained a camera button** so the last stage is reachable from
  the first, which took the row to six buttons; the touch target came down
  from 56dp to 52dp so that still fits a 360dp screen without clipping.
- **Verified from a clean install**: camera then location prompts, the titles,
  the landing on the sky, the chain through to Detection, the toolbar jumping
  straight there, and back walking the chain in reverse and then out of the
  app.

### Post-release — Russian cases, and a real Earth

- **Russian inflects and the app did not.** "Улетаем с %s" with the plain name
  gives "с Марс", which is wrong; it wants the genitive, "с Марса", and the
  ending varies by word - Венеры, Юпитера, Солнца. No format string can
  derive that, so each body has a second name resource and the languages
  that do not inflect repeat the first. The result screen had the same bug
  and is the more visible one: its caption reads "Этот камешек прилетел с"
  with the planet underneath, which is one sentence however it is laid out.
- **Earth is now a photograph, of the right hemisphere.** The bundled
  full-disk Earths - and every one in the NASA library - show the Americas or
  Africa, and the journey lands in Tokyo. DSCOVR sits at L1 and images the
  whole sunlit disc every couple of hours, so one frame per day is centred
  on the Pacific; the frame nearest 135 east is bundled, cropped to its limb
  and lifted a little, since EPIC natural colour is faithful rather than
  flattering.
- **It needed a different alpha.** The other bodies are keyed off luminance,
  which melts a lit limb into the starfield. Doing that to this frame put
  stars through the Pacific: the darkest tenth of the disc is dark enough to
  come out half transparent. A frame already cropped to its disc wants a
  plain circular mask instead.
- **A photograph exposed a transition that was getting away with it.** The
  space leg and the schematic map used to be drawn over each other at full
  strength during the handover. With a small drawn Earth that read as a
  dissolve; with a photographic one at nine hundred pixels it read as a bug,
  a schematic Honshu the size of the Pacific sliding across a picture of the
  real one. The two now cross-dissolve inside a saved layer, the swap was
  moved later and shortened to the half second when Earth has nearly filled
  the frame, and a single wash of fire across the whole screen covers the
  join.
- **Then the source planet followed.** Leaving one world as a palette of
  bands next to a photograph of another did not hold up, so both ends of the
  flight are photographs now and the brief’s "schematic journey" survives
  only in the descent map. The schematic planet is still the fallback that
  stands in while a decode is in flight.
- **Which exposed that the frames do not fill their own pictures.** Jupiter
  sits in a good deal of empty space, so a body was being drawn smaller than
  the radius asked for - invisible until a rim light was positioned against
  that radius and appeared as a hoop floating off the limb. Discs are now
  trimmed to their content before scaling, which also made the title
  sequence sharper, where the same under-filling had gone unnoticed.

### Post-release — the sky decides the next stone

- **A meteor called down from a planet claims the next pebble.** The child
  has just been told, with a fireball and a bang, where a piece of Saturn
  landed; having the colour analysis then say Jupiter makes the sky mode a
  lie. The claim outranks the colour for exactly one pebble and is then
  forgotten.
- **The dominant colour is still measured and still recorded.** Only the
  planet is overridden, so the logbook entry stays honest about what the
  stone actually looked like.
- **The hint has to come before the capture, not after.** A banner on the
  Detection screen names the world and carries an opt-out, because finding
  out that the answer had been decided in advance only once the answer
  appears would read as the app cheating. Opting out puts the colour back
  in charge.
- **Held in memory, not in saved state.** It is a thing you just did rather
  than a setting; surviving a process death or a second capture would make
  it a mode a child has to remember they are in.
- **Verified on device**, all four paths: the claim applied (Saturn over the
  colour answer of Jupiter), the claim spent after one pebble, a second
  claim named correctly, and the opt-out handing the decision back to the
  colour.

### Post-release — the README caught up with the app

- **Every screenshot regenerated from the current build**, in English, and
  the walkthrough reordered to run the way the app is played: the titles,
  then Planets around where it lands, then Radar, then the camera and the
  pebble. The planets section had been numbered 5b because it was written
  last, and Radar had a section of its own at the back of the document -
  between them they described a game nobody plays in that order.
- **The meteor is a GIF**, because a still cannot show a thing that falls,
  and two new stills cover what the journey GIF passes through too quickly
  to see: the cruise, where both worlds are photographs, and the descent
  over Japan.
- **The capture scripts find buttons by accessibility label.** The toolbar
  has gained buttons twice and changed its touch target once, and each time
  the hard-coded taps silently started pressing whatever had moved into
  their place; one run had been quietly photographing the wrong screens.
- **GIFs now play at the rate they were captured.** screencap manages only
  a few frames a second and how few depends on the screen, so a fixed frame
  time made a slow capture play back comically fast.
- **Which route is faster depends on the screen, and I measured the wrong
  one first.** The raw framebuffer beats `screencap -p` on the camera
  screens, where the encoder has a photograph to chew through, and loses on
  the drawn ones, which compress to almost nothing: the journey GIF dropped
  from 38 frames to 22 before that was spotted. Each script now uses the
  one that suits what it photographs.
