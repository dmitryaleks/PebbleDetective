# PebbleDetective — Implementation Plan

## Context

`PROJECT.md` describes a kids' Android app: a child points the phone at a pebble, taps to mark it, the app takes a "target captured" snapshot, runs a theatrical fake "Deep Research", announces which planet the pebble came from (derived from its dominant colour), and plays a 10–15 s schematic 3D animation of the pebble flying from that planet to Earth. Three languages switchable at any moment, sound effects per stage (off by default), and a persistent on-disk log of every pebble with date, time, location and photo.

Today the repository is a bare Kotlin/JVM Gradle scaffold — one `src/main/kotlin/Main.kt` hello-world, no Android plugin, no manifest, no `local.properties`. **Everything is new work.** The goal of this plan is a buildable, runnable Android app delivered in phases, each of which ends with something you can see on a device.

The whole experience is **offline by design**: the "Deep Research" is theatre, so the app declares no `INTERNET` permission at all. Nothing a child photographs ever leaves the phone. This is a deliberate, checkable property, not an afterthought.

### Decisions you confirmed
| Question | Decision |
|---|---|
| Camera | **Rear camera only** (`PROJECT.md` says "frontal"; rear is the usable one for a pebble and you chose it — no flip control) |
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
