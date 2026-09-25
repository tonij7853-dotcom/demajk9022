import './styles.css';
import './overrides.css';

const $ = (selector) => document.querySelector(selector);
const loginPanel = $('#login-panel');
const adminApp = $('#admin-app');
const status = $('#form-status');
const STORAGE_KEY = 'dismod_gif_admin_access_code';
let stagedPreview = null;
let busy = false;
let pendingUpdate = null;

function getStoredAccessCode() {
  try {
    return localStorage.getItem(STORAGE_KEY) || '';
  } catch {
    return '';
  }
}

function setStoredAccessCode(code) {
  try {
    if (code) {
      localStorage.setItem(STORAGE_KEY, code);
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch { /* storage not available */ }
}

function setStatus(message = '', kind = '') {
  status.textContent = message;
  status.className = `status ${kind}`.trim();
}

function signedInView(isSignedIn) {
  $('#logout').hidden = !isSignedIn;
  $('#account-label').textContent = isSignedIn ? 'Admin access active' : 'Admin access required';
  if (!isSignedIn) {
    loginPanel.hidden = false;
    adminApp.hidden = true;
    return;
  }
  loginPanel.hidden = true;
  adminApp.hidden = false;
  loadLibrary();
}

async function api(route, payload, codeOverride) {
  const code = codeOverride !== undefined ? codeOverride : getStoredAccessCode();
  const headers = {
    ...(payload ? { 'Content-Type': 'application/json' } : {}),
    ...(code ? { Authorization: `Bearer ${code}` } : {}),
  };
  const response = await fetch(`/api/${route}`, {
    method: payload ? 'POST' : 'GET',
    headers,
    credentials: 'same-origin',
    body: payload ? JSON.stringify(payload) : undefined,
    cache: 'no-store',
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(data.error || `Request failed (${response.status}).`);
    error.status = response.status;
    throw error;
  }
  return data;
}

$('#login-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const input = $('#access-code');
  const code = input.value.trim();
  if (!code) return;
  const loginStatus = $('#login-status');
  loginStatus.textContent = 'Verifying access code…';
  loginStatus.className = 'status';
  try {
    await api('auth', null, code);
    setStoredAccessCode(code);
    input.value = '';
    loginStatus.textContent = '';
    loginStatus.className = 'status';
    signedInView(true);
  } catch (error) {
    loginStatus.textContent = error.message || 'Invalid access code.';
    loginStatus.className = 'status error';
  }
});

$('#logout').addEventListener('click', () => {
  setStoredAccessCode(null);
  signedInView(false);
  const loginStatus = $('#login-status');
  loginStatus.textContent = 'Signed out.';
  loginStatus.className = 'status';
});

const toggleCodeButton = $('#toggle-code');
if (toggleCodeButton) {
  toggleCodeButton.addEventListener('click', () => {
    const input = $('#access-code');
    const isPassword = input.type === 'password';
    input.type = isPassword ? 'text' : 'password';
    toggleCodeButton.textContent = isPassword ? 'Hide' : 'Show';
  });
}

let stagedFileBase64 = null;

$('#gif-url').addEventListener('input', () => {
  stagedFileBase64 = null;
  stagedPreview = null;
  $('#publish-button').disabled = true;
  $('#preview-image').hidden = true;
  $('#preview-image').src = '';
  $('#preview-empty').hidden = false;
  $('#preview-details').hidden = true;
  try {
    const file = decodeURIComponent(new URL($('#gif-url').value).pathname.split('/').filter(Boolean).at(-1) || '');
    const title = file.replace(/\.(gif|webp|png|jpe?g|apng)$/i, '').replace(/[-_]+/g, ' ').trim();
    if (title && !$('#gif-title').dataset.edited) $('#gif-title').value = title;
  } catch { /* Wait for a complete URL. */ }
});
$('#gif-title').addEventListener('input', () => { $('#gif-title').dataset.edited = 'true'; });

const fileInput = $('#gif-file');
if (fileInput) {
  fileInput.addEventListener('change', () => {
    const file = fileInput.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      stagedFileBase64 = String(reader.result).split(',')[1] || '';
      $('#gif-url').value = '';
      const name = file.name.replace(/\.[^/.]+$/, '').replace(/[-_]+/g, ' ').trim();
      if (name && !$('#gif-title').dataset.edited) $('#gif-title').value = name;
      $('#gif-form').requestSubmit();
    };
    reader.readAsDataURL(file);
  });
}

$('#gif-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = $('#preview-button');
  button.disabled = true;
  button.textContent = 'Processing…';
  setStatus();
  try {
    const url = $('#gif-url').value.trim();
    if (!url && !stagedFileBase64) {
      throw new Error('Paste a link or choose a file to preview.');
    }
    const payload = stagedFileBase64 ? { fileBase64: stagedFileBase64 } : { url };
    stagedPreview = await api('preview', payload);
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
    setStatus('Preview ready. Click "Add to Dismod App" to save it.', 'success');
  } catch (error) {
    stagedPreview = null;
    $('#preview-image').hidden = true;
    $('#preview-image').src = '';
    $('#preview-empty').hidden = false;
    $('#preview-details').hidden = true;
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
  button.textContent = 'Adding to app…';
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
    if (result.direct) {
      setStatus(`"${result.title}" added successfully! It is now live in your Dismod app.`, 'success');
    } else {
      setStatus(`"${result.title}" added to catalog. `, 'success');
      if (result.url) {
        const prLink = document.createElement('a');
        prLink.href = result.url;
        prLink.target = '_blank';
        prLink.rel = 'noopener noreferrer';
        prLink.textContent = 'View update';
        status.append(prLink);
      }
    }
    $('#gif-url').value = '';
    $('#gif-title').value = '';
    $('#gif-title').dataset.edited = '';
    $('#gif-tags').value = '';
    $('#rights-confirmed').checked = false;
    stagedPreview = null;
    stagedFileBase64 = null;
    $('#preview-image').hidden = true;
    $('#preview-image').src = '';
    $('#preview-empty').hidden = false;
    $('#preview-details').hidden = true;
    $('#preview-heading').textContent = 'Your GIF will appear here';
    await loadLibrary();
  } catch (error) {
    setStatus(error.message || 'Adding GIF failed.', 'error');
  } finally {
    busy = false;
    button.disabled = !stagedPreview || Boolean(pendingUpdate);
    button.textContent = 'Add to Dismod App →';
  }
});

