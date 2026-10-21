/* Northbridge Corporate Payments — progressive enhancement helpers */
(function () {
  'use strict';

  // Sidebar toggle on mobile
  document.addEventListener('click', function (e) {
    var toggle = e.target.closest('[data-menu-toggle]');
    if (toggle) {
      var sidebar = document.querySelector('.sidebar');
      if (sidebar) sidebar.classList.toggle('open');
      return;
    }
    var sidebar = document.querySelector('.sidebar');
    if (sidebar && sidebar.classList.contains('open') && !e.target.closest('.sidebar')) {
      sidebar.classList.remove('open');
    }
  });

  // User dropdown
  document.addEventListener('click', function (e) {
    var chip = e.target.closest('[data-user-menu]');
    if (chip) {
      var dd = document.querySelector('.user-dropdown');
      if (dd) dd.classList.toggle('open');
      return;
    }
    if (!e.target.closest('.user-dropdown')) {
      document.querySelectorAll('.user-dropdown').forEach(function (d) { d.classList.remove('open'); });
    }
  });

  // Tab switching (client-side only for section tabs)
  document.addEventListener('click', function (e) {
    var tab = e.target.closest('[data-tab]');
    if (!tab) return;
    e.preventDefault();
    var container = tab.closest('[data-tabs]');
    container.querySelectorAll('[data-tab]').forEach(function (t) { t.classList.remove('active'); });
    container.querySelectorAll('[data-tab-panel]').forEach(function (p) { p.hidden = true; });
    tab.classList.add('active');
    var panel = document.getElementById(tab.getAttribute('data-tab'));
    if (panel) panel.hidden = false;
  });

  // Confirm dialogs for destructive workflow actions
  document.addEventListener('submit', function (e) {
    var form = e.target;
    var confirmText = form.getAttribute('data-confirm');
    if (confirmText && !window.confirm(confirmText)) {
      e.preventDefault();
    }
  });

  // Show-created-API-key banner copy button
  document.addEventListener('click', function (e) {
    var btn = e.target.closest('[data-copy]');
    if (!btn) return;
    var text = btn.getAttribute('data-copy');
    if (navigator.clipboard && text) {
      navigator.clipboard.writeText(text).then(function () {
        var original = btn.textContent;
        btn.textContent = 'Copied';
        setTimeout(function () { btn.textContent = original; }, 1500);
      });
    }
  });

  // Bulk upload: live preview of the CSV being uploaded
  var bulkFile = document.querySelector('[data-bulk-file-input]');
  var bulkText = document.querySelector('[data-bulk-textarea]');
  if (bulkFile && bulkText) {
    bulkFile.addEventListener('change', function () {
      var file = bulkFile.files[0];
      if (!file) return;
      var reader = new FileReader();
      reader.onload = function () {
        bulkText.value = reader.result;
        bulkText.dispatchEvent(new Event('input'));
      };
      reader.readAsText(file);
    });
  }
  var bulkPreview = document.querySelector('[data-bulk-preview]');
  if (bulkText && bulkPreview) {
    var render = function () {
      var rows = bulkText.value.split(/\r?\n/).filter(function (r) { return r.trim() && r.trim().charAt(0) !== '#'; });
      var html = '';
      if (rows.length === 0) {
        html = '<p class="small muted mb-0">Paste a CSV or choose a file to see the line preview.</p>';
      } else if (rows[0].trim().indexOf('H,') !== 0) {
        html = '<p class="small mb-0" style="color:var(--danger)">The first line must be the header: H,&lt;declaredTotal&gt;,&lt;lineCount&gt;</p>';
      } else {
        html = '<p class="small muted mb-0">' + (rows.length - 1) + ' detail line(s) detected.</p>';
      }
      bulkPreview.innerHTML = html;
    };
    bulkText.addEventListener('input', render);
    render();
  }
})();
