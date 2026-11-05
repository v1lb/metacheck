---
title: "Privacy Policy"
lang: en
---

# MetaCheck Privacy Policy

**Effective date:** 2026-11-06
**Developer:** Puwer Studio
**Contact:** werk9886@gmail.com

## 1. In short
MetaCheck shows the metadata of photos and videos (EXIF, GPS, XMP, PNG text chunks, MP4/MOV container tags, Content Credentials), looks for signs of AI generation in it, and lets you save a copy with cleaned metadata. The analysis runs on your device. The developer has no servers that the app sends your images, metadata or history to. The app has no user accounts, ads or analytics.

## 2. What is stored on your device
- **Photos and videos.** Files you choose (from the system file picker, via Share, or from a camera shot) are processed on the device. The app does not upload them. Camera shots are kept temporarily in the app cache and deleted automatically.
- **Report history.** If history is on, reports are stored only on the device, encrypted. They may contain file metadata, including GPS coordinates. You can turn history off and clear it in Settings.
- **Settings and API keys.** Stored locally. API keys are encrypted with Android Keystore.
- **File copies.** Cleaned copies are saved where you choose in the system dialog. The original file is not changed by default.

## 3. When data leaves the device
By default the app transmits nothing. Data is sent only after your own action:

- **Map.** If an image contains GPS coordinates, the map is off. After you tap "Show map (request to …)" the app downloads map tiles over HTTPS from the map provider named on that button:
  - **MapTiler** (MapTiler AG, Switzerland) is the main provider. The MapTiler map is built on OpenStreetMap data. MapTiler privacy policy: https://www.maptiler.com/privacy-policy/
  - **OpenStreetMap Foundation** is used in builds without MapTiler. OpenStreetMap Foundation privacy policy: https://osmfoundation.org/wiki/Privacy_Policy

  Tiles are requested by number, so the map provider can determine the location of the point shown to within a few hundred metres (the map opens at street level), which Google Play treats as precise location, and it also sees your IP address and the app name. The provider may use a content delivery network (CDN) that also processes your IP address. The image and its metadata are not sent. Downloaded tiles are cached on the device (up to 50 MB) and removed with the cache or the app.
- **Address search.** If you tap "Find on map", the text you typed is handled by your phone's system geocoder (on many devices this is a Google service). The app itself does not store or forward the query.
- **API check (optional).** If you turn it on in Settings, enter a server address and tap "Check via API", a text JSON report is sent to that address. It does not contain the image, and GPS coordinates are excluded unless you enable them separately. The server you choose receives the data; the MetaCheck developer does not.
- **"Send without metadata".** If you choose this option, the app creates a cleaned copy of the photo or video and passes it to the app you selected in the Share window. The copy is kept temporarily in the MetaCheck cache and deleted within a day. The MetaCheck developer does not receive it.
- **Purchases.** Pro is bought through Google Play, and Google processes payment data. The app only receives confirmation of the purchase.

MapTiler, OpenStreetMap and Google servers may be located outside your country.

## 4. Android permissions
- **Internet and network state** — only to load the map when you press the button, to send an optional report to your address, and to check whether a network is available.
- **Access to location in media (ACCESS_MEDIA_LOCATION)** — to read the coordinates stored in your photos and videos. Coordinates are processed on the device.
- **Google Play billing (BILLING)** — to make and verify the Pro purchase.

The app does not request direct access to your gallery, camera, shared storage or Wi-Fi: files are chosen and saved through system dialogs, and photos are taken by your phone's camera app. If a library the app uses declares such permissions, they are removed at build time.

## 5. What the app does not do
The app does not create accounts, show ads, use analytics or third-party tracking SDKs, or sell or share data with ad networks. It uses the Google Play Billing and osmdroid libraries; a list of components is available in the app.

## 6. Storage and deletion
All data is stored on your device. You can delete reports in Settings. Uninstalling the app erases settings, cache (including the map cache) and history.

## 7. Your rights
The developer does not receive your images, metadata or history, so in normal use of the app the developer does not process your personal data. If you email support, we use your address and message only to reply and delete them on request. You have the right to request access, correction and deletion of your data, to object to processing, and to complain to the data protection authority in your country (in Italy, the Garante per la protezione dei dati personali).

## 8. Children
The app is not intended to collect data about children and does not knowingly collect personal information.

## 9. Changes
We may update this policy. Changes are published on this page with a new effective date.

## 10. Contact
Puwer Studio, werk9886@gmail.com
