/* ============================================================
   app.js — BuddyAI Agent Admin Global JS
   ============================================================ */

// ---- Theme ----
function initTheme() {
  const saved = localStorage.getItem('theme') || 'light';
  document.documentElement.setAttribute('data-theme', saved);
  const btn = document.getElementById('theme-toggle');
  if (btn) btn.innerHTML = saved === 'dark' ? '<i class="bi bi-sun"></i>' : '<i class="bi bi-moon"></i>';
}

function toggleTheme() {
  const current = document.documentElement.getAttribute('data-theme');
  const next = current === 'dark' ? 'light' : 'dark';
  document.documentElement.setAttribute('data-theme', next);
  localStorage.setItem('theme', next);
  const btn = document.getElementById('theme-toggle');
  if (btn) btn.innerHTML = next === 'dark' ? '<i class="bi bi-sun"></i>' : '<i class="bi bi-moon"></i>';
}

// ---- Sidebar toggle for mobile ----
function toggleSidebar() {
  const sidebar = document.querySelector('.sidebar');
  if (sidebar) sidebar.classList.toggle('open');
}

// Close sidebar when clicking outside on mobile
document.addEventListener('click', function (e) {
  if (window.innerWidth > 768) return;
  const sidebar = document.querySelector('.sidebar');
  const toggleBtn = document.getElementById('sidebar-toggle');
  if (sidebar && sidebar.classList.contains('open')) {
    if (!sidebar.contains(e.target) && e.target !== toggleBtn && !toggleBtn?.contains(e.target)) {
      sidebar.classList.remove('open');
    }
  }
});

// ---- Active nav item ----
function setActiveNav() {
  const path = window.location.pathname;
  document.querySelectorAll('.nav-item').forEach(function (item) {
    item.classList.remove('active');
    const href = item.getAttribute('href');
    if (href && href !== '#' && (path === href || path.startsWith(href + '/'))) {
      item.classList.add('active');
    }
  });
}

// ---- Toast notification ----
function showToast(message, type) {
  type = type || 'info';
  const icons = { success: 'check-circle-fill', error: 'x-circle-fill', warning: 'exclamation-triangle-fill', info: 'info-circle-fill' };
  const colors = {
    success: 'var(--color-success)',
    error: 'var(--color-danger)',
    warning: 'var(--color-warning)',
    info: 'var(--color-info)'
  };

  // Remove any existing toasts of same type
  document.querySelectorAll('.toast-notification').forEach(function (t) { t.remove(); });

  const toast = document.createElement('div');
  toast.className = 'toast-notification toast-' + type;
  toast.innerHTML = '<i class="bi bi-' + (icons[type] || 'info-circle-fill') + '"></i> ' + message;
  Object.assign(toast.style, {
    position: 'fixed',
    bottom: '24px',
    right: '24px',
    background: colors[type] || colors.info,
    color: 'white',
    padding: '11px 18px',
    borderRadius: 'var(--radius-md)',
    boxShadow: 'var(--shadow-lg)',
    zIndex: '9999',
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    fontSize: 'var(--text-sm)',
    fontFamily: 'var(--font-sans)',
    fontWeight: '500',
    animation: 'toastSlideUp 0.3s ease',
    maxWidth: '360px',
    wordBreak: 'break-word'
  });
  document.body.appendChild(toast);
  setTimeout(function () {
    toast.style.animation = 'toastSlideDown 0.25s ease forwards';
    setTimeout(function () { toast.remove(); }, 250);
  }, 3500);
}

// ---- API helper ----
async function apiCall(url, options) {
  options = options || {};
  const sessionId = localStorage.getItem('adminSessionId') || crypto.randomUUID();
  localStorage.setItem('adminSessionId', sessionId);
  const defaults = {
    headers: Object.assign({
      'Content-Type': 'application/json',
      'X-Session-Id': sessionId,
      'X-User-Id': 'admin'
    }, options.headers || {})
  };
  const mergedOptions = Object.assign({}, defaults, options);
  mergedOptions.headers = Object.assign({}, defaults.headers, options.headers || {});
  const response = await fetch(url, mergedOptions);
  if (!response.ok) {
    const errText = await response.text().catch(function () { return ''; });
    throw new Error('HTTP ' + response.status + (errText ? ': ' + errText : ''));
  }
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) return response.json();
  return response.text();
}

