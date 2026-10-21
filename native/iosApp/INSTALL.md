# Getting Pluton onto an iPhone from Linux

You do not need a Mac to *build* the IPA (GitHub Actions provides the macOS runner) or to *sign and install* it
(current Linux tools do that). You cannot compile Xcode/SwiftUI or link Kotlin/Native iOS targets **on** Linux itself:
those need Apple's SDK and linker, which only run on macOS. The split below is the practical one.

## 1. Build the unsigned IPA

The workflow `.github/workflows/ios.yml` builds the Kotlin framework and the SwiftUI app on a macOS runner and
uploads `pluton-ios-unsigned` (an unsigned `.ipa`). Push the repo, then:

1. GitHub → **Actions** → **iOS unsigned IPA** → **Run workflow**.
2. Download the `pluton-ios-unsigned` artifact and unzip it (you get `Pluton-unsigned.ipa`).

The first runs will be slow (Kotlin/Native download + compile). If the Swift code has errors, the job log shows
them; that is the fastest feedback loop without a Mac.

Note on minutes: GitHub's free plan includes 2,000 minutes/month, but macOS runners count **10×**, so roughly 200
macOS minutes. One build should fit comfortably; use `workflow_dispatch` rather than building on every push while
iterating.

## 2. Install it on the phone

Check **Settings → General → About → iOS Version** first.

### A. iOS 14.0 beta 2 – 16.6.1, 16.7 RC, or 17.0: TrollStore (permanent, no computer after install)

TrollStore installs unsigned IPAs directly on-device and they never expire. It is not a jailbreak.

1. Install TrollStore using the method for the version (for **16.7 RC / 17.0**, use **TrollRestore**, which has a
   Linux build; it needs Python 3, USB, Find My disabled).
2. Get the IPA onto the phone (AirDrop won't take `pluton://`; use a local web server/`python3 -m http.server`, or
   copy via `ifuse`), then **open the IPA in TrollStore** (share sheet → TrollStore, or TrollStore's install button).
3. Done. No weekly re-signing.

Official sources: <https://github.com/opa334/TrollStore> and <https://ios.cfw.guide/installing-trollstore/>.

**17.0.1 and later (and 16.7.x except the RC) can never use TrollStore** — use the next option.

**iOS 26 and iOS 27:** TrollStore does not support them (and the project says it never will without a new
CoreTrust bug). Use iloader/SideStore or TestFlight below. Pluton's deployment target is iOS 17, so a build made with
an older Xcode SDK runs on iOS 27; you don't need the iOS 27 SDK to sideload it.

### B. Any iOS 15+ (including iOS 26/27): iloader on Linux (free Apple ID, 7-day certificate)

**iloader** (Linux AppImage; v2.3.10 at the time of writing) signs with a free Apple ID over USB and installs the IPA.
It is also the official SideStore bootstrap.

1. Install `usbmuxd` from your distro and get iloader from <https://iloader.app> or
   <https://github.com/nab138/iloader/releases> (use the official source; AUR/COPR packages are community builds).
2. Connect the iPhone, trust the computer, unlock the phone.
3. Log in with a free Apple ID (2FA via trusted device works; SMS 2FA was broken in some 2.3.x releases). A burner
   Apple ID is safer: signing tools use an anisette server to authenticate, and shared servers have caused account
   locks.
4. Import `Pluton-unsigned.ipa` and install. Then on the phone trust the developer under
   **Settings → General → VPN & Device Management**.

Limits of a free Apple ID (Apple's, not Pluton's): the app stops launching after **7 days**, at most **3 apps per
device** including SideStore, and 10 App IDs per week.

To refresh without a computer, let iloader install **SideStore** too; SideStore renews its own and your apps'
certificates on-device before they expire (needs LocalDevVPN and Wi-Fi). More at <https://docs.sidestore.io>.

### C. Paid Apple Developer Program ($99/year): TestFlight, no computer needed

CI can produce a properly signed build if you add signing secrets (certificate `.p12`, provisioning profile, team
id) and upload it to TestFlight with App Store Connect. Installing from the phone's TestFlight app needs no cable.
This is the only route that avoids both a local signer and the 7-day expiry.

## What not to use

Random "signing services" and shared enterprise certificates are a good way to lose an Apple ID. Use your own free
Apple ID with iloader/SideStore, or a paid account with TestFlight.

## Current iOS status

The Swift sources have **never been compiled** (no Mac was available while writing them). The workflow exists to
close that gap: its first runs may need interop fixes. Until a green run produces `Pluton-unsigned.ipa`, there is no
installable app to sign.
