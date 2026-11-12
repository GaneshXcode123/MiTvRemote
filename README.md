# Mi TV Remote

A phone remote for Mi TV / Xiaomi Android TV, using the same Wi-Fi protocol as Google's own remote app.

**Features:** auto-find TV · 6-character pairing · D-pad (hold to repeat) · OK / Back / Home / Menu · Power · Mute · Volume (also with your phone's volume buttons) · Netflix & Prime Video buttons like the real remote · Play/Pause/Rewind/FF/Next/Prev · Settings / Input / Info / Guide / CH± · Touchpad swipe mode · live Keyboard typing · Hold-to-talk voice (phone mic → TV Assistant) · App launcher + open any app/link.

## Get the APK (no PC tools needed)

1. Create a free account at github.com and make a **new repository** (any name, Public or Private).
2. Upload **everything inside this folder** to the repository (Add file → Upload files). Make sure the hidden
   `.github/workflows/build-apk.yml` file is included. If it isn't uploaded, use Add file → Create new file,
   type the name `.github/workflows/build-apk.yml` and paste that file's contents.
3. Open the **Actions** tab → "Build APK" → the run starts by itself after the upload (or press *Run workflow*).
   It takes about 5–8 minutes.
4. When it turns green, open the repository's **Releases** (right side) → *Mi TV Remote (latest build)* →
   download `MiTVRemote.apk` on your phone and install it (allow "Install unknown apps" when asked).

You can also download the APK from the finished run (Artifacts → MiTVRemote-apk).

## First use

1. TV on, phone on the same Wi-Fi (not a guest network, VPN off).
2. Open the app → tap your TV → type the 6-character code shown on the TV → done.
3. Next time it connects automatically.

## Notes

- Power turns the TV off/on only if the TV keeps its network connection in standby. Many Mi TVs don't, so keep the physical remote for switching on.
- If it can't connect, use **Re-pair** next to the TV, or enter the TV's IP address manually.
- Build locally: open the folder in Android Studio and press Run.
