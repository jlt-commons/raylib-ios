# Upstream findings

Things this project learned that belong to other repositories. Each has a
concrete casualty here, and each was measured rather than reasoned about.

Nothing below has been filed. `~/.claude/rules/issue-filing-freshness.md`
applies before anything is: fetch upstream immediately before filing, re-verify
every claim against the fetched ref rather than a local tree, search the tracker
with several differently shaped queries, and re-run that search right before
submitting.

## jolt: `sa-os-family` still calls a native iOS build Linux

**Status:** unfiled. One line, with a concrete casualty and no collision risk.

PR #797 (`d9646c83`, "a portable-bytecode build is not necessarily Linux")
fixed the bytecode half of this. `sa-os-family` derives the OS by substring
matching the Chez machine tag, which cannot work for `pb` / `pb64l` / `tpb64l`
because a bytecode tag deliberately names no OS, so the else branch called
every bytecode build Linux. The fix probes the filesystem when the tag has
nothing to say.

The native half is untouched:

```scheme
(define (sa-os-family)
  (let ((m (sa-host-tag)))
    (cond ((or (sa-tag-contains? m "osx") (sa-tag-contains? m "macos")) 'macos)
          ((or (sa-tag-contains? m "nt")  (sa-tag-contains? m "windows")) 'windows)
          ((sa-tag-contains? m "pb") (sa-probed-os-family))
          (else 'linux))))
```

`tarm64ios` is a real Chez machine type, documented in mainline `BUILDING`
line 568 as `-m=tarm64ios`, and it contains none of `osx`, `macos`, `nt` or
`pb`. So it reaches `else` and answers `'linux`, on a Darwin system.

That is not only `os.name`. `sa-os-family`'s own doc comment lists the callers:
`SIGCHLD` (20 on Darwin, 17 on Linux), `EAGAIN` (35 vs 11) and `O_NONBLOCK` in
`process.ss`, `LC_TIME` in `tz-primitives.ss`, the `struct stat` offsets in
`nio-file.ss`, the chmod and entropy fallbacks, and the link libraries in
`build.ss`. A native iOS build takes the Linux value for every one.

**The casualty:** `jolt.nrepl` computes `sock-cloexec` as `0x80000` unless the
OS is macOS or Windows, so it hands Darwin's `socket()` the Linux-only
`SOCK_CLOEXEC` and gets -1. That is why the iOS simulator could not start an
nREPL, and why the notebooks this project follows had to write their own
Darwin socket by hand. Our device builds are `tpb64l`, so #797 already covers
them and `raylib.live` needs no workaround. The simulator, which is native
`tarm64ios`, does not.

**Proposed fix**, one line:

```scheme
((or (sa-tag-contains? m "osx") (sa-tag-contains? m "macos")
     (sa-tag-contains? m "ios")) 'macos)
```

**Why the substring is safe.** No Chez machine tag contains `ios`, checked
against every tag named in `configure` and `BUILDING` in a v10.4.1 checkout.
And the ordering makes even a hypothetical collision harmless: a tag that
contained `ios` by accident would have to be an `osx` tag, which wants `'macos`
regardless. The branch it joins is the one it would reach anyway.

## jolt: `os.name` says "Mac OS X" on an iPhone

**Status:** unfiled, and arguably not a bug. Recorded because it is surprising.

#797's own commit message flags this and leaves it open: "Darwin resolves to
`'macos` because the contract is three symbols and none of them is Darwin. That
is right for every caller above, since those constants are Darwin's either way,
but it does mean a Darwin bytecode build answers 'Mac OS X' to os.name. Whether
the contract should grow a fourth symbol is a larger change than this fix."

Confirmed on an iPhone 17 Pro, iOS 26.6.1, `tpb64l`, over the app's own nREPL:

```clojure
[(System/getProperty "os.name") (System/getProperty "os.arch")]
;; => ["Mac OS X" "tpb64l"]
```

`os.arch` reporting the machine type is the only remaining tell for which
machine an nREPL is attached to, and it is worth knowing that `os.name` is no
longer one.

Every constant `sa-os-family` selects is correct here, because iOS is Darwin.
Only the user-visible name is wrong, so this is a naming question rather than a
correctness one, and it is not obvious it should change.

**If it ever should**, there is a clean discriminator, measured on the device:

| path | iOS 26.6.1 | macOS |
|---|---|---|
| `/System/Library/CoreServices/SystemVersion.plist` | present | present |
| `/System/Library/Frameworks/UIKit.framework` | present | absent |
| `/System/Library/Frameworks/AppKit.framework` | absent | present |

So UIKit present and AppKit absent means iOS. The plist that `sa-probed-os-family`
already keys on exists on both, which is exactly why the probe answers `'macos`
there and why it is right to.

