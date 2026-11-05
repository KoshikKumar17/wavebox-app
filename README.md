# Wavebox App(Android, Kotlin + Material 3)

**For web ui:- [Click here](https://github.com/KoshikKumar17/wavebox-web)**

Search / paste-link player and M4A downloader for JioSaavn songs, albums and playlists,
using your worker API (`jiosaavnsearch-up.koshikkumar17.workers.dev`).

## Build on your phone (no PC)
1. Create a new GitHub repository (private is fine).
2. Unzip this project and upload ALL files and folders (Add file -> Upload files).
3. Open the Actions tab -> "Build APK" -> Run workflow (it also runs on every upload).
4. After ~5-10 minutes open the finished run -> Artifacts -> Wavebox-debug-apk, download, unzip, install.
   Allow "Install unknown apps" for your browser / file manager.
5. If a build fails, open the failed step, copy the red error lines and send them over.

## Features
- Animated soft pastel gradient that changes with the playing song (toggle in Settings)
- Home, Search, Library, Music (Downloaded + On device), Settings tabs
- Search songs / albums / playlists, or paste a song / album / playlist link
- Album and playlist pages: play, shuffle, sort, download all, save as my playlist
- Your own playlists, liked songs, recently played
- Player: seek, shuffle, repeat (off / all / one), queue, play next, sleep timer, notification controls
- Downloads: M4A or MP3 (choose in Settings), with tags (title, artist, album, year, copyright, comment = song link) + cover art
- Download folder: default Music/Wavebox on internal storage, or any folder you choose
- Play quality and download quality chosen in Settings
