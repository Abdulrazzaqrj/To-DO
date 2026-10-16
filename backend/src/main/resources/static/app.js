const rawBase =
  (window.APP_CONFIG && window.APP_CONFIG.API_BASE_URL) || '';
const API = rawBase ? rawBase.replace(/\/+$/, '') + '/api/tasks' : '/api/tasks';

let tab = 'All';
let editId = null;
let all = [];
let tm;

const $ = id => document.getElementById(id);

const esc = value =>
  String(value ?? '').replace(/[&<>"']/g, char => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  })[char]);

function showConnBanner(msg) {
  const banner = $('conn-banner');
  if (!banner) return;
  banner.innerHTML = `
    <span>⚠️ <strong>${esc(msg)}</strong> If hosted on a free cloud service (like Render), it may take 45–60s to wake up from cold sleep.</span>
    <button type="button" onclick="load()">Retry Connection</button>
  `;
  banner.hidden = false;
}

function hideConnBanner() {
  const banner = $('conn-banner');
  if (banner) banner.hidden = true;
}

// API helper
async function api(url, method = 'GET', body) {
  let response;

  try {
    response = await fetch(url, {
      method,
      headers: {
        'Content-Type': 'application/json'
      },
      ...(body !== undefined
        ? { body: JSON.stringify(body) }
        : {})
    });
  } catch {
    throw new Error(
      'Cannot reach the server. Check that the backend is running and the API URL is correct.'
    );
  }

  const data = response.status === 204
    ? null
    : await response.json().catch(() => null);

  if (!response.ok) {
    throw new Error(
      data?.message ||
      data?.error ||
      `Request failed (${response.status})`
    );
  }

  return data;
}

// Load tasks from the backend
async function load() {
  const query = new URLSearchParams();

  if (tab === 'Pending' || tab === 'Completed') {
    query.set('status', tab);
  }

  if (tab === 'Overdue') {
    query.set('overdue', 'true');
  }

  const search = $('search')?.value.trim();

  if (search) {
    query.set('search', search);
  }

  try {
    all = await api(`${API}?${query.toString()}`);
    hideConnBanner();
    render();
  } catch (error) {
    console.warn('Backend load error:', error.message);
    showConnBanner(error.message);
    all = [];
    render();
  }
}

// Render task table
function render() {
  const priorityFilter = $('prio')?.value || '';
  const list = Array.isArray(all) ? all : [];

  const tasks = list.filter(task =>
    !priorityFilter || task.priority === priorityFilter
  );

  $('empty').hidden = tasks.length > 0;

  $('rows').innerHTML = tasks.map(task => {
    const completed = task.status === 'Completed';

    return `
      <tr class="${completed ? 'done' : ''} ${
        task.overdue ? 'late' : ''
      }">
        <td>
          <input
            type="checkbox"
            style="width:auto"
            ${completed ? 'checked' : ''}
            aria-label="Mark ${esc(task.title)} as completed"
            onchange="toggle(${Number(task.id)}, this.checked, this)"
          >
        </td>

        <td class="t">
          <b>${esc(task.title)}</b>
          <small>${esc(task.description)}</small>
        </td>

        <td>
          <span class="pill ${esc(task.priority)}">
            ${esc(task.priority)}
          </span>
        </td>

        <td>
          <span class="pill ${esc(task.status)}">
            ${esc(task.status)}
          </span>
          ${task.overdue ? ' ⚠️' : ''}
        </td>

        <td>${esc(task.deadline || '—')}</td>

        <td>
          <button
            type="button"
            class="ic ed"
            aria-label="Edit task"
            onclick="edit(${Number(task.id)})"
          >✏️</button>

          <button
            type="button"
            class="ic rm"
            aria-label="Delete task"
            onclick="del(${Number(task.id)})"
          >🗑️</button>
        </td>
      </tr>
    `;
  }).join('');
}

// Mark task completed or pending
async function toggle(id, checked, checkboxEl) {
  if (checkboxEl) checkboxEl.disabled = true;
  try {
    await api(`${API}/${id}/status`, 'PATCH', {
      status: checked ? 'Completed' : 'Pending'
    });

    await load();
  } catch (error) {
    alert(error.message);
    await load().catch(err => alert(err.message));
  } finally {
    if (checkboxEl) checkboxEl.disabled = false;
  }
}

// Delete task
async function del(id) {
  if (!confirm('Are you sure you want to delete this task?')) {
    return;
  }

  try {
    await api(`${API}/${id}`, 'DELETE');
    await load();
  } catch (error) {
    alert(error.message);
  }
}

// Open add/edit modal
function openModal(task = null) {
  editId = task?.id ?? null;

  $('mt').textContent = task ? 'Edit Task' : 'New Task';
  $('title').value = task?.title ?? '';
  $('desc').value = task?.description ?? '';
  $('deadline').value = task?.deadline ?? '';

  // This value must match the priority options and Java backend.
  $('priority').value = task?.priority ?? 'Medium';

  $('err').textContent = '';
  $('modal').classList.add('show');
  $('title').focus();
}

// Open edit modal
function edit(id) {
  const task = all.find(item => item.id === id);

  if (!task) {
    alert('Task not found. Please refresh the task list.');
    return;
  }

  openModal(task);
}

// Close modal
function closeModal() {
  $('modal').classList.remove('show');
  $('err').textContent = '';
  editId = null;
}

// Initialize controls
$('new').addEventListener('click', () => openModal());
$('cancel').addEventListener('click', closeModal);

// Close modal when clicking backdrop or pressing Escape
$('modal').addEventListener('click', event => {
  if (event.target === $('modal')) {
    closeModal();
  }
});

window.addEventListener('keydown', event => {
  if (event.key === 'Escape' && $('modal').classList.contains('show')) {
    closeModal();
  }
});

// Add or update task
$('form').addEventListener('submit', async event => {
  event.preventDefault();

  const title = $('title').value.trim();

  if (!title) {
    $('err').textContent = 'Title must not be empty.';
    $('title').focus();
    return;
  }

  const currentTask = all.find(task => task.id === editId);

  const body = {
    title,
    description: $('desc').value.trim(),
    deadline: $('deadline').value || null,
    priority: $('priority').value
  };

  if (!editId) {
    body.status = 'Pending';
  } else if (currentTask) {
    body.status = currentTask.status;
  }

  const submitBtn = $('form').querySelector('button[type="submit"]');
  const originalText = submitBtn.textContent;
  submitBtn.disabled = true;
  submitBtn.textContent = 'Saving...';

  try {
    await api(
      editId ? `${API}/${editId}` : API,
      editId ? 'PUT' : 'POST',
      body
    );

    closeModal();
    await load();
  } catch (error) {
    $('err').textContent = error.message;
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = originalText;
  }
});

// Switch task tabs
document.querySelectorAll('.tabs a').forEach(link => {
  link.addEventListener('click', event => {
    event.preventDefault();

    document.querySelector('.tabs .on')?.classList.remove('on');
    link.classList.add('on');

    tab = link.dataset.s || 'All';

    load().catch(error => alert(error.message));
  });
});

// Search with a short delay
$('search').addEventListener('input', () => {
  clearTimeout(tm);

  tm = setTimeout(() => {
    load().catch(error => alert(error.message));
  }, 300);
});

// Filter by priority
$('prio').addEventListener('change', render);

// Initial load
load();
