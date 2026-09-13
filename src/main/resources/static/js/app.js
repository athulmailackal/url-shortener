/* ==========================================================================
   Snapl — URL Shortener frontend logic
   Talks to the existing Spring Boot REST API using relative URLs only.
   ========================================================================== */

(function () {
  "use strict";

  const API_BASE = "/api/urls";
  const PAGE_SIZE = 10;

  const state = {
    page: 0,
    totalPages: 1,
    totalElements: 0,
    activeShortCode: null, // used by edit/delete modals
  };

  /* ---------------------------------------------------------------------
     Utilities
     --------------------------------------------------------------------- */

  const $ = (id) => document.getElementById(id);

  function escapeHtml(str) {
    if (str === null || str === undefined) return "";
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function formatDateTime(value) {
    if (!value) return "Never";
    const d = new Date(value);
    if (isNaN(d.getTime())) return value;
    return d.toLocaleString(undefined, {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  }

  // The backend's `shortUrl` field is sometimes returned as a relative path
  // (e.g. "/peepu") rather than a full absolute URL. Normalize it to an
  // absolute URL against the current origin so copy/open/display always work,
  // without ever changing what the backend actually sends.
  function resolveShortUrl(item) {
    const raw = (item && item.shortUrl) || "";
    if (/^https?:\/\//i.test(raw)) return raw;

    const path = (raw || item.shortCode || "").replace(/^\/+/, "");
    return window.location.origin + "/" + path;
  }

  function isExpired(expiresAt) {
    if (!expiresAt) return false;
    const d = new Date(expiresAt);
    if (isNaN(d.getTime())) return false;
    return d.getTime() < Date.now();
  }

  // Converts an ISO datetime string to the value expected by
  // <input type="datetime-local"> (YYYY-MM-DDTHH:mm), in local time.
  function toDatetimeLocalValue(isoString) {
    if (!isoString) return "";
    const d = new Date(isoString);
    if (isNaN(d.getTime())) return "";
    const pad = (n) => String(n).padStart(2, "0");
    return (
      d.getFullYear() +
      "-" +
      pad(d.getMonth() + 1) +
      "-" +
      pad(d.getDate()) +
      "T" +
      pad(d.getHours()) +
      ":" +
      pad(d.getMinutes())
    );
  }

  // Converts a <input type="datetime-local"> value to a full ISO string
  // (with seconds) suitable for the backend's LocalDateTime binding.
  function fromDatetimeLocalValue(value) {
    if (!value) return null;
    return value.length === 16 ? value + ":00" : value;
  }

  function setLoading(btn, loading) {
    if (!btn) return;
    btn.classList.toggle("is-loading", loading);
    btn.disabled = loading;
  }

  function showFieldError(inputEl, errorEl, message) {
    if (message) {
      inputEl.classList.add("has-error");
      errorEl.textContent = message;
      errorEl.classList.add("show");
    } else {
      inputEl.classList.remove("has-error");
      errorEl.textContent = "";
      errorEl.classList.remove("show");
    }
  }

  function clearFormErrors() {
    showFieldError($("url-input"), $("url-error"), "");
    showFieldError($("code-input"), $("code-error"), "");
    showFieldError($("expiry-input"), $("expiry-error"), "");
    const msg = $("form-msg");
    msg.textContent = "";
    msg.classList.remove("show", "is-error");
  }

  function showFormMessage(message) {
    const msg = $("form-msg");
    msg.textContent = message;
    msg.classList.add("show", "is-error");
  }

  /* ---------------------------------------------------------------------
     Toasts
     --------------------------------------------------------------------- */

  function toast(message, type) {
    const stack = $("toast-stack");
    const el = document.createElement("div");
    el.className = "toast " + (type === "error" ? "error" : "success");

    const icon =
      type === "error"
        ? '<svg class="toast-icon" width="16" height="16" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2"/><path d="M12 8v5M12 16h.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>'
        : '<svg class="toast-icon" width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M5 13l4 4L19 7" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>';

    el.innerHTML = icon + "<span>" + escapeHtml(message) + "</span>";
    stack.appendChild(el);

    setTimeout(() => {
      el.style.opacity = "0";
      el.style.transition = "opacity 0.2s ease";
      setTimeout(() => el.remove(), 200);
    }, 4000);
  }

  /* ---------------------------------------------------------------------
     API layer
     --------------------------------------------------------------------- */

  // Parses the backend's standard error shape: { status, message, timestamp }
  async function parseErrorResponse(response) {
    let message = null;
    try {
      const data = await response.json();
      if (data && data.message) message = data.message;
    } catch (e) {
      /* body wasn't JSON — fall back to status-based message below */
    }

    if (!message) {
      if (response.status === 404) message = "Short URL not found.";
      else if (response.status === 410) message = "This short URL has expired.";
      else if (response.status === 400) message = "That request couldn't be processed.";
      else message = "Something went wrong. Please try again.";
    }

    const err = new Error(message);
    err.status = response.status;
    return err;
  }

  async function apiRequest(path, options) {
    let response;
    try {
      response = await fetch(path, options);
    } catch (networkError) {
      const err = new Error("Couldn't reach the server. Check your connection and try again.");
      err.status = 0;
      throw err;
    }

    if (!response.ok) {
      throw await parseErrorResponse(response);
    }

    if (response.status === 204) return null;

    const contentType = response.headers.get("content-type") || "";
    if (contentType.includes("application/json")) {
      return response.json();
    }
    return null;
  }

  function createUrl(payload) {
    return apiRequest(API_BASE, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
  }

  function fetchUrlList(page) {
    return apiRequest(`${API_BASE}?page=${page}&size=${PAGE_SIZE}`, { method: "GET" });
  }

  function fetchStats(shortCode) {
    return apiRequest(`${API_BASE}/${encodeURIComponent(shortCode)}/stats`, { method: "GET" });
  }

  function deleteUrl(shortCode) {
    return apiRequest(`${API_BASE}/${encodeURIComponent(shortCode)}`, { method: "DELETE" });
  }

  function updateExpiration(shortCode, expiresAt) {
    return apiRequest(`${API_BASE}/${encodeURIComponent(shortCode)}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ expiresAt }),
    });
  }

  /* ---------------------------------------------------------------------
     Shorten form
     --------------------------------------------------------------------- */

  function validateForm(url, shortCode) {
    let valid = true;

    if (!url) {
      showFieldError($("url-input"), $("url-error"), "Please enter a URL.");
      valid = false;
    } else if (!/^https?:\/\//i.test(url)) {
      showFieldError($("url-input"), $("url-error"), "URL must start with http:// or https://");
      valid = false;
    }

    if (shortCode) {
      if (!/^[a-zA-Z0-9_-]+$/.test(shortCode)) {
        showFieldError(
          $("code-input"),
          $("code-error"),
          "Only letters, numbers, hyphens and underscores are allowed."
        );
        valid = false;
      } else if (shortCode.toLowerCase() === "api") {
        showFieldError($("code-input"), $("code-error"), '"api" cannot be used as a short code.');
        valid = false;
      }
    }

    return valid;
  }

  function renderResult(data) {
    const shortUrl = resolveShortUrl(data);
    $("result-short-url").href = shortUrl;
    $("result-short-url").textContent = shortUrl;
    $("result-original-url").textContent = data.originalUrl;
    $("result-original-url").title = data.originalUrl;
    $("result-clicks").textContent = data.clickCount ?? 0;
    $("result-expires").textContent = formatDateTime(data.expiresAt);

    $("result-stats-btn").dataset.shortCode = data.shortCode;
    $("result-copy").dataset.url = shortUrl;

    $("result-card").hidden = false;
    $("result-card").scrollIntoView({ behavior: "smooth", block: "nearest" });
  }

  async function handleShortenSubmit(event) {
    event.preventDefault();
    clearFormErrors();

    const url = $("url-input").value.trim();
    const shortCode = $("code-input").value.trim();
    const expiresAtRaw = $("expiry-input").value;

    if (!validateForm(url, shortCode)) return;

    const payload = { url };
    if (shortCode) payload.shortCode = shortCode;
    if (expiresAtRaw) payload.expiresAt = fromDatetimeLocalValue(expiresAtRaw);

    const btn = $("shorten-btn");
    setLoading(btn, true);

    try {
      const data = await createUrl(payload);
      renderResult(data);
      $("shorten-form").reset();
      toast("Short link created.", "success");
      loadUrlList(0);
    } catch (err) {
      if (err.status === 400) {
        showFormMessage(err.message);
      } else {
        showFormMessage(err.message);
      }
    } finally {
      setLoading(btn, false);
    }
  }

  async function copyToClipboard(text, feedbackBtn) {
    try {
      await navigator.clipboard.writeText(text);
    } catch (e) {
      // Fallback for browsers/contexts without Clipboard API access
      const textarea = document.createElement("textarea");
      textarea.value = text;
      textarea.style.position = "fixed";
      textarea.style.opacity = "0";
      document.body.appendChild(textarea);
      textarea.select();
      try {
        document.execCommand("copy");
      } catch (fallbackErr) {
        toast("Couldn't copy automatically — copy it manually.", "error");
        document.body.removeChild(textarea);
        return;
      }
      document.body.removeChild(textarea);
    }

    if (feedbackBtn) {
      const label = feedbackBtn.querySelector("span:last-child");
      const original = label ? label.textContent : null;
      if (label) label.textContent = "Copied!";
      feedbackBtn.disabled = true;
      setTimeout(() => {
        if (label && original) label.textContent = original;
        feedbackBtn.disabled = false;
      }, 1600);
    } else {
      toast("Copied to clipboard.", "success");
    }
  }

  /* ---------------------------------------------------------------------
     Dashboard / URL list
     --------------------------------------------------------------------- */

  function renderTableRow(item) {
    const expired = isExpired(item.expiresAt);
    const statusHtml = expired
      ? '<span class="status-pill expired">Expired</span>'
      : '<span class="status-pill active">Active</span>';

    const shortUrl = resolveShortUrl(item);
    const originalUrl = item.originalUrl || "";

    return `
      <tr data-short-code="${escapeHtml(item.shortCode)}">
        <td class="cell-short" data-label="Short link">
          <a href="${escapeHtml(shortUrl)}" target="_blank" rel="noopener">${escapeHtml(shortUrl)}</a>
        </td>
        <td data-label="Original URL">
          <span class="cell-original" title="${escapeHtml(originalUrl)}">${escapeHtml(originalUrl)}</span>
        </td>
        <td data-label="Clicks">${escapeHtml(item.clickCount ?? 0)}</td>
        <td data-label="Status">${statusHtml}</td>
        <td data-label="Expires">${escapeHtml(formatDateTime(item.expiresAt))}</td>
        <td class="td-actions" data-label="Actions">
          <div class="row-actions">
            <button class="icon-btn action-copy" data-url="${escapeHtml(shortUrl)}" title="Copy" aria-label="Copy short URL">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none"><rect x="9" y="9" width="11" height="11" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M5 15V6a2 2 0 012-2h9" stroke="currentColor" stroke-width="1.8"/></svg>
            </button>
            <button class="icon-btn action-stats" data-short-code="${escapeHtml(item.shortCode)}" title="Statistics" aria-label="View statistics">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none"><path d="M4 20V10M12 20V4M20 20v-7" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
            </button>
            <button class="icon-btn action-edit" data-short-code="${escapeHtml(item.shortCode)}" data-expires-at="${escapeHtml(item.expiresAt || "")}" title="Edit expiration" aria-label="Edit expiration">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none"><path d="M12 20h9" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><path d="M16.5 3.5a2.12 2.12 0 013 3L7 19l-4 1 1-4L16.5 3.5z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg>
            </button>
            <button class="icon-btn action-delete" data-short-code="${escapeHtml(item.shortCode)}" title="Delete" aria-label="Delete">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
            </button>
          </div>
        </td>
      </tr>
    `;
  }

  async function loadUrlList(page) {
    $("table-loading").hidden = false;
    $("empty-state").hidden = true;
    $("link-table").hidden = true;
    $("pagination").hidden = true;

    try {
      const data = await fetchUrlList(page);
      const content = data.content || [];

      state.page = data.page ?? page;
      state.totalPages = Math.max(data.totalPages ?? 1, 1);
      state.totalElements = data.totalElements ?? content.length;

      $("table-loading").hidden = true;

      if (content.length === 0) {
        $("empty-state").hidden = false;
        return;
      }

      $("link-table-body").innerHTML = content.map(renderTableRow).join("");
      $("link-table").hidden = false;

      $("page-current").textContent = state.page + 1;
      $("page-total").textContent = state.totalPages;
      $("page-total-elements").textContent = state.totalElements;
      $("prev-page").disabled = state.page <= 0;
      $("next-page").disabled = state.page >= state.totalPages - 1;
      $("pagination").hidden = false;
    } catch (err) {
      $("table-loading").hidden = true;
      $("empty-state").hidden = false;
      $("empty-state").querySelector(".empty-title").textContent = "Couldn't load your links.";
      $("empty-state").querySelector(".empty-sub").textContent = err.message;
      toast(err.message, "error");
    }
  }

  /* ---------------------------------------------------------------------
     Statistics modal
     --------------------------------------------------------------------- */

  function renderStats(data) {
    return `
      <div class="stat-grid">
        <div class="stat-item highlight full">
          <dt>Total clicks</dt>
          <dd>${escapeHtml(data.clickCount ?? 0)}</dd>
        </div>
        <div class="stat-item full">
          <dt>Short code</dt>
          <dd>${escapeHtml(data.shortCode)}</dd>
        </div>
        <div class="stat-item full">
          <dt>Short URL</dt>
          <dd>${escapeHtml(resolveShortUrl(data))}</dd>
        </div>
        <div class="stat-item full">
          <dt>Original URL</dt>
          <dd>${escapeHtml(data.originalUrl)}</dd>
        </div>
        <div class="stat-item">
          <dt>Created</dt>
          <dd>${escapeHtml(formatDateTime(data.createdAt))}</dd>
        </div>
        <div class="stat-item">
          <dt>Last clicked</dt>
          <dd>${escapeHtml(formatDateTime(data.lastClickedAt))}</dd>
        </div>
        <div class="stat-item full">
          <dt>Expires</dt>
          <dd>${escapeHtml(formatDateTime(data.expiresAt))}</dd>
        </div>
      </div>
    `;
  }

  async function openStatsModal(shortCode) {
    $("stats-modal").hidden = false;
    $("stats-body").innerHTML = '<div class="table-loading"><span class="spin"></span> Loading statistics…</div>';

    try {
      const data = await fetchStats(shortCode);
      $("stats-body").innerHTML = renderStats(data);
    } catch (err) {
      $("stats-body").innerHTML = `<p class="modal-hint">${escapeHtml(err.message)}</p>`;
    }
  }

  function closeStatsModal() {
    $("stats-modal").hidden = true;
  }

  /* ---------------------------------------------------------------------
     Edit expiration modal
     --------------------------------------------------------------------- */

  function openEditModal(shortCode, currentExpiresAt) {
    state.activeShortCode = shortCode;
    $("edit-code-hint").textContent = `Short code: ${shortCode}`;
    $("edit-expiry-input").value = toDatetimeLocalValue(currentExpiresAt);
    showFieldError($("edit-expiry-input"), $("edit-expiry-error"), "");
    $("edit-modal").hidden = false;
  }

  function closeEditModal() {
    $("edit-modal").hidden = true;
    state.activeShortCode = null;
  }

  async function handleEditSave() {
    const value = $("edit-expiry-input").value;
    showFieldError($("edit-expiry-input"), $("edit-expiry-error"), "");

    if (!value) {
      showFieldError($("edit-expiry-input"), $("edit-expiry-error"), "Please choose a date and time.");
      return;
    }

    const isoValue = fromDatetimeLocalValue(value);
    if (new Date(isoValue).getTime() < Date.now()) {
      showFieldError($("edit-expiry-input"), $("edit-expiry-error"), "Expiration must not be in the past.");
      return;
    }

    const btn = $("edit-save");
    setLoading(btn, true);
    try {
      await updateExpiration(state.activeShortCode, isoValue);
      toast("Expiration updated.", "success");
      closeEditModal();
      loadUrlList(state.page);
    } catch (err) {
      showFieldError($("edit-expiry-input"), $("edit-expiry-error"), err.message);
    } finally {
      setLoading(btn, false);
    }
  }

  /* ---------------------------------------------------------------------
     Delete confirmation modal
     --------------------------------------------------------------------- */

  function openDeleteModal(shortCode) {
    state.activeShortCode = shortCode;
    $("delete-code-hint").textContent = shortCode;
    $("delete-modal").hidden = false;
  }

  function closeDeleteModal() {
    $("delete-modal").hidden = true;
    state.activeShortCode = null;
  }

  async function handleDeleteConfirm() {
    const btn = $("delete-confirm");
    setLoading(btn, true);
    try {
      await deleteUrl(state.activeShortCode);
      toast("Link deleted.", "success");
      closeDeleteModal();
      // If we just deleted the last item on this page, step back a page.
      const remainingOnPage = $("link-table-body").querySelectorAll("tr").length - 1;
      const targetPage = remainingOnPage <= 0 && state.page > 0 ? state.page - 1 : state.page;
      loadUrlList(targetPage);
    } catch (err) {
      toast(err.message, "error");
      closeDeleteModal();
    } finally {
      setLoading(btn, false);
    }
  }

  /* ---------------------------------------------------------------------
     Event wiring
     --------------------------------------------------------------------- */

  function initShortenForm() {
    $("shorten-form").addEventListener("submit", handleShortenSubmit);

    $("result-close").addEventListener("click", () => {
      $("result-card").hidden = true;
    });

    $("result-copy").addEventListener("click", (e) => {
      const url = e.currentTarget.dataset.url;
      copyToClipboard(url, e.currentTarget);
    });

    $("result-stats-btn").addEventListener("click", (e) => {
      const shortCode = e.currentTarget.dataset.shortCode;
      if (shortCode) openStatsModal(shortCode);
    });

    // Clear individual field errors as the user types
    $("url-input").addEventListener("input", () => showFieldError($("url-input"), $("url-error"), ""));
    $("code-input").addEventListener("input", () => showFieldError($("code-input"), $("code-error"), ""));
  }

  function initDashboard() {
    $("refresh-btn").addEventListener("click", () => loadUrlList(state.page));

    $("prev-page").addEventListener("click", () => {
      if (state.page > 0) loadUrlList(state.page - 1);
    });
    $("next-page").addEventListener("click", () => {
      if (state.page < state.totalPages - 1) loadUrlList(state.page + 1);
    });

    // Event delegation for row actions
    $("link-table-body").addEventListener("click", (e) => {
      const copyBtn = e.target.closest(".action-copy");
      if (copyBtn) {
        copyToClipboard(copyBtn.dataset.url, null);
        toast("Short link copied.", "success");
        return;
      }

      const statsBtn = e.target.closest(".action-stats");
      if (statsBtn) {
        openStatsModal(statsBtn.dataset.shortCode);
        return;
      }

      const editBtn = e.target.closest(".action-edit");
      if (editBtn) {
        openEditModal(editBtn.dataset.shortCode, editBtn.dataset.expiresAt);
        return;
      }

      const deleteBtn = e.target.closest(".action-delete");
      if (deleteBtn) {
        openDeleteModal(deleteBtn.dataset.shortCode);
        return;
      }
    });
  }

  function initModals() {
    $("stats-close").addEventListener("click", closeStatsModal);
    $("stats-modal").addEventListener("click", (e) => {
      if (e.target === $("stats-modal")) closeStatsModal();
    });

    $("edit-close").addEventListener("click", closeEditModal);
    $("edit-cancel").addEventListener("click", closeEditModal);
    $("edit-save").addEventListener("click", handleEditSave);
    $("edit-modal").addEventListener("click", (e) => {
      if (e.target === $("edit-modal")) closeEditModal();
    });

    $("delete-close").addEventListener("click", closeDeleteModal);
    $("delete-cancel").addEventListener("click", closeDeleteModal);
    $("delete-confirm").addEventListener("click", handleDeleteConfirm);
    $("delete-modal").addEventListener("click", (e) => {
      if (e.target === $("delete-modal")) closeDeleteModal();
    });

    document.addEventListener("keydown", (e) => {
      if (e.key !== "Escape") return;
      if (!$("stats-modal").hidden) closeStatsModal();
      if (!$("edit-modal").hidden) closeEditModal();
      if (!$("delete-modal").hidden) closeDeleteModal();
    });
  }

  function init() {
    initShortenForm();
    initDashboard();
    initModals();
    loadUrlList(0);
  }

  document.addEventListener("DOMContentLoaded", init);
})();
