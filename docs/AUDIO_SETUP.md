# Audio Setup (Voicemeeter / Virtual Audio Cable)

This guide shows how to send your PC's audio to Dynamic Skies so the sky reacts to music, **while you still hear everything normally**.

> **Try this first:** set **Audio Source** to **System Audio** (`/dynamicskies` → Audio → Source).
> It listens to whatever Windows is playing, with nothing to install. You only need the setups
> below if System Audio doesn't work for you, or if you want the sky to react to *only* your music
> (not Discord, game sounds, etc).

## Which setup should I use?

| Setup | Installs | The sky reacts to | Difficulty |
|---|---|---|---|
| [1. VB-CABLE only](#setup-1-vb-cable-only) | VB-CABLE | Everything, or just the apps you pick | Easy |
| [2. Voicemeeter only](#setup-2-voicemeeter-only) | Voicemeeter | Everything on your PC | Medium |
| [3. Voicemeeter + VB-CABLE](#setup-3-voicemeeter--vb-cable-music-only) | Both | Only your music app, with its own volume | Medium |

Downloads (free / donationware, from VB-Audio):
- VB-CABLE: https://vb-audio.com/Cable/
- Voicemeeter (Standard, Banana or Potato all work): https://vb-audio.com/Voicemeeter/

Run each installer **as administrator** and **restart your PC** afterwards. The new devices often don't show up until you do.

---

## How it works

A virtual cable is a fake speaker and a fake microphone joined together:

- **CABLE Input** is the fake *speaker*. Sound played into it comes out of...
- **CABLE Output**, the fake *microphone*. The mod listens to this.

The catch: once your sound goes into the cable, it no longer goes to your headphones. Every setup below has one step that sends it back to you.

---

## Setup 1: VB-CABLE only

### 1. Send audio into the cable

**All PC audio:**
1. Right-click the speaker icon in the taskbar → **Sound settings**.
2. Under **Output**, choose **CABLE Input (VB-Audio Virtual Cable)**.

**Only some apps (e.g. just Spotify):** leave your normal output as it is, and instead:
1. **Sound settings** → **App volume and device preferences** (Windows 10) or **Volume mixer** (Windows 11).
2. Start the app and play something so it shows in the list.
3. Set that app's **Output** to **CABLE Input**.

### 2. Hear it yourself
1. Press `Win + R`, type `mmsys.cpl`, press Enter.
2. Open the **Recording** tab.
3. Double-click **CABLE Output** → **Listen** tab.
4. Tick **Listen to this device**.
5. Under **Playback through this device**, choose your **headphones/speakers**. Do *not* pick "Default Playback Device" if the cable is your default, or you get a loop.
6. Click **OK**.

You should now hear your music again. (It may be a few milliseconds late. You won't notice for music, but don't send game audio this way if you're sensitive to delay.)

### 3. Point the mod at it
- **Audio Source:** `Input Device`
- **Audio Device:** `CABLE Output`

---

## Setup 2: Voicemeeter only

Voicemeeter is a mixer: everything goes into it, and it sends the sound both to your headphones and to a virtual output the mod can listen to.

### 1. Send PC audio into Voicemeeter
1. Open Voicemeeter (keep it running; if it's closed you hear nothing).
2. **Sound settings** → **Output** → choose **Voicemeeter Input (VB-Audio Voicemeeter VAIO)**.

### 2. Hear it yourself
1. In Voicemeeter, top right, click **A1** (under *Hardware Out*).
2. Pick your headphones/speakers. Prefer the **WDM** entry; use **MME** if WDM crackles.

### 3. Send it to the mod as well
On the **Voicemeeter VAIO** strip (the *Virtual Inputs* column), turn on **both**:
- **A1**: you hear it
- **B1**: it goes to the virtual output **Voicemeeter Output** (called *Voicemeeter Out B1* in Banana/Potato)

### 4. Point the mod at it
- **Audio Source:** `Input Device`
- **Audio Device:** `Voicemeeter Out`

**Tip:** your microphone is on *Hardware Input 1*. Keep **B1** off on that strip, or the sky reacts to your voice too.

---

## Setup 3: Voicemeeter + VB-CABLE (music only)

Use this when you want the sky to follow **only** your music, and to turn your music up or down without touching game or Discord volume.

Only the music app goes through Voicemeeter. Voicemeeter sends it two places: your headphones (**A1**) and the cable (**A2**), which the mod listens to. The game keeps playing straight to your headphones as normal.

```
Music app ──► Voicemeeter Input ──┬─ A1 ──► your headphones (you hear it)
                                  └─ A2 ──► CABLE Input ──► CABLE Output ──► the mod
Game / Discord ─────────────────────────► your headphones (unchanged)
```

### 1. Keep Windows on your headphones
**Sound settings** → **Output**: leave it on your normal **headphones/speakers**. Minecraft, Discord and everything else keep using this.

### 2. Send only the music app into Voicemeeter
1. Open Voicemeeter (keep it running while you listen).
2. Start your music app and play something.
3. **Sound settings** → **App volume and device preferences** (Windows 10) or **Volume mixer** (Windows 11).
4. Set the music app's **Output** to **Voicemeeter Input (VB-Audio Voicemeeter VAIO)**.

### 3. Set Voicemeeter's outputs
Top right, under *Hardware Out*:
1. Click **A1** → pick your **headphones/speakers** (WDM preferred, MME if it crackles).
2. Click **A2** → pick **CABLE Input (VB-Audio Virtual Cable)** (WDM preferred).

### 4. Route the music to both
On the **Voicemeeter VAIO** strip (the *Virtual Inputs* column), turn on **A1** and **A2**. Turn **B1** off; it isn't needed.

### 5. Point the mod at the cable
- **Audio Source:** `Input Device`
- **Audio Device:** `CABLE Output`

### Turning the music down
- To make the music quieter **for you only**, lower the **A1** fader (bottom right, *Master Section*). The sky keeps reacting at the same strength, and game volume doesn't change.
- The fader on the **Voicemeeter VAIO** strip lowers the music for **both** you and the mod. If you use that one, you may need to raise **Sensitivity** in the mod.

---

## Mod settings

Open `/dynamicskies` → **Audio**.

| Setting | What to set |
|---|---|
| **Audio Source** | `Input Device` for the setups above (`System Audio` needs no setup) |
| **Audio Device** | Part of the device name, e.g. `CABLE Output` or `Voicemeeter Out`. Leave empty to let the mod pick a loopback device itself |
| **List Audio Devices** | Prints every recording device the mod can see in chat. Copy part of a name from here |
| **Restart Audio** | Press after changing anything in Windows or Voicemeeter |
| **Sensitivity** | Raise if the sky barely moves with quiet music |
| **Reactivity** | How strongly the sky reacts overall |

---

## Troubleshooting

**The sky doesn't react**
- Press **List Audio Devices**. If your cable isn't listed, restart your PC (needed after installing).
- Check the name in **Audio Device** matches part of a name in that list, then press **Restart Audio**.
- In `mmsys.cpl` → **Recording**, play music and watch the green bar next to CABLE Output / Voicemeeter Output. No bar means no audio is reaching it, so recheck the routing steps.
- Raise **Sensitivity**.

**I can't hear anything**
- Setup 1: check **Listen to this device** is ticked and points at your real headphones.
- Setups 2 and 3: Voicemeeter must be open, **A1** must be set to your headphones, and **A1** must be lit on the strips you want to hear.
- Setup 3: if the game went silent, Windows' main output was probably switched to Voicemeeter. Set it back to your headphones and only route the music app to Voicemeeter.

**Echo or a loud feedback squeal**
- Something is playing back into itself. Make sure "Listen to this device" doesn't play through the cable or through Voicemeeter Input, and that no strip sends B1 into a device that comes back into Voicemeeter.

**Crackling or robotic sound in Voicemeeter**
- Switch **A1** from WDM to MME, or raise the buffer in **Menu → System Settings / Options**.
- Make sure every device uses the same sample rate (`mmsys.cpl` → device → **Properties → Advanced**, e.g. **48000 Hz, 16 bit** everywhere).

**"No recording device matching ..." in chat**
- The text in **Audio Device** doesn't match any device. Use a shorter piece of the name (e.g. `CABLE` instead of the full name).

**The sky reacts to my voice / Discord**
- Setup 2: turn **B1** off on the mic strip. For music only, use [Setup 3](#setup-3-voicemeeter--vb-cable-music-only).
