# MetaCheck product strategy — path to a real Google Play business

## Positioning

MetaCheck is a **privacy-first image metadata inspector and editor**, not an AI authenticity oracle.
Users can inspect GPS/EXIF/XMP, understand whether a photo leaks location/device data, edit supported JPEG tags and create cleaned copies on-device.

The initial product promise must be simple and verifiable: **"See what your photos reveal. Remove what you do not want to share."**
Do not promise that all AI content can be identified from metadata, that any image is proved authentic, or that hidden information can be erased from already shared copies.

## Minimum high-quality public release

1. Zero-data-loss single-file cleaning; duplicate first, never silently overwrite.
2. Clear metadata viewer and search. Explain absent metadata as *inconclusive*.
3. Verified JPEG EXIF editing with original preserved; overwrite only with independently verified backup.
4. Accessible text in RU/EN and clean daylight-friendly themes, minimum 7:1 for regular text in selected palettes.
5. Batch Pro with per-image progress, source preservation, no misleading success on failed verification.
6. Actual instrumented Android tests for read/write/backup; independent inspection of JPEG/PNG outputs.
7. Legally correct externally hosted privacy policy, app support contact, no undisclosed collection.
8. Play Billing test purchases and secure server-side validation before promising robust commercial anti-fraud.
9. Production-signed AAB, staged release, monitored ANRs/crashes and rollback plan.

## Suggested milestones, in order

- **Engineering stabilization:** fix data integrity; test release build, critical file operations and billing edge cases.
- **Closed beta:** gather reports from a small opt-in cohort using Samsung, Pixel and Xiaomi devices, at least several Android API levels and storage providers. Focus on safe file handling and comprehension.
- **Play Store 1.0:** position as privacy and metadata utility with screenshots of actual functions; ensure price/value is transparent, with Free genuinely useful.
- **Product-market fit:** conduct opt-in user interviews and count real repeat sessions, successful clean copies, successful exports, crashes/ANRs, refund requests and support tickets. Avoid silent third-party tracking.
- **Scale:** multilingual localization, verified device compatibility, accessibility, reliable support, paid channel experiments, trustworthy educational content, careful claims about AI generation. Evaluate C2PA verification only when implemented with real cryptographic signature checks.

## Metrics, not invented forecasts

- Success rate for file operations (successful *verified* saves / attempted saves).
- Data loss incidents: target zero, investigate every report.
- Crash-free/ANR-free sessions using privacy-respecting user-consented diagnostics.
- Retention D1/D7/D30 from consensual measurement or aggregate store analytics.
- Organic install conversion, customer-support response time, paid purchase completion/refunds.
- Store ratings derived only from genuine user feedback (no fabricated reviews).

A million installs is a market outcome, not something the code alone can guarantee. A useful, honest, safe first release is the prerequisite.