function makeCard(gif) {
  const card = document.createElement('article');
  card.className = 'gif-card';
  const thumb = document.createElement('div');
  thumb.className = 'gif-thumb';
  const image = document.createElement('img');
  const previewSrc = gif.previewUrl || gif.mediaUrl;
  const fullSrc = gif.mediaUrl || gif.previewUrl;
  image.src = previewSrc;
  image.alt = gif.title;
  image.loading = 'lazy';
  image.decoding = 'async';
  thumb.append(image);

  // Play animation only on hover on devices that support hover (desktop mouse)
  // On phones/touch screens, it stays as a static still image to prevent phone lag completely!
  if (window.matchMedia('(hover: hover) and (pointer: fine)').matches && previewSrc !== fullSrc) {
    card.addEventListener('mouseenter', () => {
      image.src = fullSrc;
    });
    card.addEventListener('mouseleave', () => {
      image.src = previewSrc;
    });
  }

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

function selectGroup(name, emoji) {
  if (!name) return;
  const categoryInput = $('#gif-category');
  const emojiInput = $('#gif-emoji');
  if (categoryInput) categoryInput.value = name;
  if (emojiInput && emoji) emojiInput.value = emoji;

  const select = $('#gif-category-select');
  if (select && select.value !== name) {
    select.value = name;
  }

  const chips = document.querySelectorAll('.group-chip');
  chips.forEach((c) => {
    c.classList.toggle('active', c.dataset.group === name);
  });
}

function updateGroupOptions(gifs = []) {
  const groupsMap = new Map();
  // Standard default if nothing yet
  groupsMap.set('Reactions', '🙂');

  for (const gif of gifs) {
    if (gif.category && gif.category.trim()) {
      const cat = gif.category.trim();
      const emoji = (gif.categoryEmoji || '🙂').trim();
      groupsMap.set(cat, emoji);
    }
  }

  const select = $('#gif-category-select');
  const chipsContainer = $('#group-chips');
  const currentCat = $('#gif-category')?.value.trim() || 'Reactions';

  if (select) {
    select.innerHTML = '<option value="">-- Choose existing group --</option>';
    for (const [name, emoji] of groupsMap.entries()) {
      const opt = document.createElement('option');
      opt.value = name;
      opt.dataset.emoji = emoji;
      opt.textContent = `${emoji} ${name}`;
      if (name.toLowerCase() === currentCat.toLowerCase()) {
        opt.selected = true;
      }
      select.appendChild(opt);
    }
  }

  if (chipsContainer) {
    chipsContainer.innerHTML = '';
    for (const [name, emoji] of groupsMap.entries()) {
      const chip = document.createElement('button');
      chip.type = 'button';
      chip.className = 'group-chip' + (name.toLowerCase() === currentCat.toLowerCase() ? ' active' : '');
      chip.dataset.group = name;
      chip.innerHTML = `<span>${emoji}</span> <span>${name}</span>`;
      chip.addEventListener('click', () => {
        selectGroup(name, emoji);
      });
      chipsContainer.appendChild(chip);
    }
  }
}

const groupSelect = $('#gif-category-select');
if (groupSelect) {
  groupSelect.addEventListener('change', () => {
    const val = groupSelect.value;
    if (val) {
      const opt = groupSelect.options[groupSelect.selectedIndex];
      selectGroup(val, opt?.dataset.emoji || '🙂');
    }
  });
}

const categoryInput = $('#gif-category');
if (categoryInput) {
  categoryInput.addEventListener('input', () => {
    const current = categoryInput.value.trim();
    if (groupSelect) groupSelect.value = current;
    const chips = document.querySelectorAll('.group-chip');
    chips.forEach((c) => {
      c.classList.toggle('active', c.dataset.group.toLowerCase() === current.toLowerCase());
    });
  });
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
    updateGroupOptions(gifs);
  } catch (error) {
    if (error.status === 401) {
      setStoredAccessCode(null);
      signedInView(false);
      const loginStatus = $('#login-status');
      loginStatus.textContent = 'Session ended because the access code was rejected. Please sign in again.';
      loginStatus.className = 'status error';
      return;
    }
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
    if (result.direct) {
      setStatus('GIF removed from Dismod app catalog.', 'success');
    } else {
      setStatus(`Removal ready. Merge pull request #${result.number} to complete. `, 'success');
      if (result.url) {
        const prLink = document.createElement('a');
        prLink.href = result.url;
        prLink.target = '_blank';
        prLink.rel = 'noopener noreferrer';
        prLink.textContent = 'View pull request';
        status.append(prLink);
      }
    }
    await loadLibrary();
  } catch (error) {
    button.disabled = false;
    window.alert(error.message || 'Could not remove GIF.');
  }
});
$('#refresh-button').addEventListener('click', loadLibrary);

