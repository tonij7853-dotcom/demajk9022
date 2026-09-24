import { getUser, handleAuthCallback, login, logout, onAuthChange } from '@netlify/identity';
import './styles.css';
import './overrides.css';

const $ = (selector) => document.querySelector(selector);
const loginPanel = $('#login-panel');
const adminApp = $('#admin-app');
const status = $('#form-status');
let user = null;
let stagedPreview = null;
let busy = false;
let pendingUpdate = null;

function setStatus(message = '', kind = '') {
  status.textContent = message;
  status.className = `status ${kind}`.trim();
}

function signedInView(nextUser) {
  user = nextUser;
  const roles = nextUser?.roles ?? [];
  $('#logout').hidden = !nextUser;
  $('#account-label').textContent = nextUser ? nextUser.email : 'Admin access required';
  if (!nextUser || !roles.includes('gif-admin')) {
    loginPanel.hidden = false;
    adminApp.hidden = true;
    if (nextUser) $('#login-status').textContent = 'This account is signed in but has no GIF admin access. Ask the site owner to assign the gif-admin role.';
    return;
  }
  loginPanel.hidden = true;
  adminApp.hidden = false;
  loadLibrary();
}

async function api(route, payload) {
  const response = await fetch(`/api/${route}`, {
    method: payload ? 'POST' : 'GET',
    headers: {
      ...(payload ? { 'Content-Type': 'application/json' } : {}),
    },
    credentials: 'same-origin',
    body: payload ? JSON.stringify(payload) : undefined,
    cache: 'no-store',
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || `Request failed (${response.status}).`);
  return data;
}

$('#login-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  $('#login-status').textContent = 'Signing in…';
  try {
    await login($('#email').value.trim(), $('#password').value);
    signedInView(await getUser());
  } catch (error) {
    $('#login-status').textContent = error.message || 'Sign-in failed.';
  }
});

$('#logout').addEventListener('click', async () => {
  await logout();
  signedInView(null);
});

$('#gif-url').addEventListener('input', () => {
  stagedPreview = null;
  $('#publish-button').disabled = true;
  $('#preview-image').hidden = true;
  $('#preview-empty').hidden = false;
  try {
    const file = decodeURIComponent(new URL($('#gif-url').value).pathname.split('/').filter(Boolean).at(-1) || '');
    const title = file.replace(/\.(gif|webp|png|jpe?g|apng)$/i, '').replace(/[-_]+/g, ' ').trim();
    if (title && !$('#gif-title').dataset.edited) $('#gif-title').value = title;
  } catch { /* Wait for a complete URL. */ }
});
$('#gif-title').addEventListener('input', () => { $('#gif-title').dataset.edited = 'true'; });

$('#gif-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = $('#preview-button');
  button.disabled = true;
  button.textContent = 'Checking link…';
  setStatus();
  try {
    stagedPreview = await api('preview', { url: $('#gif-url').value.trim() });
    $('#preview-image').src = `data:image/gif;base64,${stagedPreview.gifBase64}`;
    $('#preview-image').hidden = false;
    $('#preview-empty').hidden = true;
    $('#preview-heading').textContent = $('#gif-title').value.trim() || 'Ready to add';
    const details = $('#preview-details');
    details.replaceChildren();
    for (const value of [stagedPreview.frames > 1 ? `${stagedPreview.frames} frames · animated` : 'Still image · converted to GIF', `${stagedPreview.width} × ${stagedPreview.height}`, `${(stagedPreview.bytes / 1048576).toFixed(2)} MB`]) {
      const chip = document.createElement('span');
      chip.textContent = value;
      details.append(chip);
    }
    details.hidden = false;
    $('#publish-button').disabled = Boolean(pendingUpdate);
    setStatus('Preview checked. Add it to publish it for app users.', 'success');
  } catch (error) {
    stagedPreview = null;
    setStatus(error.message || 'Unable to preview this link.', 'error');
  } finally {
    button.disabled = false;
    button.textContent = 'Check & preview';
  }
});

