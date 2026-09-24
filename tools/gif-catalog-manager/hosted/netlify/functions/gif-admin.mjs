import { lookup } from 'node:dns/promises';
import { isIP } from 'node:net';
import { createHash, randomUUID, timingSafeEqual } from 'node:crypto';
import sharp from 'sharp';

const MAX_SOURCE = 40 * 1024 * 1024;
const MAX_OUTPUT = 4 * 1024 * 1024;
const MAX_CATALOG = 300;
const MAX_FRAMES = 240;
const MAX_ANIMATION_PIXELS = 90_000_000;
const OWNER = process.env.GIF_GITHUB_OWNER || 'tonij7853-dotcom';
const REPO = process.env.GIF_GITHUB_REPO || 'demajk9022';
const BRANCH = process.env.GIF_GITHUB_BRANCH || 'main';
const API = `https://api.github.com/repos/${OWNER}/${REPO}`;
const ADMIN_ACCESS_CODE = (process.env.GIF_ADMIN_ACCESS_CODE || process.env.GIF_ACCESS_CODE || '').trim();

function reply(body, status = 200) {
  return Response.json(body, {
    status,
    headers: {
      'Cache-Control': 'no-store',
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'no-referrer',
    },
  });
}

function fail(message, status = 400) {
  const error = new Error(message);
  error.status = status;
  throw error;
}

function safeCompare(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string' || !a || !b) return false;
  const hashA = createHash('sha256').update(a).digest();
  const hashB = createHash('sha256').update(b).digest();
  return timingSafeEqual(hashA, hashB);
}

function verifyRequestOrigin(request) {
  const origin = request.headers.get('origin');
  if (!origin) return;
  const host = request.headers.get('host');
  if (!host) return;
  try {
    const originHost = new URL(origin).host;
    if (originHost !== host) {
      fail('Cross-site request blocked.', 403);
    }
  } catch {
    fail('Invalid request origin.', 403);
  }
}

function extractAccessCode(request) {
  const auth = request.headers.get('authorization') || '';
  if (auth.toLowerCase().startsWith('bearer ')) {
    return auth.slice(7).trim();
  }
  const custom = request.headers.get('x-admin-code') || request.headers.get('x-access-code');
  if (custom) return custom.trim();
  return '';
}

function requireAdmin(request) {
  if (!ADMIN_ACCESS_CODE) {
    fail('GIF Studio admin access code is not configured. Set GIF_ADMIN_ACCESS_CODE in your Netlify site settings.', 503);
  }
  const providedCode = extractAccessCode(request);
  if (!providedCode) {
    fail('Sign in with your admin access code to use GIF Studio.', 401);
  }
  if (!safeCompare(providedCode, ADMIN_ACCESS_CODE)) {
    fail('Invalid admin access code.', 401);
  }
  if (request.method !== 'GET') verifyRequestOrigin(request);
}

async function jsonBody(request, max = 6 * 1024 * 1024) {
  const length = Number(request.headers.get('content-length') || 0);
  if (length > max) fail('That request is too large.', 413);
  try {
    const payload = await request.json();
    if (!payload || typeof payload !== 'object' || Array.isArray(payload)) fail('Expected a JSON object.');
    return payload;
  } catch (error) {
    if (error.status) throw error;
    fail('Could not read the request data.');
  }
}

function ipv4IsPublic(address) {
  const values = address.split('.').map(Number);
  if (values.length !== 4 || values.some((value) => !Number.isInteger(value) || value < 0 || value > 255)) return false;
  const [a, b] = values;
  return !(a === 0 || a === 10 || a === 127 || a >= 224 ||
    (a === 100 && b >= 64 && b <= 127) || (a === 169 && b === 254) ||
    (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168) ||
    (a === 192 && b === 0) || (a === 198 && (b === 18 || b === 19)) ||
    (a === 198 && b === 51) || (a === 203 && b === 0));
}