// ---- Confirm dialog ----
function confirmAction(message) {
  return confirm(message);
}

// ---- Copy to clipboard ----
function copyToClipboard(text) {
  if (navigator.clipboard && window.isSecureContext) {
    navigator.clipboard.writeText(text).then(function () { showToast('Copied to clipboard', 'success'); });
  } else {
    const el = document.createElement('textarea');
    el.value = text;
    el.style.position = 'fixed';
    el.style.opacity = '0';
    document.body.appendChild(el);
    el.select();
    document.execCommand('copy');
    el.remove();
    showToast('Copied to clipboard', 'success');
  }
}

// ---- Format helpers ----
function formatDate(dateStr) {
  if (!dateStr) return '-';
  return new Date(dateStr).toLocaleString(undefined, { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function formatDateShort(dateStr) {
  if (!dateStr) return '-';
  return new Date(dateStr).toLocaleDateString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function formatDuration(ms) {
  if (!ms && ms !== 0) return '-';
  if (ms < 1000) return ms + 'ms';
  if (ms < 60000) return (ms / 1000).toFixed(1) + 's';
  return Math.floor(ms / 60000) + 'm ' + Math.round((ms % 60000) / 1000) + 's';
}

function truncate(str, len) {
  len = len || 60;
  if (!str) return '-';
  return str.length > len ? str.substring(0, len) + '…' : str;
}

// ---- Modal helpers ----
function openModal(id) {
  const overlay = document.getElementById(id);
  if (overlay) {
    overlay.classList.add('open');
    overlay.querySelector('.modal')?.focus();
  }
}

function closeModal(id) {
  const overlay = document.getElementById(id);
  if (overlay) overlay.classList.remove('open');
}

// Close modal on overlay click
document.addEventListener('click', function (e) {
  if (e.target.classList.contains('modal-overlay')) {
    e.target.classList.remove('open');
  }
});

// Close modal on Escape
document.addEventListener('keydown', function (e) {
  if (e.key === 'Escape') {
    document.querySelectorAll('.modal-overlay.open').forEach(function (m) { m.classList.remove('open'); });
  }
});

// ---- Export CSV ----
function exportTableCSV(tableId, filename) {
  const table = document.getElementById(tableId);
  if (!table) return;
  const rows = Array.from(table.querySelectorAll('tr'));
  const csvLines = rows.map(function (row) {
    return Array.from(row.querySelectorAll('th, td'))
      .map(function (cell) {
        return '"' + cell.innerText.replace(/"/g, '""').trim() + '"';
      })
      .join(',');
  });
  const blob = new Blob([csvLines.join('\n')], { type: 'text/csv' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = (filename || 'export') + '_' + new Date().toISOString().slice(0, 10) + '.csv';
  a.click();
  URL.revokeObjectURL(url);
  showToast('CSV exported', 'success');
}

// ---- Auto-resize textarea ----
function autoResize(el) {
  el.style.height = 'auto';
  el.style.height = Math.min(el.scrollHeight, 200) + 'px';
}

// ---- Initialize ----
document.addEventListener('DOMContentLoaded', function () {
  initTheme();
  setActiveNav();

  // Auto-resize textareas
  document.querySelectorAll('textarea[data-autoresize]').forEach(function (el) {
    el.addEventListener('input', function () { autoResize(el); });
  });
});

// ---- Add keyframe animations ----
(function () {
  const style = document.createElement('style');
  style.textContent = [
    '@keyframes toastSlideUp { from { opacity:0; transform: translateY(16px); } to { opacity:1; transform: translateY(0); } }',
    '@keyframes toastSlideDown { from { opacity:1; transform: translateY(0); } to { opacity:0; transform: translateY(12px); } }'
  ].join('\n');
  document.head.appendChild(style);
})();