$('#publish-button').addEventListener('click', async () => {
  if (!stagedPreview || busy) return;
  busy = true;
  const button = $('#publish-button');
  button.disabled = true;
  button.textContent = 'Publishing…';
  try {
    const result = await api('publish', {
      gifBase64: stagedPreview.gifBase64,
      sha256: stagedPreview.sha256,
      title: $('#gif-title').value.trim(),
      category: $('#gif-category').value.trim() || 'Other',
      categoryEmoji: $('#gif-emoji').value.trim() || '🙂',
      tags: $('#gif-tags').value.split(',').map((tag) => tag.trim()).filter(Boolean),
      rightsConfirmed: $('#rights-confirmed').checked,
    });
    setStatus(`${result.title} is ready. Squash-merge pull request #${result.number} to publish it for app users. `, 'success');
    const prLink = document.createElement('a');
    prLink.href = result.url;
    prLink.target = '_blank';
    prLink.rel = 'noopener noreferrer';
    prLink.textContent = 'Open pull request';
    status.append(prLink);
    $('#gif-url').value = '';
    $('#gif-title').value = '';
    $('#gif-title').dataset.edited = '';
    $('#gif-tags').value = '';
    $('#rights-confirmed').checked = false;
    stagedPreview = null;
    $('#preview-image').hidden = true;
    $('#preview-empty').hidden = false;
    $('#preview-heading').textContent = 'Your GIF will appear here';
    await loadLibrary();
  } catch (error) {
    setStatus(error.message || 'Publishing failed.', 'error');
  } finally {
    busy = false;
    button.disabled = !stagedPreview || Boolean(pendingUpdate);
    button.textContent = 'Create publish PR →';
  }
});

function makeCard(gif) {
  const card = document.createElement('article');
  card.className = 'gif-card';
  const thumb = document.createElement('div');
  thumb.className = 'gif-thumb';
  const image = document.createElement('img');
  image.src = gif.mediaUrl;
  image.alt = gif.title;
  image.loading = 'lazy';
  thumb.append(image);
  const content = document.createElement('div');
  content.className = 'gif-card-content';
  const title = document.createElement('p');
  title.className = 'gif-card-title';
  title.textContent = gif.title;
  const meta = document.createElement('div');
  meta.className = 'gif-card-meta';
  const category = document.createElement('span');
  category.textContent = `${gif.categoryEmoji || '🙂'} ${gif.category}`;
  const remove = document.createElement('button');
  remove.className = 'delete-gif';
  remove.type = 'button';
  remove.textContent = 'Remove';
  remove.dataset.id = gif.id;
  remove.disabled = Boolean(pendingUpdate);
  meta.append(category, remove);
  content.append(title, meta);
  card.append(thumb, content);
  return card;
}

async function loadLibrary() {
  try {
    const { gifs, pendingPullRequest } = await api('catalog');
    pendingUpdate = pendingPullRequest;
    const banner = $('#pending-update');
    banner.replaceChildren();
    banner.hidden = !pendingUpdate;
    if (pendingUpdate) {
      banner.append(document.createTextNode(`Pull request #${pendingUpdate.number} is awaiting review. Merge or close it before preparing another catalog change. `));
      const link = document.createElement('a');
      link.href = pendingUpdate.url;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      link.textContent = 'Review pull request';
      banner.append(link);
    }
    $('#publish-button').disabled = !stagedPreview || Boolean(pendingUpdate);
    $('#gif-count').textContent = gifs.length;
    $('#library-grid').replaceChildren(...gifs.map(makeCard));
    $('#library-empty').hidden = gifs.length > 0;
  } catch (error) {
    $('#library-grid').replaceChildren();
    $('#library-empty').textContent = error.message;
    $('#library-empty').hidden = false;
  }
}

$('#library-grid').addEventListener('click', async (event) => {
  const button = event.target.closest('.delete-gif');
  if (!button || !window.confirm('Remove this GIF from the app catalog?')) return;
  button.disabled = true;
  try {
    const result = await api('delete', { id: button.dataset.id });
    setStatus(`Removal is ready. Squash-merge pull request #${result.number} to publish it. `, 'success');
    const prLink = document.createElement('a');
    prLink.href = result.url;
    prLink.target = '_blank';
    prLink.rel = 'noopener noreferrer';
    prLink.textContent = 'Open pull request';
    status.append(prLink);
    await loadLibrary();
  } catch (error) {
    button.disabled = false;
    window.alert(error.message || 'Could not remove GIF.');
  }
});
$('#refresh-button').addEventListener('click', loadLibrary);

try { await handleAuthCallback(); } catch (error) { $('#login-status').textContent = error.message; }
signedInView(await getUser().catch(() => null));
onAuthChange((_event, nextUser) => signedInView(nextUser));