Two caveats before anyone builds on that. Mac Catalyst ships both frameworks,
so the test has to be UIKit AND NOT AppKit, and even then a Catalyst process is
arguably macOS. And this would want to sit beside `sa-os-family` rather than
inside it, so the three-symbol contract and every constant it feeds stay
untouched.

## raylib, from the notebooks

Carried over from `statonjr/glimmer-ios-demo`, which found them. Listed so they
are not rediscovered here.

- **The SDL platform never binds SDL's drawable framebuffer.** REFUTED on
  hardware, 2026-09-03, before it was filed. See below.
- **`CUSTOMIZE_BUILD` reads `#define SUPPORT_X 0` as ON.** The parser keys on
  the `#define` and ignores the value, so `SUPPORT_CUSTOM_FRAME_CONTROL` and
  `SUPPORT_BUSY_WAIT_LOOP` flip on for anyone who customises anything.
- **`GetTouchPointId` truncates `SDL_FingerID`**, a pointer-shaped 64-bit value
  on iOS, into an `int`. Every finger reports the same id.
- **`FLAG_WINDOW_HIGHDPI` "is not supported on PLATFORM_DESKTOP_SDL"**, yet it
  still asks SDL for a high-DPI drawable and then sets `render = screen`.

## raylib: `GetFPS` is stateful and nothing says so

**Status:** unfiled. A documentation point, not a defect.

`GetFPS` advances a 30-slot ring by one position per call and returns
`1/sum-of-ring`, which is a frame rate only once all 30 slots are full. That
requires calling it every frame, which is what `DrawFPS` does and the only way
raylib itself ever calls it. `raylib.h` says only `// Get current FPS`.

Called once per 300 frames from this project's host summary it returned 1757,
then 887, 590, 441 and on down to 98 across ninety seconds, while
`GetFrameTime` held at 17.02 ms. The model `1/(n * frame-time/30)` fits all
eighteen readings to within 0.76% with no free parameters. See the README.

## Refuted: raylib does NOT draw into framebuffer 0 on iOS

**Status: not filed, because it is not true.** Recorded because this project
carried a workaround for it, and because the refutation cost less than the
report would have.

The notebooks report that raylib's SDL platform never binds SDL's drawable
framebuffer, so it draws into framebuffer 0, which a device rejects. The code
reading is correct: `rcore_desktop_sdl.c` contains no `glBindFramebuffer`, no
`glBindRenderbuffer` and no use of the uikit union, at 6.0 and at master
`9b2efc45` alike. Its one `SDL_GetWindowWMInfo` call is `GetWindowHandle`,
fetching a native window pointer, and it reads the `cocoa` union member under
`__APPLE__` rather than `uikit`.

The conclusion drawn from that reading does not survive contact with a device.

Measured on an iPhone 17 Pro, iOS 26.6.1, SDL 2.32.10, over the app's own
nREPL, by reading `GL_FRAMEBUFFER_BINDING` and `GL_RENDERBUFFER_BINDING` in the
frame, immediately before `EndDrawing` calls `SDL_GL_SwapWindow`:

| build | host binding | at swap: framebuffer | at swap: renderbuffer |
|---|---|---|---|
| raylib master `9b2efc45` | never binds | 1 | 1 |
| raylib master `9b2efc45` | binds every frame | 1 | 1 |
| raylib 6.0 | never binds | 1 | 1 |

SDL reports its drawable as `{:framebuffer 1, :colorbuffer 1}`, so every row is
already correct and this project's per-frame binding changes nothing. Both of
`README-ios`'s requirements are satisfied without it: SDL's own swap path leaves
the drawable bound, and the binding persists frame to frame.

One real difference between versions turned up on the way. Immediately after
`InitWindow`, before any frame runs, master leaves framebuffer 1 bound and 6.0
leaves 0. By swap time both read 1, so at most this costs 6.0 a single startup
frame rendered nowhere, which is invisible in practice.

**Why the notebooks concluded otherwise, most likely.** They were debugging a
black screen with four independent causes at once, one of which was that the
iOS simulator has not displayed OpenGL ES since 17.5 and never would have
regardless. The binding went in among several changes, was harmless, and the
device runs that followed all carried it. Nothing isolated it.

**The lesson worth keeping**, since it nearly produced a wrong bug report on
someone else's project: a correct reading of source code is a hypothesis about
runtime behaviour, not a measurement of it. The tell was cheap and was there to
be taken all along, which is that the workaround could simply be turned off.

`raylib.host` keeps `bind-drawable?`, defaulting on and settable from launch
with `RAYLIB_BIND_DRAWABLE=0`, plus `pre-swap` and `initial-framebuffer`. The
binding is a no-op today and costs two GL calls a frame. It stays because it is
what `README-ios` actually requires, so a future SDL or raylib that stops
leaving the drawable bound would break silently without it, and the probes are
how that gets re-checked in one command.
