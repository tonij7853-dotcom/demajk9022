# Dismod GIF catalog

The Android app reads `catalog.json` from this public repository. Use the local [GIF Studio](../../tools/gif-catalog-manager/README.md) or the invite-only [hosted GIF Studio](../../tools/gif-catalog-manager/hosted/README.md) to add and remove items; both save GIFs here and update the catalog.

GIF Studio turns share links into local copies, so Discord CDN signatures and other expiring source URLs are not stored in the app catalog. Its importer supports public HTTPS pages with an Open Graph/Twitter image, direct GIFs, animated WebP, and still PNG/JPEG/WebP/BMP images (converted to GIF). It caps catalog GIFs at 15 MB, limits animations to 240 frames, and resizes dimensions above 1024 pixels. It does not convert video-only links such as MP4/WebM.

Each entry may include `categoryEmoji` to label its group in the picker. The app saves each account's favorites locally on that device. Only add files you own or have permission to redistribute. The local manager stays on loopback; the hosted manager checks Netlify Identity's `gif-admin` role on every management request and keeps its GitHub write token server-side. It creates a reviewable catalog pull request; once merged, users see the change the next time they open the picker, without an APK update.