// ===================== EMOJI STUDIO =====================
const tabBtnGifs = $('#tab-btn-gifs');
const tabBtnEmojis = $('#tab-btn-emojis');
const viewGifs = $('#view-gifs');
const viewEmojis = $('#view-emojis');
let activeStudioTab = 'gifs';

let stagedEmojiPreview = null;
let stagedEmojiFileBase64 = null;
let emojiBusy = false;

function setEmojiStatus(message = '', kind = '') {
  const el = $('#emoji-form-status');
  if (!el) return;
  el.textContent = message;
  el.className = `status ${kind}`.trim();
}

function switchTab(tab) {
  activeStudioTab = tab;
  if (tab === 'emojis') {
    tabBtnEmojis?.classList.add('active');
    tabBtnGifs?.classList.remove('active');
    if (viewEmojis) viewEmojis.hidden = false;
    if (viewGifs) viewGifs.hidden = true;
    loadEmojiLibrary();
  } else {
    tabBtnGifs?.classList.add('active');
    tabBtnEmojis?.classList.remove('active');
    if (viewGifs) viewGifs.hidden = false;
    if (viewEmojis) viewEmojis.hidden = true;
    loadLibrary();
  }
}

tabBtnGifs?.addEventListener('click', () => switchTab('gifs'));
tabBtnEmojis?.addEventListener('click', () => switchTab('emojis'));

