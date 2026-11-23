/* UrbanMove — utilidades comunes a las tres interfaces: sesión, API, formato y acceso. */
const UM = (() => {
  const app = document.body.dataset.app;
  const storageKey = `urbanmove.session.${app}`;
  let session = read();

  function read() {
    try { return JSON.parse(localStorage.getItem(storageKey)); } catch { return null; }
  }

  function store(value) {
    session = value;
    try {
      if (value) localStorage.setItem(storageKey, JSON.stringify(value));
      else localStorage.removeItem(storageKey);
    } catch { /* almacenamiento no disponible: la sesión vive en memoria */ }
  }

  async function refresh() {
    if (!session?.refreshToken) return false;
    try {
      const res = await fetch('/api/auth/refresh', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: session.refreshToken }),
      });
      if (!res.ok) return false;
      store(await res.json());
      return true;
    } catch { return false; }
  }

  async function api(path, { method = 'GET', body, auth = true, retry = true } = {}) {
    const headers = { 'Content-Type': 'application/json' };
    if (auth && session) headers.Authorization = `Bearer ${session.accessToken}`;
    const res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    if (res.status === 401 && auth && retry && await refresh()) return api(path, { method, body, auth, retry: false });
    if (res.status === 401 && auth) { store(null); location.reload(); throw new Error('La sesión ha caducado'); }
    if (res.status === 204) return null;
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = { message: text }; }
    if (!res.ok) {
      const error = new Error(data?.message || `Error ${res.status}`);
      error.status = res.status;
      error.code = data?.error;
      throw error;
    }
    return data;
  }

  // ------------------------------------------------------------------ formato
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  const STATES = {
    AVAILABLE: 'Disponible', RESERVED: 'Reservado', IN_USE: 'En uso', LOW_BATTERY: 'Batería baja',
    WITHDRAWN: 'Retirado', CHARGING: 'Cargando', REPAIRING: 'En reparación', READY_FOR_SERVICE: 'Listo',
    RETIRED: 'Baja', ACTIVE: 'Activa', COMPLETED: 'Completada', CANCELLED: 'Cancelada', EXPIRED: 'Expirada',
    OPEN: 'Abierta', IN_PROGRESS: 'En curso', CLOSED: 'Cerrada', CREATED: 'Creada', ASSIGNED: 'Asignada',
    RESOLVED: 'Resuelta', DEFICIT: 'Déficit', EXCESS: 'Exceso', OK: 'Equilibrada', UP: 'Activo', DOWN: 'Caído',
  };
  const TYPES = { BICYCLE: 'Bicicleta', ELECTRIC_BICYCLE: 'Bici eléctrica', ELECTRIC_SCOOTER: 'Patinete' };

  const state = (s) => `<span class="state ${esc(s)}">${esc(STATES[s] || s)}</span>`;
  const type = (t) => esc(TYPES[t] || t);
  const battery = (pct) => pct == null ? '<span class="muted">—</span>'
    : `<span class="battery"><i><b style="width:${Math.max(0, Math.min(100, pct)) * 0.22}px"></b></i>${pct}%</span>`;
  const money = (n) => new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' }).format(Number(n || 0));
  const num = (n, d = 0) => new Intl.NumberFormat('es-ES', { maximumFractionDigits: d, minimumFractionDigits: d }).format(Number(n || 0));
  const time = (iso) => iso ? new Date(iso).toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' }) : '—';
  const dateTime = (iso) => iso ? new Date(iso).toLocaleString('es-ES', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—';
  const duration = (seconds) => {
    if (seconds == null) return '—';
    const s = Math.max(0, Math.round(seconds));
    const m = Math.floor(s / 60);
    return m ? `${m} min ${String(s % 60).padStart(2, '0')} s` : `${s} s`;
  };
  const short = (id) => id ? String(id).slice(0, 8) : '—';

  // ------------------------------------------------------------------ avisos
  let toastTimer;
  function toast(message, kind = 'info') {
    let el = document.querySelector('.toast');
    if (!el) { el = document.createElement('div'); el.className = 'toast'; el.setAttribute('role', 'status'); document.body.append(el); }
    el.textContent = message;
    el.className = `toast show ${kind === 'error' ? 'error' : ''}`;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove('show'), 3600);
  }

  async function run(action, success) {
    try {
      const result = await action();
      if (success) toast(success);
      return result;
    } catch (e) {
      toast(e.message, 'error');
      return undefined;
    }
  }

  // ------------------------------------------------------------------ fondo y cabecera
  function backdrop() {
    const wrap = document.createElement('div');
    wrap.className = 'backdrop';
    wrap.innerHTML = '<video src="/shared/bg.mp4" autoplay muted loop playsinline preload="auto" aria-hidden="true"></video>';
    document.body.prepend(wrap);
  }

  function topbar(subtitle) {
    const bar = document.createElement('header');
    bar.className = 'topbar';
    bar.innerHTML = `<a class="wordmark" href="/">UrbanMove<small>${esc(subtitle)}</small></a><div class="who"></div>`;
    document.body.insertBefore(bar, document.querySelector('main'));
    if (session) {
      bar.querySelector('.who').innerHTML = `<span>${esc(session.user.fullName || session.user.username)}</span><button type="button">Salir</button>`;
      bar.querySelector('button').onclick = () => { store(null); location.reload(); };
    }
  }

  // ------------------------------------------------------------------ acceso
  function gate({ subtitle, headline, intro, roles, allowRegister, hint, start }) {
    backdrop();
    const allowed = (s) => s?.user?.roles?.some((r) => roles.includes(r));
    if (session && allowed(session)) {
      topbar(subtitle);
      start(session);
      return;
    }
    store(null);
    topbar(subtitle);
    const main = document.querySelector('main');
    let mode = 'login';
    const render = (error = '') => {
      main.innerHTML = `
        <section class="auth">
          <div class="intro"><h1 class="headline">${headline}</h1><p>${intro}</p></div>
          <form class="panel" novalidate>
            <h2>${mode === 'login' ? 'Iniciar sesión' : 'Crear cuenta'}</h2>
            ${error ? `<p class="error-line">${esc(error)}</p>` : ''}
            ${mode === 'register' ? `
              <label class="field"><span>Nombre y apellidos</span><input name="fullName" autocomplete="name" required></label>
              <label class="field"><span>Correo</span><input name="email" type="email" autocomplete="email" required></label>` : ''}
            <label class="field"><span>Usuario</span><input name="username" autocomplete="username" required></label>
            <label class="field"><span>Contraseña</span><input name="password" type="password" autocomplete="${mode === 'login' ? 'current-password' : 'new-password'}" required></label>
            <button class="btn block" type="submit">${mode === 'login' ? 'Entrar' : 'Registrarme'}</button>
            ${allowRegister ? `<p class="switch">${mode === 'login'
              ? '¿No tienes cuenta? <button type="button" data-mode="register">Regístrate</button>'
              : '¿Ya tienes cuenta? <button type="button" data-mode="login">Inicia sesión</button>'}</p>` : ''}
            ${hint ? `<p class="hint">${hint}</p>` : ''}
          </form>
        </section>`;
      main.querySelector('[data-mode]')?.addEventListener('click', (e) => { mode = e.target.dataset.mode; render(); });
      main.querySelector('form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const form = Object.fromEntries(new FormData(e.target));
        const button = e.target.querySelector('[type=submit]');
        button.disabled = true;
        try {
          const s = mode === 'login'
            ? await api('/api/auth/login', { method: 'POST', body: { username: form.username, password: form.password }, auth: false })
            : await api('/api/auth/register', { method: 'POST', body: form, auth: false });
          if (!allowed(s)) { render('Tu cuenta no tiene acceso a esta aplicación.'); return; }
          store(s);
          location.reload();
        } catch (err) {
          render(err.message);
        }
      });
      main.querySelector('input')?.focus();
    };
    render();
  }

  function tabs(container, names, onChange, initial) {
    const key = `urbanmove.tab.${app}`;
    let current = initial;
    try { current = localStorage.getItem(key) || initial; } catch { /* sin almacenamiento */ }
    if (!names.some(([id]) => id === current)) current = names[0][0];
    container.innerHTML = names.map(([id, label]) => `<button type="button" role="tab" data-tab="${id}" aria-selected="${id === current}">${label}</button>`).join('');
    container.onclick = (e) => {
      const id = e.target.dataset?.tab;
      if (!id) return;
      current = id;
      try { localStorage.setItem(key, id); } catch { /* sin almacenamiento */ }
      container.querySelectorAll('button').forEach((b) => b.setAttribute('aria-selected', String(b.dataset.tab === id)));
      onChange(id);
    };
    onChange(current);
  }

  return {
    api, run, toast, gate, tabs, esc, state, type, battery, money, num, time, dateTime, duration, short,
    STATES, TYPES, get session() { return session; },
    hasRole: (r) => session?.user?.roles?.includes(r),
  };
})();
