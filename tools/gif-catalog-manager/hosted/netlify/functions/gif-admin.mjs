import { lookup } from 'node:dns/promises';
import { isIP } from 'node:net';
import { createHash, timingSafeEqual } from 'node:crypto';
import sharp from 'sharp';
import { getStore } from '@netlify/blobs';

const MAX_SOURCE = 40 * 1024 * 1024;
const MAX_OUTPUT = 4 * 1024 * 1024;
const MAX_CATALOG = 300;
const MAX_FRAMES = 240;
const MAX_ANIMATION_PIXELS = 90_000_000;
const ADMIN_ACCESS_CODE = (process.env.GIF_ADMIN_ACCESS_CODE || process.env.GIF_ACCESS_CODE || '').trim();
const SITE_DOMAIN = (process.env.URL || 'https://adminofdismod.netlify.app').replace(/\/+$/, '');

function reply(body, status = 200, extraHeaders = {}) {
  return Response.json(body, {
    status,
    headers: {
      'Cache-Control': 'no-store',
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'no-referrer',
      ...extraHeaders,
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
    if (!response.ok) {
      if (response.status === 404 && (url.includes('discordapp.com') || url.includes('discordapp.net'))) {
        fail('Discord reported "This content is no longer available" (404). Discord attachment links require security parameters (?ex=...&is=...&hm=...) and expire. Copy a fresh link from Discord or click "Upload file" to select the GIF directly from your device.', 404);
      }
      fail(`The source returned HTTP ${response.status}. Check that the link works.`);
    }
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

    if (metadata.format === 'gif' && data.byteLength <= 2 * 1024 * 1024 && frameWidth <= 420 && frameHeight <= 420 && frames <= 50) {
      return { data, width: frameWidth, height: frameHeight, frames };
    }

    const candidateConfigs = [];
    if (frames > 80 || data.byteLength > 10 * 1024 * 1024) {
      candidateConfigs.push({ width: 320, colours: 160 }, { width: 280, colours: 128 }, { width: 240, colours: 96 });
    } else if (frames > 40 || data.byteLength > 5 * 1024 * 1024) {
      candidateConfigs.push({ width: 380, colours: 224 }, { width: 320, colours: 160 }, { width: 260, colours: 128 });
    } else {
      candidateConfigs.push({ width: 420, colours: 256 }, { width: 360, colours: 224 }, { width: 300, colours: 160 });
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

function slug(value) {
  return value.normalize('NFKD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 48) || 'gif';
}

function getGifStore() {
  const options = { name: 'dismod-gifs', consistency: 'strong' };
  const siteID = process.env.NETLIFY_SITE_ID || process.env.NETLIFY_BLOBS_SITE_ID;
  const token = process.env.NETLIFY_API_TOKEN || process.env.NETLIFY_AUTH_TOKEN || process.env.NETLIFY_BLOBS_TOKEN;
  if (siteID && token) {
    options.siteID = siteID;
    options.token = token;
  }
  return getStore(options);
}

async function readCatalog() {
  try {
    const store = getGifStore();
    const catalog = await store.get('catalog.json', { type: 'json' });
    if (catalog && Array.isArray(catalog.gifs)) {
      let changed = false;
      for (const gif of catalog.gifs) {
        const id = gif.id || slug(gif.title);
        const expectedMedia = `${SITE_DOMAIN}/api/raw/${id}.gif`;
        const expectedThumb = `${SITE_DOMAIN}/api/raw/${id}-thumb.gif`;

        if (gif.mediaUrl !== expectedMedia) {
          gif.mediaUrl = expectedMedia;
          changed = true;
        }
        if (gif.previewUrl !== expectedThumb) {
          gif.previewUrl = expectedThumb;
          changed = true;
        }
      }
      if (changed) {
        await store.setJSON('catalog.json', catalog).catch(() => {});
      }
      return catalog;
    }
  } catch (error) {
    console.warn('[gif-studio] Could not read catalog from blob store:', error?.message || error);
  }
  return { version: 1, gifs: [] };
}

async function normalizeEmoji(data) {
  let input;
  try {
    input = sharp(data, { animated: true, limitInputPixels: 50_000_000, failOn: 'none' });
    const metadata = await input.metadata();
    if (!['gif', 'webp', 'png', 'jpeg', 'bmp'].includes(metadata.format)) {
      fail('Use a GIF, WebP, PNG, or JPEG emoji.');
    }
    const frames = metadata.pages || 1;
    const isAnimated = frames > 1;

    let outputData;
    let format = 'png';
    let mime = 'image/png';

    if (isAnimated) {
      outputData = await sharp(data, { animated: true, limitInputPixels: 50_000_000, failOn: 'none' })
        .resize({ width: 128, height: 128, fit: 'inside', withoutEnlargement: true })
        .gif({ loop: 0, effort: 4 })
        .toBuffer();
      format = 'gif';
      mime = 'image/gif';
    } else {
      outputData = await sharp(data, { failOn: 'none' })
        .resize({ width: 128, height: 128, fit: 'inside', withoutEnlargement: true })
        .png({ compressionLevel: 9 })
        .toBuffer();
      format = 'png';
      mime = 'image/png';
    }

    const sha256 = createHash('sha256').update(outputData).digest('hex');
    const outWidth = Math.min(metadata.width || 128, 128);
    const frameHeight = metadata.pageHeight || (frames > 1 ? Math.floor(metadata.height / frames) : metadata.height);
    const outHeight = Math.min(frameHeight || 128, 128);

    return {
      data: outputData,
      format,
      mime,
      isAnimated,
      frames,
      width: outWidth,
      height: outHeight,
      sha256,
      bytes: outputData.byteLength,
    };
  } catch (error) {
    if (error.status) throw error;
    fail(error.message || 'Could not parse emoji image.');
  }
}

async function readEmojiCatalog() {
  try {
    const store = getGifStore();
    const catalog = await store.get('emojis.json', { type: 'json' });
    if (catalog && Array.isArray(catalog.emojis)) {
      let changed = false;
      for (const emoji of catalog.emojis) {
        const ext = emoji.format || (emoji.isAnimated ? 'gif' : 'png');
        const filename = emoji.filename || `emoji-${emoji.shortcode || emoji.id}-${(emoji.sha256 || '').slice(0, 8)}.${ext}`;
        const expectedMedia = `${SITE_DOMAIN}/api/raw/${filename}`;
        if (emoji.mediaUrl !== expectedMedia) {
          emoji.mediaUrl = expectedMedia;
          emoji.previewUrl = expectedMedia;
          changed = true;
        }
        if (!emoji.filename) {
          emoji.filename = filename;
          changed = true;
        }
      }
      if (changed) {
        await store.setJSON('emojis.json', catalog).catch(() => {});
      }
      return catalog;
    }
  } catch (error) {
    console.warn('[gif-studio] Could not read emojis from blob store:', error?.message || error);
  }
  return { version: 1, emojis: [] };
}


export default async (request) => {
  try {
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        status: 204,
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
          'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Admin-Code, X-Access-Code',
          'Access-Control-Max-Age': '86400',
        },
      });
    }

    const url = new URL(request.url);
    const cleanPath = url.pathname.replace(/^\/\.netlify\/functions\/gif-admin/, '').replace(/^\/api/, '');
    const segments = cleanPath.split('/').filter(Boolean);
    const action = segments[0] || '';

    // Route: /api/raw/:filename (Public GIF / Emoji binary delivery)
    if (action === 'raw') {
      if (request.method !== 'GET' && request.method !== 'HEAD') {
        return reply({ error: 'Method not allowed.' }, 405);
      }
      const rawName = segments[1] || url.searchParams.get('file') || '';
      const filename = decodeURIComponent(rawName).trim();
      if (!filename || !/^[A-Za-z0-9_-]{1,120}\.(gif|png|webp|jpe?g)$/i.test(filename)) {
        return reply({ error: 'Invalid or missing media filename.' }, 400);
      }
      try {
        const store = getGifStore();
        let data = await store.get(filename, { type: 'arrayBuffer' });

        // If it's a requested thumbnail (-thumb.gif) that isn't cached yet, generate it on-the-fly from the source GIF
        if (!data && filename.endsWith('-thumb.gif')) {
          const sourceFilename = filename.replace(/-thumb\.gif$/i, '.gif');
          const sourceData = await store.get(sourceFilename, { type: 'arrayBuffer' });
          if (sourceData) {
            try {
              const thumbBuffer = await sharp(Buffer.from(sourceData), { animated: false, pages: 1 })
                .resize({ width: 240, height: 240, fit: 'inside', withoutEnlargement: true })
                .gif()
                .toBuffer();
              data = thumbBuffer;
              // Cache generated thumbnail in blob store for future instant loads
              await store.set(filename, thumbBuffer, {
                metadata: { source: sourceFilename, type: 'thumbnail' }
              }).catch(() => {});
            } catch (err) {
              console.warn('[gif-studio] Failed to generate thumbnail, falling back to source:', err);
              data = sourceData;
            }
          }
        }

        if (!data) {
          return reply({ error: 'Media not found.' }, 404);
        }
        const ext = filename.split('.').pop().toLowerCase();
        const contentType = ext === 'png' ? 'image/png' : ext === 'webp' ? 'image/webp' : (ext === 'jpg' || ext === 'jpeg') ? 'image/jpeg' : 'image/gif';
        return new Response(data, {
          status: 200,
          headers: {
            'Content-Type': contentType,
            'Cache-Control': 'public, max-age=31536000, immutable',
            'Access-Control-Allow-Origin': '*',
            'X-Content-Type-Options': 'nosniff',
          },
        });
      } catch (error) {
        console.error('[gif-studio] Error retrieving raw blob:', error);
        return reply({ error: 'Failed to retrieve media.' }, 500);
      }
    }

    // Route: /api/catalog (Public catalog for app and manager)
    if (request.method === 'GET' && action === 'catalog') {
      const [catalog, emojiCatalog] = await Promise.all([readCatalog(), readEmojiCatalog()]);
      return Response.json(
        { gifs: catalog.gifs, emojis: emojiCatalog.emojis, pendingPullRequest: null },
        {
          status: 200,
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
            'Cache-Control': 'public, max-age=30, s-maxage=30, stale-while-revalidate=120',
            'Access-Control-Allow-Origin': '*',
            'X-Content-Type-Options': 'nosniff',
          },
        }
      );
    }

    // Route: /api/emojis (Public emoji catalog for Dismod app and studio)
    if (request.method === 'GET' && action === 'emojis') {
      const emojiCatalog = await readEmojiCatalog();
      return Response.json(
        { version: 1, emojis: emojiCatalog.emojis },
        {
          status: 200,
          headers: {
            'Content-Type': 'application/json; charset=utf-8',
            'Cache-Control': 'public, max-age=30, s-maxage=30, stale-while-revalidate=120',
            'Access-Control-Allow-Origin': '*',
            'X-Content-Type-Options': 'nosniff',
          },
        }
      );
    }

    // All management endpoints below require admin access
    requireAdmin(request);

    if (action === 'auth') {
      return reply({ ok: true, admin: true });
    }

    if (request.method !== 'POST') {
      return reply({ error: 'Not found.' }, 404);
    }

    const payload = await jsonBody(request);

    // Route: /api/preview (Admin preview of source URL / uploaded file)
    if (action === 'preview') {
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
        gifBase64: converted.data.toString('base64'),
        sha256,
        bytes: converted.data.byteLength,
        width: converted.width,
        height: converted.height,
        frames: converted.frames,
      });
    }

    // Route: /api/publish (Admin auto-publish into Netlify Blobs)
    if (action === 'publish') {
      const title = String(payload.title || '').replace(/[\r\n\0]+/g, ' ').trim();
      const category = String(payload.category || 'Other').trim() || 'Other';
      const categoryEmoji = String(payload.categoryEmoji || '🙂').trim() || '🙂';
      if (!title || title.length > 80) fail('Add a title up to 80 characters.');
      if (category.length > 32 || categoryEmoji.length > 8) fail('Keep the group name under 32 characters and use one emoji.');
      if (payload.rightsConfirmed !== true) fail('Confirm you have permission to share this GIF.');
      if (!Array.isArray(payload.tags) || payload.tags.length > 20) fail('Add up to 20 search tags.');
      const tags = payload.tags.map((tag) => String(tag).trim().slice(0, 32)).filter(Boolean);
      const base64 = String(payload.gifBase64 || '');
      if (!/^[A-Za-z0-9+/]+={0,2}$/.test(base64) || base64.length > Math.ceil(MAX_OUTPUT / 3) * 4 + 8) {
        fail('Preview the GIF again; the file is invalid or too large.', 413);
      }
      const data = Buffer.from(base64, 'base64');
      if (data.length > MAX_OUTPUT || createHash('sha256').update(data).digest('hex') !== payload.sha256) {
        fail('The preview changed or is too large. Check the link again.', 413);
      }
      const signature = data.subarray(0, 6).toString('ascii');
      if (!['GIF87a', 'GIF89a'].includes(signature)) fail('The preview is not a valid GIF.');

      const store = getGifStore();
      const catalog = await readCatalog();
      if (catalog.gifs.length >= MAX_CATALOG) fail(`The shared picker is limited to ${MAX_CATALOG} GIFs.`, 413);
      if (catalog.gifs.some((gif) => gif.sha256 === payload.sha256)) fail('That GIF is already in the catalog.', 409);

      const id = `${slug(title)}-${String(payload.sha256).slice(0, 10)}`;
      const filename = `${id}.gif`;
      const thumbFilename = `${id}-thumb.gif`;

      // Generate static first-frame thumbnail for lag-free mobile & grid previews
      let thumbData;
      try {
        thumbData = await sharp(data, { animated: false, pages: 1 })
          .resize({ width: 240, height: 240, fit: 'inside', withoutEnlargement: true })
          .gif()
          .toBuffer();
      } catch {
        thumbData = data;
      }

      // Store GIF binary privately in Netlify Blobs
      await store.set(filename, data, {
        metadata: {
          id,
          title,
          sha256: payload.sha256,
          createdAt: new Date().toISOString(),
        },
      });

      // Store thumbnail in Netlify Blobs
      await store.set(thumbFilename, thumbData, {
        metadata: {
          id,
          type: 'thumbnail',
          createdAt: new Date().toISOString(),
        },
      });

      const mediaUrl = `${SITE_DOMAIN}/api/raw/${filename}`;
      const previewUrl = `${SITE_DOMAIN}/api/raw/${thumbFilename}`;
      catalog.gifs.push({
        id,
        title,
        category,
        categoryEmoji,
        tags,
        mediaUrl,
        previewUrl,
        sha256: payload.sha256,
      });

      // Update catalog JSON in Netlify Blobs
      await store.setJSON('catalog.json', catalog);

      return reply({ ok: true, direct: true, title, filename, mediaUrl, previewUrl });
    }

    // Route: /api/delete (Admin remove GIF from Netlify Blobs)
    if (action === 'delete') {
      const id = String(payload.id || '').trim();
      if (!id) fail('Missing GIF id.');
      const store = getGifStore();
      const catalog = await readCatalog();
      const index = catalog.gifs.findIndex((gif) => gif.id === id);
      if (index < 0) fail('That GIF is no longer in the catalog.', 404);
      const [removed] = catalog.gifs.splice(index, 1);

      let filename = `${removed.id}.gif`;
      if (removed.mediaUrl) {
        try {
          const parsed = new URL(removed.mediaUrl);
          const lastPart = parsed.pathname.split('/').filter(Boolean).at(-1);
          if (lastPart && lastPart.endsWith('.gif')) filename = lastPart;
        } catch { /* use default filename */ }
      }

      try {
        await store.delete(filename);
        await store.delete(`${removed.id}-thumb.gif`).catch(() => {});
      } catch (error) {
        console.warn(`[gif-studio] Could not delete blob ${filename}:`, error?.message || error);
      }

      await store.setJSON('catalog.json', catalog);

      return reply({ ok: true, direct: true, id, title: removed.title });
    }

    // Route: /api/preview-emoji (Admin preview of source URL / uploaded emoji)
    if (action === 'preview-emoji') {
      const sourceUrl = String(payload.url || '').trim();
      const fileBase64 = String(payload.fileBase64 || '').trim();
      if (!sourceUrl && !fileBase64) fail('Paste an emoji link or choose a file.');

      let converted;
      let lastError;
      if (fileBase64) {
        if (fileBase64.length > Math.ceil(MAX_SOURCE / 3) * 4 + 8) fail('That file is too large to import.', 413);
        const data = Buffer.from(fileBase64, 'base64');
        converted = await normalizeEmoji(data);
      } else {
        if (sourceUrl.length > 4096) fail('That link is too long.');
        for (const candidate of await imageCandidates(sourceUrl)) {
          try {
            converted = await normalizeEmoji(candidate.data);
            break;
          } catch (error) {
            lastError = error;
          }
        }
      }

      if (!converted) throw lastError || new Error('No supported emoji image was found.');

      return reply({
        emojiBase64: converted.data.toString('base64'),
        sha256: converted.sha256,
        format: converted.format,
        mime: converted.mime,
        isAnimated: converted.isAnimated,
        frames: converted.frames,
        width: converted.width,
        height: converted.height,
        bytes: converted.bytes,
      });
    }

    // Route: /api/publish-emoji (Admin add emoji to Netlify Blobs & emojis.json)
    if (action === 'publish-emoji') {
      const shortcodeRaw = String(payload.shortcode || '').trim().replace(/^:+|:+$/g, '');
      const shortcode = slug(shortcodeRaw) || 'emoji';
      const name = String(payload.name || payload.title || shortcode).trim().slice(0, 60);
      const category = String(payload.category || 'Reactions').trim() || 'Reactions';
      const categoryEmoji = String(payload.categoryEmoji || '✨').trim() || '✨';
      const sourceUrl = String(payload.sourceUrl || '').trim();

      if (!shortcode) fail('Provide an emoji shortcode (e.g. ordik).');
      if (payload.rightsConfirmed !== true) fail('Confirm you have permission to share this emoji.');

      const base64 = String(payload.emojiBase64 || '');
      if (!base64) fail('Preview the emoji before adding.');
      const data = Buffer.from(base64, 'base64');
      const sha256 = createHash('sha256').update(data).digest('hex');
      if (sha256 !== payload.sha256) fail('The preview changed. Check the link again.');

      const isAnimated = payload.isAnimated === true;
      const format = payload.format === 'gif' || isAnimated ? 'gif' : 'png';
      const filename = `emoji-${shortcode}-${sha256.slice(0, 8)}.${format}`;

      const store = getGifStore();
      const catalog = await readEmojiCatalog();
      if (catalog.emojis.some((e) => e.shortcode === shortcode)) {
        fail(`An emoji with shortcode :${shortcode}: already exists.`, 409);
      }

      // Store emoji binary in Netlify Blobs
      await store.set(filename, data, {
        metadata: {
          shortcode,
          name,
          category,
          sha256,
          isAnimated: String(isAnimated),
          createdAt: new Date().toISOString(),
        }
      });

      const mediaUrl = `${SITE_DOMAIN}/api/raw/${filename}`;
      const newEmoji = {
        id: `${shortcode}-${sha256.slice(0, 8)}`,
        name,
        shortcode,
        category,
        categoryEmoji,
        filename,
        format,
        mediaUrl,
        previewUrl: mediaUrl,
        sourceUrl: sourceUrl || undefined,
        isAnimated,
        sha256,
      };

      catalog.emojis.push(newEmoji);
      await store.setJSON('emojis.json', catalog);

      return reply({
        ok: true,
        emoji: newEmoji,
      });
    }

    // Route: /api/delete-emoji (Admin delete emoji from catalog)
    if (action === 'delete-emoji') {
      const id = String(payload.id || payload.shortcode || '').trim();
      if (!id) fail('Missing emoji id.');
      const store = getGifStore();
      const catalog = await readEmojiCatalog();
      const index = catalog.emojis.findIndex((e) => e.id === id || e.shortcode === id);
      if (index < 0) fail('Emoji not found in catalog.', 404);

      const [removed] = catalog.emojis.splice(index, 1);
      const ext = removed.format || (removed.isAnimated ? 'gif' : 'png');
      const filename = removed.filename || `emoji-${removed.shortcode || removed.id}-${(removed.sha256 || '').slice(0, 8)}.${ext}`;
      await store.delete(filename).catch(() => {});

      await store.setJSON('emojis.json', catalog);
      return reply({ ok: true, id: removed.id, shortcode: removed.shortcode });
    }

    return reply({ error: 'Not found.' }, 404);
  } catch (error) {
    const status = Number.isInteger(error.status) ? error.status : 500;
    if (status >= 500) console.error('[gif-studio]', error);
    return reply({ error: status >= 500 ? error.message || 'Something went wrong.' : error.message }, status);
  }
};
