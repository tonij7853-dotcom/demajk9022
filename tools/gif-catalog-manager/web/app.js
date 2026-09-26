const form = document.querySelector('#gif-form');
const urlField = document.querySelector('#gif-url');
const titleField = document.querySelector('#gif-title');
const categoryField = document.querySelector('#gif-category');
const categoryEmojiField = document.querySelector('#gif-category-emoji');
const tagsField = document.querySelector('#gif-tags');
const rightsField = document.querySelector('#rights-confirmed');
const previewButton = document.querySelector('#preview-button');
const publishButton = document.querySelector('#publish-button');
const formStatus = document.querySelector('#form-status');
const previewImage = document.querySelector('#preview-image');
const previewEmpty = document.querySelector('#preview-empty');
const previewLoading = document.querySelector('#preview-loading');
const previewHeading = document.querySelector('#preview-heading');
const previewDetails = document.querySelector('#preview-details');
const libraryGrid = document.querySelector('#library-grid');
const libraryEmpty = document.querySelector('#library-empty');
const gifCount = document.querySelector('#gif-count');
const toast = document.querySelector('#toast');
let stagedPreview = null;
let toastTimer = null;

function setStatus(message = '', kind = '') {
  formStatus.textContent = message;
  formStatus.className = `status ${kind}`.trim();
}

function notify(message, isError = false) {
  toast.textContent = message;
  toast.className = `toast visible${isError ? ' error' : ''}`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { toast.className = 'toast'; }, 3500);
}

async function requestJson(path, payload) {
  const response = await fetch(path, {
    method: payload ? 'POST' : 'GET',
    headers: payload ? { 'Content-Type': 'application/json' } : undefined,
    body: payload ? JSON.stringify(payload) : undefined,
    cache: 'no-store',
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || `Request failed (${response.status}).`);
  return data;
}

function setPreviewBusy(busy) {
  previewButton.disabled = busy;
  previewButton.innerHTML = busy
    ? '<span class="spinner" aria-hidden="true"></span> Checking link…'
    : '<span class="button-icon" aria-hidden="true">⌕</span> Check &amp; preview';
  previewLoading.hidden = !busy;
  previewEmpty.hidden = busy || Boolean(stagedPreview);
  previewImage.hidden = busy || !stagedPreview;
}

function suggestName(value) {
  try {
    const path = new URL(value).pathname;
    const lastPart = decodeURIComponent(path.split('/').filter(Boolean).at(-1) || '');
    const name = lastPart.replace(/\.(gif|webp|png|jpe?g|apng)$/i, '').replace(/[-_]+/g, ' ').trim();
    return name && name.length < 80 ? name : '';
  } catch {
    return '';
  }
}

function clearPreview() {
  stagedPreview = null;
  publishButton.disabled = true;
  previewImage.removeAttribute('src');
  previewImage.hidden = true;
  previewEmpty.hidden = false;
  previewDetails.hidden = true;
  previewHeading.textContent = 'Your GIF will appear here';
}

urlField.addEventListener('input', () => {
  clearPreview();
  setStatus();
  const suggestion = suggestName(urlField.value);
  if (suggestion && !titleField.dataset.edited) titleField.value = suggestion;
});

titleField.addEventListener('input', () => {
  titleField.dataset.edited = 'true';
});

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  clearPreview();
  setStatus();
  setPreviewBusy(true);
  try {
    const result = await requestJson('/api/preview', { url: urlField.value.trim() });
    stagedPreview = result;
    previewImage.src = `${result.previewUrl}?v=${Date.now()}`;
    previewImage.hidden = false;
    previewEmpty.hidden = true;
    previewHeading.textContent = titleField.value.trim() || 'Ready to add';
    previewDetails.replaceChildren();
    const details = [
      result.animated ? `${result.frames} frames · animated` : 'Still image · converted to GIF',
      `${result.width} × ${result.height}`,
      `${(result.bytes / (1024 * 1024)).toFixed(2)} MB`,
    ];
    for (const detail of details) {
      const chip = document.createElement('span');
      chip.textContent = detail;
      if (detail.includes('animated')) chip.className = 'animated';
      previewDetails.append(chip);
    }
    previewDetails.hidden = false;
    publishButton.disabled = false;
    setStatus('Looks good. Review the preview, then add it to the catalog.', 'success');
  } catch (error) {
    clearPreview();
    setStatus(error.message, 'error');
  } finally {
    setPreviewBusy(false);
  }
});