function ipIsPublic(address, family) {
  if (family === 4 || isIP(address) === 4) return ipv4IsPublic(address);
  const ip = address.toLowerCase().split('%')[0];
  if (ip.startsWith('::ffff:')) return ipv4IsPublic(ip.slice(7));
  const first = Number.parseInt(ip.split(':')[0] || '0', 16);
  return first >= 0x2000 && first <= 0x3fff && !ip.startsWith('2001:db8:');
}

async function validatePublicUrl(value) {
  let url;
  try { url = new URL(value); } catch { fail('Paste a valid public HTTPS link.'); }
  if (url.protocol !== 'https:' || !url.hostname || url.username || url.password || (url.port && url.port !== '443')) {
    fail('Use a public HTTPS link on the standard port.');
  }
  const host = url.hostname.replace(/\.$/, '');
  let addresses;
  if (isIP(host)) addresses = [{ address: host, family: isIP(host) }];
  else {
    try { addresses = await lookup(host, { all: true, verbatim: true }); }
    catch { fail('That link could not be resolved. Check the URL.'); }
  }
  if (!addresses.length || addresses.some(({ address, family }) => !ipIsPublic(address, family))) {
    fail('That link does not resolve to a public internet address.');
  }
  return url;
}

async function readLimited(response, limit) {
  const length = Number(response.headers.get('content-length') || 0);
  if (length > limit) fail('The source file is too large to import.', 413);
  if (!response.body) fail('The source did not return a file.');
  const reader = response.body.getReader();
  const chunks = [];
  let size = 0;
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > limit) {
      await reader.cancel();
      fail('The source file is too large to import.', 413);
    }
    chunks.push(Buffer.from(value));
  }
  return Buffer.concat(chunks, size);
}

async function safeFetch(value) {
  let url = value;
  for (let redirect = 0; redirect <= 5; redirect += 1) {
    await validatePublicUrl(url);

    let referer = '';
    try {
      const u = new URL(url);
      const parts = u.hostname.split('.');
      const rootDomain = parts.length >= 2 ? parts.slice(-2).join('.') : u.hostname;
      referer = `${u.protocol}//${rootDomain}/`;
    } catch { /* ignore */ }

    const makeHeaders = (ref) => ({
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
      Accept: 'image/avif,image/webp,image/apng,image/svg+xml,image/*,text/html;q=0.8,*/*;q=0.5',
      'Accept-Language': 'en-US,en;q=0.9',
      ...(ref ? { Referer: ref } : {}),
      'Sec-Fetch-Dest': 'image',
      'Sec-Fetch-Mode': 'no-cors',
      'Sec-Fetch-Site': 'cross-site',
    });

    let response;
    try {
      response = await fetch(url, {
        redirect: 'manual',
        signal: AbortSignal.timeout(20000),
        headers: makeHeaders(referer),
      });
      if ((response.status === 403 || response.status === 401) && referer) {
        const retry = await fetch(url, {
          redirect: 'manual',
          signal: AbortSignal.timeout(20000),
          headers: makeHeaders(''),
        });
        if (retry.ok) response = retry;
      }
    } catch { fail('Could not reach the link. It may have expired or block downloads.'); }
    if (response.status >= 300 && response.status < 400) {
      const location = response.headers.get('location');
      if (!location || redirect === 5) fail('The link has too many redirects.');
      url = new URL(location, url).href;
      continue;
    }
    if (!response.ok) fail(`The source returned HTTP ${response.status}. Check that the link works.`);
    const contentType = (response.headers.get('content-type') || '').split(';')[0].trim().toLowerCase();
    const limit = contentType === 'text/html' || contentType === 'application/xhtml+xml' ? 2 * 1024 * 1024 : MAX_SOURCE;
    return { data: await readLimited(response, limit), contentType, finalUrl: url };
  }
  fail('Could not follow that link.');
}