function extractShortcodeFromUrl(urlStr) {
  try {
    const parsed = new URL(urlStr);
    const filename = decodeURIComponent(parsed.pathname.split('/').filter(Boolean).at(-1) || '');
    const clean = filename.replace(/\.(gif|webp|png|jpe?g)$/i, '');
    // Handle emoji.gg style: 990783-ordik -> ordik
    const parts = clean.split(/[-_]+/);
    const name = parts.length > 1 && /^\d+$/.test(parts[0]) ? parts.slice(1).join('_') : clean;
    return name.toLowerCase().replace(/[^a-z0-9_]/g, '').slice(0, 32);
  } catch {
    return '';
  }
}

$('#emoji-url')?.addEventListener('input', () => {
  stagedEmojiFileBase64 = null;
  stagedEmojiPreview = null;
  const publishBtn = $('#emoji-publish-button');
  if (publishBtn) publishBtn.disabled = true;
  $('#emoji-preview-box').hidden = true;
  $('#emoji-preview-empty').hidden = false;
  $('#emoji-preview-details').hidden = true;

  const url = $('#emoji-url').value.trim();
  const sc = extractShortcodeFromUrl(url);
  if (sc && !$('#emoji-shortcode').dataset.edited) {
    $('#emoji-shortcode').value = sc;
    if (!$('#emoji-title').dataset.edited) {
      $('#emoji-title').value = sc.charAt(0).toUpperCase() + sc.slice(1).replace(/_/g, ' ');
    }
  }
});

$('#emoji-shortcode')?.addEventListener('input', () => { $('#emoji-shortcode').dataset.edited = 'true'; });
$('#emoji-title')?.addEventListener('input', () => { $('#emoji-title').dataset.edited = 'true'; });

const emojiFileInput = $('#emoji-file');
if (emojiFileInput) {
  emojiFileInput.addEventListener('change', () => {
    const file = emojiFileInput.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      stagedEmojiFileBase64 = String(reader.result).split(',')[1] || '';
      $('#emoji-url').value = '';
      const clean = file.name.replace(/\.[^/.]+$/, '').toLowerCase().replace(/[^a-z0-9_]/g, '_');
      if (clean && !$('#emoji-shortcode').dataset.edited) {
        $('#emoji-shortcode').value = clean;
        if (!$('#emoji-title').dataset.edited) {
          $('#emoji-title').value = clean.charAt(0).toUpperCase() + clean.slice(1).replace(/_/g, ' ');
        }
      }
      $('#emoji-form')?.requestSubmit();
    };
    reader.readAsDataURL(file);
  });
}

$('#emoji-form')?.addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = $('#emoji-preview-button');
  button.disabled = true;
  button.textContent = 'Processing…';
  setEmojiStatus();
  try {
    const url = $('#emoji-url').value.trim();
    if (!url && !stagedEmojiFileBase64) {
      throw new Error('Paste an emoji link (e.g. from emoji.gg) or choose a file.');
    }
    const payload = stagedEmojiFileBase64 ? { fileBase64: stagedEmojiFileBase64 } : { url };
    stagedEmojiPreview = await api('preview-emoji', payload);

    const imgSrc = `data:${stagedEmojiPreview.mime};base64,${stagedEmojiPreview.emojiBase64}`;
    $('#emoji-preview-inline').src = imgSrc;
    $('#emoji-preview-jumbo').src = imgSrc;
    $('#emoji-preview-box').hidden = false;
    $('#emoji-preview-empty').hidden = true;
    $('#emoji-preview-heading').textContent = $('#emoji-title').value.trim() || 'Ready to add';

    const details = $('#emoji-preview-details');
    details.replaceChildren();
    for (const val of [
      stagedEmojiPreview.isAnimated ? `${stagedEmojiPreview.frames} frames · animated` : 'Static emoji',
      `${stagedEmojiPreview.width} × ${stagedEmojiPreview.height}`,
      `${(stagedEmojiPreview.bytes / 1024).toFixed(1)} KB`
    ]) {
      const chip = document.createElement('span');
      chip.textContent = val;
      details.append(chip);
    }
    details.hidden = false;
    $('#emoji-publish-button').disabled = false;
    setEmojiStatus('Preview ready! Looks small and crisp like Discord Nitro. Click "Add to Dismod App" to save.', 'success');
  } catch (error) {
    stagedEmojiPreview = null;
    $('#emoji-preview-box').hidden = true;
    $('#emoji-preview-empty').hidden = false;
    $('#emoji-preview-details').hidden = true;
    setEmojiStatus(error.message || 'Unable to preview this emoji link.', 'error');
  } finally {
    button.disabled = false;
    button.textContent = 'Check & preview';
  }
});

