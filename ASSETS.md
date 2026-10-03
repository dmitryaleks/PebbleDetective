# Bundled assets

Every third-party file shipped inside the APK, with its source and licence.
Regenerate with `python tools/fetch_assets.py`.


## Planet photographs

Source: [NASA Image and Video Library](https://images.nasa.gov/). NASA media are generally **not copyrighted** and may be reused; NASA does not endorse this app. See the [NASA media usage guidelines](https://www.nasa.gov/nasa-brand-center/images-and-media/).


| Body | File | NASA ID | Picture | Size |
|---|---|---|---|---|
| Sun | `app/src/main/assets/planets/sun.jpg` | [PIA03149](https://images.nasa.gov/details/PIA03149) | sun full disk | 1280x1239, 151KB |
| Mercury | `app/src/main/assets/planets/mercury.jpg` | [PIA15160](https://images.nasa.gov/details/PIA15160) | Mercury MESSENGER global mosaic | 1280x1280, 273KB |
| Venus | `app/src/main/assets/planets/venus.jpg` | [PIA00271](https://images.nasa.gov/details/PIA00271) | Venus Magellan global view | 1280x1280, 208KB |
| Earth | `app/src/main/assets/planets/earth.jpg` | [PIA18033](https://images.nasa.gov/details/PIA18033) | Earth full disk | 1280x1280, 239KB |
| Mars | `app/src/main/assets/planets/mars.jpg` | [PIA00407](https://images.nasa.gov/details/PIA00407) | Mars Viking global mosaic | 1280x1280, 181KB |
| Jupiter | `app/src/main/assets/planets/jupiter.jpg` | [PIA22946](https://images.nasa.gov/details/PIA22946) | Jupiter Juno | 1280x1280, 90KB |
| Saturn | `app/src/main/assets/planets/saturn.jpg` | [PIA11141](https://images.nasa.gov/details/PIA11141) | Saturn Cassini | 1280x619, 28KB |
| Uranus | `app/src/main/assets/planets/uranus.jpg` | [PIA18182](https://images.nasa.gov/details/PIA18182) | Uranus Voyager 2 | 1280x1280, 47KB |
| Neptune | `app/src/main/assets/planets/neptune.jpg` | [PIA01492](https://images.nasa.gov/details/PIA01492) | Neptune Voyager 2 | 1280x1278, 92KB |

## Sound cues

Source: [60 CC0 Sci-Fi SFX](https://opengameart.org/content/60-cc0-sci-fi-sfx) by rubberduck, released under **CC0 1.0** (public domain dedication). No attribution is required; it is recorded here anyway.


| Cue | File | Original | Role |
|---|---|---|---|
| `cue_ui_tap` | `app/src/main/res/raw/cue_ui_tap.ogg` | `sfx_09a.ogg` | 0.13s tick - button presses |
| `cue_reticle_lock` | `app/src/main/res/raw/cue_reticle_lock.ogg` | `sfx_20a.ogg` | 0.21s blip - reticle snaps onto the pebble |
| `cue_shutter` | `app/src/main/res/raw/cue_shutter.ogg` | `sfx_07a.ogg` | 0.22s snap - target captured |
| `cue_popup_open` | `app/src/main/res/raw/cue_popup_open.ogg` | `sfx_10a.ogg` | 0.30s rise - Deep Research prompt appears |
| `cue_satellite_ping` | `app/src/main/res/raw/cue_satellite_ping.ogg` | `sfx_22a.ogg` | 0.33s beep - repeated while acquiring signal |
| `cue_signal_acquired` | `app/src/main/res/raw/cue_signal_acquired.ogg` | `sfx_13a.ogg` | 0.62s confirm - signal locked |
| `cue_progress_complete` | `app/src/main/res/raw/cue_progress_complete.ogg` | `sfx_01a.ogg` | 0.69s success - research finished |
| `cue_matrix_hum` | `app/src/main/res/raw/cue_matrix_hum.ogg` | `sfx_11c.ogg` | 8.9s bed - looped under the thinking sequence |
| `cue_launch_whoosh` | `app/src/main/res/raw/cue_launch_whoosh.ogg` | `sfx_06.ogg` | 2.2s whoosh - pebble leaves the planet |
| `cue_space_drone` | `app/src/main/res/raw/cue_space_drone.ogg` | `sfx_11d.ogg` | 8.7s bed - looped during the cruise |
| `cue_entry_rumble` | `app/src/main/res/raw/cue_entry_rumble.ogg` | `sfx_18a.ogg` | 3.4s rumble - atmospheric entry |
| `cue_arrival_chime` | `app/src/main/res/raw/cue_arrival_chime.ogg` | `sfx_16a.ogg` | 2.6s chime - landed on Earth |

Total audio: 1439KB

