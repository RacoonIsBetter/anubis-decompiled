# Security

Anubis is open source (GPL-3.0). This page explains the parts of the client that handle logins and
outside connections, and how to tell an official build from a modified copy.

## Getting a genuine copy

- Download Anubis only through `/download` on the official Discord (discord.gg/anubis). A jar sent to
  you in DMs or re-uploaded somewhere else can have extra code added.
- Each release announcement lists the jar's SHA-256. On Windows, compare it with:
  `certutil -hashfile "Anubis Beta <version>.jar" SHA256`
- Release jars are signed with `jarsigner`. `jarsigner -verify -verbose -certs <file>.jar` shows the
  signing certificate.

## The startup build check

Before the game starts (Fabric's `preLaunch` stage), the client checks whether its own jar is still exactly
the signed release (`src/client/java/net/anubis/client/security/OfficialBuild.java`).

- It opens only the Anubis jar file, read-only, and uses the JDK's built-in jar signature verification.
- It uses no network, reads no other files, and never deletes or changes anything.
- If the jar differs from the signed release, the game is stopped before Minecraft and the other mods'
  main code start. A separate warning window (`TamperStop.java`, `TamperWindow.java`) explains what to do;
  its "Download Official Release" button opens discord.gg/anubis. One line is written to the game log.
- Development builds and unsigned local builds are not checked.
- Limit: someone who edits the jar can also edit or remove this check. It catches tools that add code to
  the jar without knowing about the check; it is not an antivirus. If the window appears and you did not
  change the file yourself, delete it, scan your PC, sign out of all devices in your Microsoft account,
  change your password and download Anubis again from the official Discord.

## Pay Guard

`src/client/java/net/anubis/client/security/PayGuard.java`

- A `/pay` command is only sent when you typed it into the chat box and pressed Enter, or when Panic Pay
  (Donut Macros or Player Detection) sends it.
- Any other `/pay`, for example one added to a modified copy, an add-on or a script, is not sent. The
  client shows a warning screen and a chat line instead, and writes one log line naming the code that tried.
- Limit: like the build check, a modified copy can remove it.

## Account switcher and proxies (Anubis Accounts add-on)

`addons/accounts/src/main/java/net/anubis/accounts/`

- Since 0.9.9.3 the account switcher, token and offline logins and the proxy list are not part of the
  Anubis jar. They ship as the optional `Anubis Accounts.jar`; without it Anubis has no login, token or
  proxy code, and the Accounts button and the multiplayer Accounts/Proxies bar are hidden.
- Microsoft sign-in uses Microsoft's device-code page in your browser; the client never sees your
  password.
- Tokens are sent only to Microsoft, Xbox Live and Mojang login services (`login.live.com`,
  `device.auth.xboxlive.com`, `sisu.xboxlive.com`, `api.minecraftservices.com`) and then handed to the
  game the same way the official launcher does.
- Saved logins stay on your PC in `config/anubis/accounts.json`, encrypted with AES-GCM. The key is a
  random file at `<user home>/.anubis/seal.key`, so a copied config folder alone doesn't reveal them; anyone
  who has both the key file and the config can decrypt them. Proxy logins in `config/anubis/proxies.json`
  are encrypted the same way. The account switcher, proxies and this encryption live in the separate
  Anubis Accounts add-on (`addons/accounts`, `net/anubis/accounts/util/SecretSeal.java`), not in the main
  client. Anubis doesn't call Windows' credential decryption (DPAPI); logins saved by 0.9.9.2 with it have
  to be signed in again.

## Webhooks

Player Detection, Spawner Protect and Coord Snapper can post alerts to a Discord webhook.

- There is no built-in webhook address. Alerts go only to the URL you type in; the field is empty by
  default and nothing is sent while it is empty.
- Webhook URLs are saved on your PC in `config/anubis/` as plain text, and are never included in shared
  profiles or config codes.
- Alerts contain only the game details described in the code comments of each module, never tokens or
  login data.

## DonutBets

`src/client/java/net/anubis/client/donutbets/DonutBetsFeed.java`

- The DonutBets feed is part of the client and always on; it is not a module and cannot be switched off.
  About once a minute it sends one plain GET request to the fixed address
  `https://donutbets.net/api/client/feed` and reads the event texts in the answer. The address is written in
  the code; it is not a setting, and nothing in the answer can change it.
- Nothing about you is sent: no account, name, UUID or token. The site sees your IP address, like any web
  request, and the User-Agent `Anubis-Client/<version> (DonutBets event feed, text only)`.
- Only plain text is taken from the answer: title, text, start and end time, and a link. It never runs
  commands, never sends anything to the Minecraft server, never downloads files and never starts programs.
  Colour codes in the texts (`&0`–`&f`, `&#RRGGBB`, `&l` `&o` `&n` `&m` `&k`, `&r`, `&rainbow`) are turned into
  colours and styles by the client itself; they cannot add clicks, hovers or commands.
  The answer is limited to 64 KB, 20 events and 12 seconds, and redirects are not followed.
- A link is only shown when it is an https address on donutbets.net. Opening it takes your click and
  Minecraft's own "open this link?" screen.
- The only file it writes is `config/anubis/donutbets_seen.json`, the ids of events already shown.
- Each start writes one line to the game log saying what it reads.
- The DonutBets title-screen invite opens the site only when you click Visit. The default DonutBets card
  banner is an image inside the jar and makes no request.

## Other outside connections

- Staff Live: the YouTube live page and TikTok live status of each channel in its own list.
- Spotify HUD lyrics: lrclib.net, with the playing song's title, artist, album and length. On macOS and
  Linux the cover image comes from the address the music player itself reports.
- Skin Change: Mojang (`api.mojang.com`, `sessionserver.mojang.com`), for the name you enter.
- Webhooks: only the address you type in (see above).

## Reporting a problem

If you find a security problem or a modified copy being passed around, tell the staff on the official
Discord.
