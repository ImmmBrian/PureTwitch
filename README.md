<div align="center">

<h1>PureTV</h1>

**Twitch on Windows, without the ads.**

PureTV strips ads out of the stream on your own PC before the player sees them.
You sign in with your own Twitch account, and nothing you watch gets logged anywhere.

<br>

[![Download for Windows](https://img.shields.io/badge/Windows-Download-0078D4?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/ImmmBrian/PureTwitch/releases/latest)

<br>

<img src="docs/images/champions-player.png" alt="PureTV playing a stream with ads blocked and chat open" width="900">

</div>

<br>

## Install

1. Open the [latest release](https://github.com/ImmmBrian/PureTwitch/releases/latest) and download **PureTV-Setup-x.x.x.exe** under Assets.
2. Run it. Windows may show a blue "Windows protected your PC" screen because the app isn't code-signed. Click **More info**, then **Run anyway**.
3. Open PureTV, go to **Account**, and sign in with your Twitch account.

PureTV updates itself. When a new version comes out, it offers the update on launch and installs it in one click.

<br>

## What you get

**Watching**
- No ads on live streams or past broadcasts
- A mini player that keeps the stream going while you browse, which you can drag and snap to any corner
- Pop out the player or chat into their own windows
- Multi-view: watch several streams side by side
- Audio-only mode, plus lower quality automatically while the stream sits in the mini player
- Clips from any channel, and PureTV follows raids for you

**Finding streams**
- Home shows your live follows, what you watched recently, and a **Coming up** shelf of scheduled streams from channels you follow
- Browse every category with live viewer counts, and pin the ones you like
- Discover streams with filters for language, tags and viewer count, sorted high to low or low to high
- Search finds channels, categories and settings in one place. Press **Ctrl+Shift+Space** anywhere to search without leaving the stream

**Chat**
- Chat that's easy to read, with emotes from Twitch, 7TV, BTTV and FFZ
- Labels when chat is in slow mode, emote only, followers only or sub only
- Click a name for a user card; ignore users and highlight words you care about

**Channels**
- Everything under the stream: title, tags, the channel's panels, chat rules and links
- Stats for nerds: 30-day averages, peaks and follower growth, plus live chat stats. Hide it in Settings if you don't want it

**Make it yours**
- Five themes, any accent color, text size and density
- Hide tabs you don't use, pick which tab the app opens on, and choose which shelves Home shows
- Back up and restore all your settings

<br>

<div align="center">

<img src="docs/images/browse.png" alt="Browsing categories" width="440">
&nbsp;
<img src="docs/images/settings.png" alt="Settings with color themes" width="440">

<br><br>

<img src="docs/images/live-now.png" alt="The Live now grid" width="440">
&nbsp;
<img src="docs/images/channel.png" alt="A channel page with past broadcasts" width="440">

</div>

<br>

## Keyboard shortcuts

| Key | On a stream |
|---|---|
| **Ctrl+Shift+Space** | Search anywhere in the app |
| **Space** | Play or pause |
| **F** | Fullscreen |
| **T** | Theater mode |
| **C** | Show or hide chat |
| **M** | Mute |
| **Up / Down** | Volume |
| **Esc** | Leave fullscreen or theater |

<br>

## Following channels

The Follow button shows whether you follow a channel on Twitch. Twitch doesn't let other apps follow or unfollow for you, so clicking it opens the channel on twitch.tv, where you click Follow yourself. PureTV picks up the change when you come back.

<br>

## Privacy

- You sign in with your own Twitch account through Twitch's official sign-in page. PureTV never sees your password.
- Your login stays encrypted on your PC.
- The ad blocking runs on your PC. No relay server sits between you and Twitch.

<br>

## Build it yourself

Kotlin Multiplatform with Compose for Desktop and VLC. The shared `core` module holds the Twitch API client, sign-in, chat and the ad-block engine.

```bash
./gradlew :app-windows:run    # needs JDK 17 and VLC
```

Setup details live in [docs/DEVELOPING.md](docs/DEVELOPING.md).

<br>

## Credits

This is a Windows-focused build of [dhawal-ss/puretv](https://github.com/dhawal-ss/puretv), with its own features on top. The ad-block engine is a Kotlin port of [pixeltris/TwitchAdSolutions](https://github.com/pixeltris/TwitchAdSolutions) (`vaft`); [docs/ADBLOCK-REFERENCE.md](docs/ADBLOCK-REFERENCE.md) pins the upstream version it tracks.

PureTV isn't affiliated with or endorsed by Twitch.

<br>

## License

MIT, see [LICENSE](LICENSE).