publishButton.addEventListener('click', async () => {
  if (!stagedPreview) return;
  publishButton.disabled = true;
  publishButton.textContent = 'Saving…';
  setStatus();
  try {
    const result = await requestJson('/api/publish', {
      stageId: stagedPreview.stageId,
      title: titleField.value.trim(),
      category: categoryField.value.trim() || 'Other',
      categoryEmoji: categoryEmojiField.value.trim() || '🙂',
      tags: tagsField.value.split(',').map((tag) => tag.trim()).filter(Boolean),
      rightsConfirmed: rightsField.checked,
    });
    notify(`${result.title} is in your Dismod catalog.`);
    setStatus(`${result.filename} saved in community-assets/gifs. Push and merge the repo changes to publish it to users.`, 'success');
    form.reset();
    categoryField.value = 'Reactions';
    titleField.dataset.edited = '';
    clearPreview();
    await loadLibrary();
  } catch (error) {
    setStatus(error.message, 'error');
    notify(error.message, true);
  } finally {
    publishButton.innerHTML = 'Add to Dismod <span aria-hidden="true">→</span>';
    publishButton.disabled = !stagedPreview;
  }
});

function makeGifCard(gif) {
  const card = document.createElement('article');
  card.className = 'gif-card';
  const thumb = document.createElement('div');
  thumb.className = 'gif-thumb';
  const image = document.createElement('img');
  image.src = gif.localPreviewUrl || gif.previewUrl || gif.mediaUrl;
  image.alt = gif.title || 'Dismod GIF';
  image.loading = 'lazy';
  thumb.append(image);

  const content = document.createElement('div');
  content.className = 'gif-card-content';
  const title = document.createElement('p');
  title.className = 'gif-card-title';
  title.textContent = gif.title || 'Untitled GIF';
  const meta = document.createElement('div');
  meta.className = 'gif-card-meta';
  const category = document.createElement('span');
  category.textContent = gif.category || 'Other';
  const remove = document.createElement('button');
  remove.type = 'button';
  remove.className = 'delete-gif';
  remove.textContent = 'Remove';
  remove.dataset.gifId = gif.id;
  remove.setAttribute('aria-label', `Remove ${gif.title || 'GIF'}`);
  meta.append(category, remove);
  content.append(title, meta);
  card.append(thumb, content);
  return card;
}

async function loadLibrary() {
  try {
    const data = await requestJson('/api/catalog');
    const entries = Array.isArray(data.gifs) ? data.gifs : [];
    gifCount.textContent = entries.length;
    libraryGrid.replaceChildren(...entries.map(makeGifCard));
    libraryEmpty.hidden = entries.length > 0;
  } catch (error) {
    libraryGrid.replaceChildren();
    libraryEmpty.hidden = false;
    libraryEmpty.textContent = error.message;
  }
}

libraryGrid.addEventListener('click', async (event) => {
  const button = event.target.closest('.delete-gif');
  if (!button) return;
  const card = button.closest('.gif-card');
  const title = card?.querySelector('.gif-card-title')?.textContent || 'this GIF';
  if (!window.confirm(`Remove “${title}” from the Dismod catalog?`)) return;
  button.disabled = true;
  try {
    await requestJson('/api/delete', { id: button.dataset.gifId });
    notify(`${title} removed from the catalog.`);
    await loadLibrary();
  } catch (error) {
    button.disabled = false;
    notify(error.message, true);
  }
});

document.querySelector('#refresh-button').addEventListener('click', loadLibrary);
rightsField.addEventListener('change', () => {
  if (!rightsField.checked) setStatus('Confirm sharing permission before adding the GIF.');
});

loadLibrary();