$('#emoji-publish-button')?.addEventListener('click', async () => {
  if (!stagedEmojiPreview || emojiBusy) return;
  emojiBusy = true;
  const button = $('#emoji-publish-button');
  button.disabled = true;
  button.textContent = 'Adding emoji…';
  try {
    const shortcode = $('#emoji-shortcode').value.trim().replace(/^:+|:+$/g, '');
    const title = $('#emoji-title').value.trim();
    const category = $('#emoji-category').value.trim() || 'Reactions';
    const categoryEmoji = $('#emoji-group-emoji').value.trim() || '✨';
    const sourceUrl = $('#emoji-url').value.trim();

    const result = await api('publish-emoji', {
      emojiBase64: stagedEmojiPreview.emojiBase64,
      sha256: stagedEmojiPreview.sha256,
      format: stagedEmojiPreview.format,
      isAnimated: stagedEmojiPreview.isAnimated,
      shortcode,
      name: title,
      category,
      categoryEmoji,
      sourceUrl,
      rightsConfirmed: $('#emoji-rights-confirmed').checked,
    });

    setEmojiStatus(`Emoji :${result.emoji?.shortcode || shortcode}: added successfully! It is now live in your Dismod app picker.`, 'success');

    $('#emoji-url').value = '';
    $('#emoji-shortcode').value = '';
    $('#emoji-shortcode').dataset.edited = '';
    $('#emoji-title').value = '';
    $('#emoji-title').dataset.edited = '';
    $('#emoji-tags').value = '';
    $('#emoji-rights-confirmed').checked = false;
    stagedEmojiPreview = null;
    stagedEmojiFileBase64 = null;
    $('#emoji-preview-box').hidden = true;
    $('#emoji-preview-empty').hidden = false;
    $('#emoji-preview-details').hidden = true;
    $('#emoji-preview-heading').textContent = 'Your emoji will appear here';

    await loadEmojiLibrary();
  } catch (error) {
    setEmojiStatus(error.message || 'Adding emoji failed.', 'error');
  } finally {
    emojiBusy = false;
    button.disabled = !stagedEmojiPreview;
    button.textContent = 'Add to Dismod App →';
  }
});

function makeEmojiCard(emoji) {
  const card = document.createElement('article');
  card.className = 'emoji-card';
  const thumb = document.createElement('div');
  thumb.className = 'emoji-thumb';
  const img = document.createElement('img');
  img.src = emoji.mediaUrl || emoji.previewUrl;
  img.alt = emoji.name;
  img.loading = 'lazy';
  thumb.append(img);

  const content = document.createElement('div');
  content.className = 'emoji-card-content';
  const code = document.createElement('p');
  code.className = 'emoji-card-shortcode';
  code.textContent = `:${emoji.shortcode}:`;
  code.title = `:${emoji.shortcode}:`;

  const meta = document.createElement('div');
  meta.className = 'emoji-card-meta';
  const pack = document.createElement('span');
  pack.className = 'emoji-card-pack';
  pack.textContent = `${emoji.categoryEmoji || '✨'} ${emoji.category || 'Other'}`;

  const delBtn = document.createElement('button');
  delBtn.className = 'delete-gif';
  delBtn.type = 'button';
  delBtn.textContent = '✕';
  delBtn.title = 'Remove emoji';
  delBtn.dataset.id = emoji.id;
  delBtn.dataset.shortcode = emoji.shortcode;

  meta.append(pack, delBtn);
  content.append(code, meta);
  card.append(thumb, content);
  return card;
}

