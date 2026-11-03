# Data safety form cheat sheet (Google Play)

Русская версия: [DATA_SAFETY.ru.md](DATA_SAFETY.ru.md).

This is a guide derived from the app's code. Google Play's definitions of "collected" and "shared" change over time.
**[ACTION REQUIRED]** Before submitting the form, check every answer against the current Play Console help.

## What the app does with data
- Images, metadata, report history, settings: on the device only, never sent over the network.
  History is stored encrypted; the API key is protected by Android Keystore.
- Map: after the button press the app requests map tiles over HTTPS from MapTiler (if the build has the
  `METACHECK_MAPTILER_KEY` key), otherwise from the OpenStreetMap Foundation servers. The provider sees the IP address
  and tile numbers (which reveal the approximate area of the point). The file and its metadata are not sent.
  Before submitting the form, check which provider the release uses: its name is shown on the "Show map" button.
- Address search: the query text goes to the phone's system geocoder (on many devices a Google service).
- JSON report to the user's server: optional, off by default; the user chooses the recipient.
- Pro purchase: through Google Play; payment data is not available to the app.
- No accounts, ads, analytics or third-party tracking SDKs.

## Suggested answers
| Form question | Answer |
|---|---|
| Does the app collect or share user data? | Yes: approximate location (the area of the point when the map loads). Everything else stays on the device. |
| Approximate location | Shared with a third party (MapTiler or OpenStreetMap, depending on the build), optional (on button press), purpose: app functionality. |
| Photos and videos, files | Not collected (processed on the device only). |
| Is data encrypted in transit? | Yes (HTTPS). |
| Can users request data deletion? | The developer stores no data; the map cache is removed by clearing the cache or uninstalling the app. |
| Ads | No. |
| App for children | No. |

## What to check in the Play help
1. Whether a tile request triggered by a button press counts as "sharing" that must be declared (definitions of "collected" and "shared", exceptions for user-initiated actions).
2. Whether the IP address must be declared separately.
3. How to reflect ACCESS_MEDIA_LOCATION: coordinates from photos are read on the device and not transmitted.