function ogImages(html) {
  const tags = html.match(/<meta\b[^>]*>/gi) || [];
  const results = [];
  for (const tag of tags) {
    const attrs = {};
    for (const match of tag.matchAll(/([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/g)) {
      attrs[match[1].toLowerCase()] = match[2] ?? match[3] ?? match[4] ?? '';
    }
    const key = (attrs.property || attrs.name || '').toLowerCase();
    if (['og:image', 'og:image:url', 'twitter:image', 'twitter:image:src'].includes(key) && attrs.content) {
      results.push(attrs.content.trim());
    }
  }
  return [...new Set(results)];
}

async function imageCandidates(url) {
  const urlsToTry = [url];
  try {
    const parsed = new URL(url);
    if (parsed.hostname === 'media.discordapp.net') {
      const cdnUrl = new URL(url);
      cdnUrl.hostname = 'cdn.discordapp.com';
      cdnUrl.searchParams.delete('width');
      cdnUrl.searchParams.delete('height');
      urlsToTry.push(cdnUrl.href);
    }
  } catch { /* Handled in safeFetch */ }

  let lastError;
  for (const candidateUrl of urlsToTry) {
    try {
      const source = await safeFetch(candidateUrl);
      const looksHtml = /^\s*(?:<!doctype\s+html|<html|<!--)/i.test(source.data.subarray(0, 128).toString('utf8'));
      if (!['text/html', 'application/xhtml+xml'].includes(source.contentType) && !looksHtml) return [source];
      const candidates = ogImages(source.data.toString('utf8')).slice(0, 4);
      if (!candidates.length) fail('This page has no public image preview. Paste a direct GIF or image link.');
      const result = [];
      for (const candidate of candidates) {
        try { result.push(await safeFetch(new URL(candidate, source.finalUrl).href)); } catch { /* Try the next public preview. */ }
      }
      if (result.length) return result;
    } catch (error) {
      lastError = error;
    }
  }
  if (lastError) throw lastError;
  fail('Could not download a public image from that page.');
}

async function normalizeGif(data) {
  let input;
  try {
    input = sharp(data, { animated: true, limitInputPixels: 100_000_000, failOn: 'none' });
    const metadata = await input.metadata();
    if (!['gif', 'webp', 'png', 'jpeg', 'bmp'].includes(metadata.format)) fail('Use a GIF, animated WebP, PNG, or JPEG image.');
    const frames = metadata.pages || 1;
    if (frames > MAX_FRAMES) fail(`That animation has more than ${MAX_FRAMES} frames.`);
    const frameHeight = metadata.pageHeight || (frames > 1 ? Math.floor(metadata.height / frames) : metadata.height);
    const frameWidth = metadata.width;
    if (!frameWidth || !frameHeight) fail('Invalid image dimensions.');
    const totalPixels = frameWidth * metadata.height;
    if (frameWidth * frameHeight > 20_000_000) fail('That image is too large to convert safely.');
    if (totalPixels > MAX_ANIMATION_PIXELS) fail('That animation is too large to convert safely.');

    if (metadata.format === 'gif' && data.byteLength <= MAX_OUTPUT && frameWidth <= 1024 && frameHeight <= 1024) {
      return { data, width: frameWidth, height: frameHeight, frames };
    }

    const candidateConfigs = [];
    if (frames > 100 || data.byteLength > 12 * 1024 * 1024) {
      candidateConfigs.push({ width: 320, colours: 160 }, { width: 280, colours: 128 }, { width: 240, colours: 96 });
    } else if (frames > 50 || data.byteLength > 6 * 1024 * 1024) {
      candidateConfigs.push({ width: 480, colours: 256 }, { width: 360, colours: 192 }, { width: 280, colours: 128 });
    } else {
      candidateConfigs.push({ width: 720, colours: 256 }, { width: 480, colours: 256 }, { width: 360, colours: 192 }, { width: 280, colours: 128 });
    }

    let finalData = null;
    let finalInfo = null;

    for (const { width: targetWidth, colours } of candidateConfigs) {
      const { data: output, info } = await sharp(data, { animated: true, limitInputPixels: 100_000_000, failOn: 'none' })
        .resize({ width: targetWidth, height: targetWidth, fit: 'inside', withoutEnlargement: true })
        .gif({ loop: 0, effort: 3, ...(colours < 256 ? { colours } : {}) })
        .toBuffer({ resolveWithObject: true });

      if (output.byteLength <= MAX_OUTPUT) {
        finalData = output;
        finalInfo = info;
        break;
      }
    }

    if (!finalData) {
      const { data: output, info } = await sharp(data, { animated: true, limitInputPixels: 100_000_000, failOn: 'none' })
        .resize({ width: 240, height: 240, fit: 'inside', withoutEnlargement: true })
        .gif({ loop: 0, effort: 3, colours: 64 })
        .toBuffer({ resolveWithObject: true });
      if (output.byteLength <= MAX_OUTPUT) {
        finalData = output;
        finalInfo = info;
      }
    }

    if (!finalData || finalData.byteLength > MAX_OUTPUT) {
      fail('The converted GIF is over 4 MB. Use a shorter animation or smaller image.', 413);
    }

    const actualFrames = finalInfo.pages || frames;
    const finalWidth = finalInfo.width || frameWidth;
    const finalHeight = (finalInfo.pages && finalInfo.pages > 1) ? Math.floor(finalInfo.height / finalInfo.pages) : finalInfo.height;

    return { data: finalData, width: finalWidth, height: finalHeight, frames: actualFrames };
  } catch (error) {
    if (error.status) throw error;
    fail(error.message || 'That link did not contain a readable GIF or supported image.');
  }
}

function githubHeaders() {
  const token = process.env.GIF_GITHUB_TOKEN;
  if (!token) fail('GIF Studio is not fully configured yet. Add the GitHub publishing token to the Netlify site environment.', 503);
  return {
    Authorization: `Bearer ${token}`,
    Accept: 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2022-11-28',
    'User-Agent': 'Dismod-GIF-Studio',
  };
}

async function github(path, options = {}) {
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: { ...githubHeaders(), ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers },
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    if (response.status === 401 || response.status === 403) fail('GitHub rejected the publisher. Check the token permissions and branch rules in Netlify.', response.status);
    fail(data.message || 'GitHub did not accept the catalog update.', response.status >= 500 ? 502 : 409);
  }
  return data;
}

const catalogPath = 'community-assets/gifs/catalog.json';
function apiPath(path) { return `/contents/${path}?ref=${encodeURIComponent(BRANCH)}`; }

async function readCatalog() {
  try {
    const file = await github(apiPath(catalogPath));
    const text = Buffer.from(file.content.replace(/\s/g, ''), 'base64').toString('utf8');
    const catalog = JSON.parse(text);
    if (!Array.isArray(catalog.gifs)) fail('The catalog does not contain a gifs list.', 500);
    return catalog;
  } catch (error) {
    if (error.status === 404) return { gifs: [] };
    throw error;
  }
}

async function pendingCatalogPullRequest() {
  const pullRequests = await github(`/pulls?state=open&base=${encodeURIComponent(BRANCH)}&per_page=100`);
  return pullRequests.find((pull) => pull.head?.ref?.startsWith('gif-studio/')) || null;
}

async function commitFiles({ catalog, assetName, assetData, deleteAsset = false, title }) {
  const gifPath = `community-assets/gifs/${assetName}`;
  const catalogText = `${JSON.stringify(catalog, null, 2)}\n`;
  const pending = await pendingCatalogPullRequest();
  if (pending) fail(`Merge or close the open GIF catalog pull request #${pending.number} before making another catalog update.`, 409);
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const ref = await github(`/git/ref/heads/${encodeURIComponent(BRANCH)}`);
    const parentSha = ref.object.sha;
    const parent = await github(`/git/commits/${parentSha}`);
    const catalogBlob = await github('/git/blobs', {
      method: 'POST',
      body: JSON.stringify({ content: Buffer.from(catalogText).toString('base64'), encoding: 'base64' }),
    });
    const treeEntries = [
      { path: catalogPath, mode: '100644', type: 'blob', sha: catalogBlob.sha },
    ];
    if (assetData) {
      const blob = await github('/git/blobs', {
        method: 'POST',
        body: JSON.stringify({ content: assetData.toString('base64'), encoding: 'base64' }),
      });
      treeEntries.push({ path: gifPath, mode: '100644', type: 'blob', sha: blob.sha });
    } else if (deleteAsset) {
      treeEntries.push({ path: gifPath, sha: null });
    }
    const tree = await github('/git/trees', {
      method: 'POST',
      body: JSON.stringify({ base_tree: parent.tree.sha, tree: treeEntries }),
    });
    const commit = await github('/git/commits', {
      method: 'POST',
      body: JSON.stringify({ message: assetData ? `Add GIF: ${assetName}` : deleteAsset ? `Remove GIF: ${assetName}` : 'Update GIF catalog', tree: tree.sha, parents: [parentSha] }),
    });
    const branch = `gif-studio/${randomUUID()}`;
    await github('/git/refs', {
      method: 'POST',
      body: JSON.stringify({ ref: `refs/heads/${branch}`, sha: commit.sha }),
    });
    const action = deleteAsset ? 'remove' : 'add';
    const pull = await github('/pulls', {
      method: 'POST',
      body: JSON.stringify({
        title: `[skip ci] GIF catalog: ${action} ${title}`.slice(0, 120),
        head: `${OWNER}:${branch}`,
        base: BRANCH,
        body: `This change ${action}s **${title}** ${deleteAsset ? 'from' : 'to'} the Dismod GIF catalog.\n\nSquash-merge this pull request to publish the catalog update to app users. GIF-only changes do not need a new APK.`,
      }),
    });
    return { number: pull.number, url: pull.html_url };
  }
  fail('GitHub changed during publishing. Refresh the catalog and try again.', 409);
}