function updateEmojiGroupOptions(emojis = []) {
  const groupsMap = new Map();
  groupsMap.set('Reactions', '🐸');
  groupsMap.set('Cats & Party', '🐱');
  groupsMap.set('Animated', '✨');
  groupsMap.set('Memes', '🔥');
  groupsMap.set('Gaming', '🎮');

  for (const emoji of emojis) {
    if (emoji.category && emoji.category.trim()) {
      const cat = emoji.category.trim();
      const icon = (emoji.categoryEmoji || '✨').trim();
      groupsMap.set(cat, icon);
    }
  }

  const select = $('#emoji-category-select');
  const chipsContainer = $('#emoji-group-chips');
  const currentCat = $('#emoji-category')?.value.trim() || 'Reactions';

  if (select) {
    select.innerHTML = '<option value="">-- Choose existing pack --</option>';
    for (const [name, icon] of groupsMap.entries()) {
      const opt = document.createElement('option');
      opt.value = name;
      opt.dataset.icon = icon;
      opt.textContent = `${icon} ${name}`;
      if (name.toLowerCase() === currentCat.toLowerCase()) opt.selected = true;
      select.appendChild(opt);
    }
  }

  if (chipsContainer) {
    chipsContainer.innerHTML = '';
    for (const [name, icon] of groupsMap.entries()) {
      const chip = document.createElement('button');
      chip.type = 'button';
      chip.className = 'group-chip' + (name.toLowerCase() === currentCat.toLowerCase() ? ' active' : '');
      chip.innerHTML = `<span>${icon}</span> <span>${name}</span>`;
      chip.addEventListener('click', () => {
        const catInput = $('#emoji-category');
        const iconInput = $('#emoji-group-emoji');
        if (catInput) catInput.value = name;
        if (iconInput) iconInput.value = icon;
        if (select) select.value = name;
        document.querySelectorAll('#emoji-group-chips .group-chip').forEach((c) => {
          c.classList.toggle('active', c === chip);
        });
      });
      chipsContainer.appendChild(chip);
    }
  }
}

const emojiCategorySelect = $('#emoji-category-select');
if (emojiCategorySelect) {
  emojiCategorySelect.addEventListener('change', () => {
    const val = emojiCategorySelect.value;
    if (val) {
      const opt = emojiCategorySelect.options[emojiCategorySelect.selectedIndex];
      const icon = opt?.dataset.icon || '✨';
      const catInput = $('#emoji-category');
      const iconInput = $('#emoji-group-emoji');
      if (catInput) catInput.value = val;
      if (iconInput) iconInput.value = icon;
    }
  });
}

async function loadEmojiLibrary() {
  try {
    const data = await api('emojis');
    const emojis = data.emojis || [];
    $('#emoji-count').textContent = emojis.length;
    $('#emoji-library-grid').replaceChildren(...emojis.map(makeEmojiCard));
    $('#emoji-library-empty').hidden = emojis.length > 0;
    updateEmojiGroupOptions(emojis);
  } catch (error) {
    $('#emoji-library-grid').replaceChildren();
    $('#emoji-library-empty').textContent = error.message;
    $('#emoji-library-empty').hidden = false;
  }
}

$('#emoji-library-grid')?.addEventListener('click', async (event) => {
  const button = event.target.closest('.delete-gif');
  if (!button || !window.confirm(`Remove emoji :${button.dataset.shortcode}: from Dismod?`)) return;
  button.disabled = true;
  try {
    await api('delete-emoji', { id: button.dataset.id, shortcode: button.dataset.shortcode });
    setEmojiStatus(`Emoji :${button.dataset.shortcode}: removed.`, 'success');
    await loadEmojiLibrary();
  } catch (error) {
    button.disabled = false;
    window.alert(error.message || 'Could not remove emoji.');
  }
});
$('#emoji-refresh-button')?.addEventListener('click', loadEmojiLibrary);

// ===================== SESSION INIT =====================
async function initSession() {
  const code = getStoredAccessCode();
  if (!code) {
    signedInView(false);
    return;
  }
  try {
    await api('auth', null, code);
    signedInView(true);
  } catch (error) {
    if (error.status === 401) {
      setStoredAccessCode(null);
      signedInView(false);
      const loginStatus = $('#login-status');
      loginStatus.textContent = 'Saved access code is no longer valid. Please sign in again.';
      loginStatus.className = 'status error';
    } else {
      signedInView(false);
      const loginStatus = $('#login-status');
      loginStatus.textContent = error.message || 'Could not verify admin access.';
      loginStatus.className = 'status error';
    }
  }
}

initSession();

