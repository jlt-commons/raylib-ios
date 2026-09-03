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

- **The SDL platform never binds SDL's drawable framebuffer.** iOS has no
  framebuffer 0. SDL's `README-ios` requires `uikit.framebuffer` bound while
  rendering and `uikit.colorbuffer` at swap, both from `SDL_SysWMinfo`.
  `rcore_desktop_sdl.c` draws into 0, which the simulator silently accepts and
  a device rejects. One `SDL_GetWindowWMInfo` after `SDL_GL_CreateContext` and
  a bind per frame is the whole fix.
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