function slug(value) {
  return value.normalize('NFKD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 48) || 'gif';
}

export default async (request) => {
  try {
    requireAdmin(request);
    const route = new URL(request.url).pathname.split('/').filter(Boolean).at(-1);
    if (route === 'auth') {
      return reply({ ok: true, admin: true });
    }
    if (request.method === 'GET' && route === 'catalog') {
      const catalog = await readCatalog();
      const pending = await pendingCatalogPullRequest();
      return reply({
        gifs: catalog.gifs,
        pendingPullRequest: pending ? { number: pending.number, url: pending.html_url, title: pending.title } : null,
      });
    }
    if (request.method !== 'POST') return reply({ error: 'Not found.' }, 404);
    const payload = await jsonBody(request);
    if (route === 'preview') {
      const sourceUrl = String(payload.url || '').trim();
      const fileBase64 = String(payload.fileBase64 || '').trim();
      if (!sourceUrl && !fileBase64) fail('Paste a link or select a file to import.');
      let converted;
      let lastError;
      if (fileBase64) {
        if (fileBase64.length > Math.ceil(MAX_SOURCE / 3) * 4 + 8) fail('That file is too large to import.', 413);
        const data = Buffer.from(fileBase64, 'base64');
        converted = await normalizeGif(data);
      } else {
        if (sourceUrl.length > 4096) fail('That link is too long.');
        for (const candidate of await imageCandidates(sourceUrl)) {
          try { converted = await normalizeGif(candidate.data); break; }
          catch (error) { lastError = error; }
        }
      }
      if (!converted) throw lastError || new Error('No supported image was found.');
      const sha256 = createHash('sha256').update(converted.data).digest('hex');
      return reply({
        gifBase64: converted.data.toString('base64'), sha256, bytes: converted.data.byteLength,
        width: converted.width, height: converted.height, frames: converted.frames,
      });
    }
    if (route === 'publish') {
      const title = String(payload.title || '').replace(/[\r\n\0]+/g, ' ').trim();
      const category = String(payload.category || 'Other').trim() || 'Other';
      const categoryEmoji = String(payload.categoryEmoji || '🙂').trim() || '🙂';
      if (!title || title.length > 80) fail('Add a title up to 80 characters.');
      if (category.length > 32 || categoryEmoji.length > 8) fail('Keep the group name under 32 characters and use one emoji.');
      if (payload.rightsConfirmed !== true) fail('Confirm you have permission to share this GIF.');
      if (!Array.isArray(payload.tags) || payload.tags.length > 20) fail('Add up to 20 search tags.');
      const tags = payload.tags.map((tag) => String(tag).trim().slice(0, 32)).filter(Boolean);
      const base64 = String(payload.gifBase64 || '');
      if (!/^[A-Za-z0-9+/]+={0,2}$/.test(base64) || base64.length > Math.ceil(MAX_OUTPUT / 3) * 4 + 8) fail('Preview the GIF again; the file is invalid or too large.', 413);
      const data = Buffer.from(base64, 'base64');
      if (data.length > MAX_OUTPUT || createHash('sha256').update(data).digest('hex') !== payload.sha256) fail('The preview changed or is too large. Check the link again.', 413);
      const signature = data.subarray(0, 6).toString('ascii');
      if (!['GIF87a', 'GIF89a'].includes(signature)) fail('The preview is not a valid GIF.');
      const catalog = await readCatalog();
      if (catalog.gifs.length >= MAX_CATALOG) fail(`The shared picker is limited to ${MAX_CATALOG} GIFs.`, 413);
      if (catalog.gifs.some((gif) => gif.sha256 === payload.sha256)) fail('That GIF is already in the catalog.', 409);
      const id = `${slug(title)}-${String(payload.sha256).slice(0, 10)}`;
      const filename = `${id}.gif`;
      const mediaUrl = `https://raw.githubusercontent.com/${OWNER}/${REPO}/${BRANCH}/community-assets/gifs/${filename}`;
      catalog.gifs.push({ id, title, category, categoryEmoji, tags, mediaUrl, previewUrl: mediaUrl, sha256: payload.sha256 });
      const pullRequest = await commitFiles({ catalog, assetName: filename, assetData: data, title });
      return reply({ ok: true, title, filename, ...pullRequest });
    }
    if (route === 'delete') {
      const id = String(payload.id || '');
      const catalog = await readCatalog();
      const index = catalog.gifs.findIndex((gif) => gif.id === id);
      if (index < 0) fail('That GIF is no longer in the catalog.', 404);
      const [removed] = catalog.gifs.splice(index, 1);
      const prefix = `https://raw.githubusercontent.com/${OWNER}/${REPO}/${BRANCH}/community-assets/gifs/`;
      const filename = removed.mediaUrl.startsWith(prefix) ? decodeURIComponent(removed.mediaUrl.slice(prefix.length)) : '';
      if (!/^[A-Za-z0-9_-]{1,80}\.gif$/i.test(filename)) fail('The catalog GIF path is invalid.', 500);
      const pullRequest = await commitFiles({ catalog, assetName: filename, deleteAsset: true, title: removed.title });
      return reply({ ok: true, ...pullRequest });
    }
    return reply({ error: 'Not found.' }, 404);
  } catch (error) {
    const status = Number.isInteger(error.status) ? error.status : 500;
    if (status >= 500) console.error('[gif-studio]', error);
    return reply({ error: status >= 500 ? error.message || 'Something went wrong.' : error.message }, status);
  }
};
