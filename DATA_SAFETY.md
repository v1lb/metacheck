# Data safety form cheat sheet (Google Play)

Русская версия: [DATA_SAFETY.ru.md](DATA_SAFETY.ru.md).

This is a guide derived from the app's code. Google Play's definitions of "collected" and "shared" change over time.
**[ACTION REQUIRED]** Before submitting the form, check every answer against the current Play Console help.

## What the app does with data
- Images, metadata, report history, settings: on the device only, never sent over the network.
  History is stored encrypted; the API key is protected by Android Keystore.
- Map: after the button press the app requests map tiles over HTTPS from MapTiler (if the build has the
  `METACHECK_MAPTILER_KEY` key), otherwise from the OpenStreetMap Foundation servers. The provider sees the IP address
  and tile numbers (the map opens at zoom 15–16, so they reveal the point to within a few hundred metres). The file and its metadata are not sent.
  Before submitting the form, check which provider the release uses: its name is shown on the "Show map" button.
- Address search: the query text goes to the phone's system geocoder (on many devices a Google service).
- JSON report to the user's server: optional, off by default; the user chooses the recipient.
- Pro purchase: through Google Play; payment data is not available to the app.
- No accounts, ads, analytics or third-party tracking SDKs.

## Suggested answers
| Form question | Answer |
|---|---|
| Does the app collect or share user data? | Yes: precise location (tiles at zoom 15–16 cover less than 3 km²). Everything else stays on the device. |
| Account creation | The app has no accounts; no login with external accounts. |
| Precise location | Collected (not shared: the map provider is a service provider), not ephemeral, optional (on button press), purpose: app functionality only. |
| Photos and videos, files | Not collected (processed on the device only). |
| Is data encrypted in transit? | Yes (HTTPS). |
| Can users request data deletion? | Left blank (optional): the developer stores no data; the map cache is removed by clearing the cache or uninstalling the app. |
| Ads | No. |
| Target age group | 18 and over only. |
| Privacy policy URL | https://v1lb.github.io/legal-metacheck/PRIVACY_POLICY_EN.html |

## What to check in the Play help
1. Whether a tile request triggered by a button press counts as "sharing" that must be declared (definitions of "collected" and "shared", exceptions for user-initiated actions).
2. Whether the IP address must be declared separately.
3. How to reflect ACCESS_MEDIA_LOCATION: coordinates from photos are read on the device and not transmitted.
