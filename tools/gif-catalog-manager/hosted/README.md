# Dismod GIF Studio (hosted)

This is the hosted version of the GIF manager. It is a small Vite site with Netlify Functions. Netlify Identity gates the manager and every API route; publishing uses a fine-grained GitHub token held only in Netlify's server-side environment. It creates a pull request with the GIF and `community-assets/gifs/catalog.json` update. Squash-merge that PR to publish it to the Android picker; main-branch protection stays in place.

## Deploy to Netlify

1. Push/merge this folder to the GitHub repository, then create a Netlify site connected to `tonij7853-dotcom/demajk9022`.
2. Set the site's **Base directory** to `tools/gif-catalog-manager/hosted`. The included `netlify.toml` supplies the build command, publish folder, and function folder.
3. Enable Netlify Identity for that site. Set registration to **Invite only**; do not enable public sign-ups.
4. In the Netlify site's environment variables, set `GIF_GITHUB_TOKEN` to a new fine-grained GitHub token scoped only to this repository with **Contents: Read and write** and **Pull requests: Read and write**. Set these optional values only if needed: `GIF_GITHUB_OWNER=tonij7853-dotcom`, `GIF_GITHUB_REPO=demajk9022`, `GIF_GITHUB_BRANCH=main`.
5. In Identity > Users, invite your own email and each person you approve. Assign each approved account the exact role `gif-admin`. Accounts without that role can sign in but cannot use the studio API. Keep your Netlify team-owner account under your control; use the Netlify dashboard to add or remove the invited admins.
6. Set the Identity invitation/confirmation redirect to your deployed site URL. After deploy, open the URL, accept the invite, and sign in.

The GitHub token is never put in the page, Android app, catalog, repository, or browser storage. Never paste it into chat or commit it. Since a GitHub token was pasted into this chat earlier, revoke that token and use a newly created fine-grained token in the Netlify environment settings.

### Branch protection

The publisher creates one catalog pull request at a time so updates do not conflict. Review it and use **Squash and merge** with its generated `[skip ci] GIF catalog: ...` title. This keeps `main` protected and keeps catalog-only updates from starting the repository's automatic APK build/release workflow. The GitHub token never pushes to `main`; branch protection and required reviews remain in control. Once the PR is merged, users see the new catalog the next time they open the picker, without an APK update.

## Local development

Install Node.js 22, then from this directory run `npm install`, link the folder to the Netlify site, and run `npx netlify dev` for sign-in and function support. `npm run dev` starts only the visual Vite preview, without the Identity-backed API. Do not put the GitHub token in a `VITE_` variable; frontend variables are public.

The original loopback-only Python manager remains available at `../server.py` for offline catalog edits. It does not use Netlify login and should stay on `127.0.0.1`; do not expose it to the internet.

## GIF import limits

The importer accepts public HTTPS image URLs or pages with `og:image`/`twitter:image`, checks redirects and blocks private/local network destinations, converts GIF, animated WebP, APNG, PNG, JPEG, and BMP with Sharp, and writes validated GIF bytes. Source images are capped at 40 MB. Hosted output is capped at 4 MB so an animated preview can safely pass through the serverless function request limit; it is scaled to at most 1024 px and capped at 240 frames. The app catalog is capped at 300 items. Only import images you own or have permission to redistribute.

## Favorites and groups

Admins choose the group name and emoji when adding a GIF. The app shows those as picker groups. Favorites are per signed-in Dismod account and saved in Android app storage on that device; the current app has no server-side preference-sync endpoint, so favorites do not roam between devices.
