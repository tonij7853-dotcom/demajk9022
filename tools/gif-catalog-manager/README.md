# Dismod GIF Studio

The repository also includes a [hosted Netlify manager](hosted/README.md) for invite-only multi-admin GIF publishing.

A private, local website for curating the GIF picker in Dismod. Paste a public GIF/image link or a share-page URL, inspect its animated preview, then save the checked GIF and catalog entry into the app repository.

The tool binds only to `127.0.0.1`, accepts same-origin writes, and never asks for or stores a GitHub token. Each imported item is fetched by the local server, converted to a standard GIF, and stored under `community-assets/gifs/`. Discord CDN query signatures and the original source URL are not copied into the public catalog.

## Run it on Windows

From the repository root, run:

```powershell
python -m pip install -r tools/gif-catalog-manager/requirements.txt
python tools/gif-catalog-manager/server.py
```

Open **http://127.0.0.1:8765**. Keep the terminal open while using the site; press **Ctrl+C** there to stop it. Close an earlier GIF Studio window if the port is already in use.

## Add a GIF

1. Paste a public HTTPS link. Direct GIFs work, as do public pages that expose an `og:image` or `twitter:image` preview (including many Discord, Tenor, and Giphy share links).
2. Wait for the preview and conversion checks. Animated GIF/WebP frames are retained. Still PNG/JPEG/WebP/BMP images become one-frame GIFs. MP4/WebM-only links are not converted.
3. Add a name, group name, group emoji, search tags, and confirm you have the right to redistribute the image. Click **Add to Dismod**. The manager writes the GIF and updates `community-assets/gifs/catalog.json` in this checkout. Existing catalog entries can be removed from the site.
4. Push your branch and merge the repository changes into `main` to publish GIFs to everyone. The app reads the public catalog when its GIF picker opens, so adding GIFs does not require a new APK.

The importer accepts only public HTTPS addresses and checks redirects to prevent private/local network fetches. The source download is limited to 40 MB; converted GIFs must be at most 15 MB, at most 240 frames, and no more than 1024 pixels in either dimension. The catalog is limited to 300 items. Add only assets you own or have permission to redistribute.
