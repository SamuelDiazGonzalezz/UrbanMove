/* App de operaciones y mantenimiento. */
UM.gate({
  subtitle: 'Operaciones',
  headline: 'La flota, en la calle y en el taller.',
  intro: 'Estado de cada vehículo, órdenes de mantenimiento y de recolocación, incidencias y telemetría.',
  roles: ['MAINTENANCE', 'ADMIN'],
  allowRegister: false,
  hint: 'Cuenta de prueba: operador / urbanmove',
  start,
});

const ctx = { zones: [], vehicles: [] };
const dialog = document.getElementById('dialog');

function start() {
  const main = document.querySelector('main');
  main.innerHTML = `<nav class="tabs" role="tablist"></nav><section id="view"></section>`;
  const view = main.querySelector('#view');
  const tabs = [['flota', 'Flota'], ['mantenimiento', 'Mantenimiento'], ['incidencias', 'Incidencias'],
    ['recolocacion', 'Recolocación'], ['telemetria', 'Telemetría'], ['eventos', 'Eventos']];
  if (UM.hasRole('ADMIN')) tabs.push(['inventario', 'Inventario'], ['usuarios', 'Usuarios']);
  const views = { flota, mantenimiento, incidencias, recolocacion, telemetria, eventos, inventario, usuarios };
  let stop = () => {};
  UM.tabs(main.querySelector('.tabs'), tabs, async (tab) => {
    stop();
    view.innerHTML = '<div class="panel"><p class="empty">Cargando…</p></div>';
    await loadContext();
    stop = views[tab](view) || (() => {});
  }, 'flota');
}

async function loadContext() {
  try {
    [ctx.zones, ctx.vehicles] = await Promise.all([UM.api('/api/zones', { auth: false }), UM.api('/api/availability')]);
  } catch (e) { UM.toast(e.message, 'error'); }
}

const zoneName = (id) => ctx.zones.find((z) => z.zoneId === id)?.name || id || '—';
const code = (id) => ctx.vehicles.find((v) => v.vehicleId === String(id))?.code || UM.short(id);
const zoneOptions = (selected = '') => ctx.zones.map((z) => `<option value="${z.zoneId}" ${z.zoneId === selected ? 'selected' : ''}>${UM.esc(z.name)}</option>`).join('');
const vehicleOptions = (filter = () => true) => ctx.vehicles.filter((v) => v.state !== 'RETIRED').filter(filter)
  .sort((a, b) => a.code.localeCompare(b.code))
  .map((v) => `<option value="${v.vehicleId}">${UM.esc(v.code)} · ${UM.STATES[v.state]}${v.batteryPct != null ? ` · ${v.batteryPct}%` : ''}</option>`).join('');

const badge = (cls, label) => `<span class="state ${cls}">${UM.esc(label)}</span>`;
const ORDER_STATES = {
  OPEN: ['OPEN', 'Abierta'], WITHDRAWN: ['WITHDRAWN', 'Retirado'], IN_PROGRESS: ['IN_PROGRESS', 'En taller'],
  COMPLETED: ['READY_FOR_SERVICE', 'Listo'], CLOSED: ['CLOSED', 'Cerrada'], CANCELLED: ['CANCELLED', 'Cancelada'],
};

function every(ms, fn) {
  fn();
  const id = setInterval(fn, ms);
  return () => clearInterval(id);
}

function ask(title, body, onSubmit) {
  dialog.innerHTML = `<form method="dialog"><h2>${title}</h2>${body}
    <div class="actions" style="margin-top:18px"><button class="btn" value="ok">Confirmar</button><button class="btn ghost" value="cancel" formnovalidate>Cancelar</button></div></form>`;
  const form = dialog.querySelector('form');
  form.onsubmit = async (e) => {
    if (e.submitter?.value !== 'ok') return;
    e.preventDefault();
    const ok = await onSubmit(new FormData(form), form);
    if (ok !== false) dialog.close();
  };
  dialog.showModal();
}

// -------------------------------------------------------------------- flota
function flota(view) {
  view.innerHTML = `
    <div class="panel"><h2>Por zona <span id="total"></span></h2><div class="zones-strip" id="strip"></div></div>
    <div class="panel"><h2>Vehículos</h2>
      <div class="filters">
        <label class="field"><span>Estado</span><select id="fs"><option value="">Todos</option>${Object.keys(UM.STATES).slice(0, 9).map((s) => `<option value="${s}">${UM.STATES[s]}</option>`).join('')}</select></label>
        <label class="field"><span>Zona</span><select id="fz"><option value="">Todas</option>${zoneOptions()}</select></label>
      </div>
      <div class="table-wrap"><table><thead><tr><th>Código</th><th>Tipo</th><th>Estado</th><th>Zona</th><th>Batería</th><th>Actualizado</th><th></th></tr></thead><tbody id="rows"></tbody></table></div>
    </div>`;
  const render = async () => {
    const [summary, vehicles] = await Promise.all([UM.api('/api/availability/summary'), UM.api('/api/availability')]);
    ctx.vehicles = vehicles;
    view.querySelector('#total').textContent = `${vehicles.filter((v) => v.state !== 'RETIRED').length} vehículos`;
    view.querySelector('#strip').innerHTML = summary.map((z) => `<div>
      <div class="name">${UM.esc(z.name)}</div><div class="big">${z.available}</div>
      <div class="meta">res ${z.reserved} · uso ${z.in_use} · bat ${z.low_battery} · taller ${z.maintenance}</div></div>`).join('');
    const fs = view.querySelector('#fs').value;
    const fz = view.querySelector('#fz').value;
    view.querySelector('#rows').innerHTML = vehicles.filter((v) => (!fs || v.state === fs) && (!fz || v.zoneId === fz)).map((v) => `<tr>
      <td class="mono">${UM.esc(v.code)}</td><td>${UM.type(v.vehicleType)}</td><td>${UM.state(v.state)}</td>
      <td>${UM.esc(zoneName(v.zoneId))}</td><td>${UM.battery(v.batteryPct)}</td><td class="muted">${UM.time(v.updatedAt)}</td>
      <td><button class="btn small ghost" data-trace="${v.vehicleId}">Traza</button></td></tr>`).join('');
  };
  view.querySelector('#fs').onchange = render;
  view.querySelector('#fz').onchange = render;
  view.querySelector('#rows').onclick = (e) => { if (e.target.dataset.trace) trace({ vehicleId: e.target.dataset.trace }); };
  return every(4000, () => render().catch((e) => UM.toast(e.message, 'error')));
}

async function trace(filter) {
  const query = new URLSearchParams({ ...filter, limit: 40 }).toString();
  const events = await UM.api(`/api/history?${query}`);
  dialog.innerHTML = `<h2>Traza de eventos</h2>
    <div class="table-wrap flow"><table><thead><tr><th>Hora</th><th>Evento</th><th>Productor</th></tr></thead><tbody>
    ${events.map((e) => `<tr><td class="mono">${new Date(e.occurred_at).toLocaleTimeString('es-ES')}</td><td>${UM.esc(e.event_type)}</td><td class="muted">${UM.esc(e.producer)}</td></tr>`).join('') || '<tr><td colspan="3" class="empty">Sin eventos</td></tr>'}
    </tbody></table></div><div class="actions" style="margin-top:16px"><button class="btn ghost" onclick="this.closest('dialog').close()">Cerrar</button></div>`;
  dialog.style.width = 'min(640px, calc(100vw - 32px))';
  dialog.showModal();
  dialog.addEventListener('close', () => { dialog.style.width = ''; }, { once: true });
}

// -------------------------------------------------------------------- mantenimiento
function mantenimiento(view) {
  view.innerHTML = `<div class="cols">
    <form class="panel" id="new"><h2>Nueva orden</h2>
      <label class="field"><span>Vehículo</span><select name="vehicleId">${vehicleOptions()}</select></label>
      <label class="field"><span>Tipo</span><select name="type"><option value="CHARGE">Carga</option><option value="REPAIR">Reparación</option></select></label>
      <label class="field"><span>Motivo</span><input name="reason" placeholder="Revisión de frenos"></label>
      <button class="btn">Abrir orden</button>
      <p class="muted" style="font-size:12px;margin:16px 0 0">Las órdenes automáticas las crea la Lambda <span class="mono">maintenance-detection</span> al detectar batería baja, una incidencia o demasiados usos.</p>
    </form>
    <div class="panel"><h2>Órdenes</h2><div class="table-wrap"><table>
      <thead><tr><th>Vehículo</th><th>Tipo</th><th>Motivo</th><th>Origen</th><th>Estado</th><th>Abierta</th><th></th></tr></thead><tbody id="rows"></tbody></table></div></div></div>`;
  const next = {
    OPEN: (o) => `<button class="btn small" data-a="withdraw" data-id="${o.order_id}">Retirar</button><button class="btn small ghost" data-a="cancel" data-id="${o.order_id}">Cancelar</button>`,
    WITHDRAWN: (o) => `<button class="btn small" data-a="start" data-id="${o.order_id}">${o.type === 'CHARGE' ? 'Iniciar carga' : 'Iniciar reparación'}</button>`,
    IN_PROGRESS: (o) => `<button class="btn small" data-a="complete" data-id="${o.order_id}">Completar</button>`,
    COMPLETED: (o) => `<button class="btn small" data-a="return" data-id="${o.order_id}">Devolver a la calle</button>`,
  };
  const render = async () => {
    const orders = await UM.api('/api/maintenance-orders');
    view.querySelector('#rows').innerHTML = orders.map((o) => `<tr>
      <td class="mono">${UM.esc(code(o.vehicle_id))}</td><td>${o.type === 'CHARGE' ? 'Carga' : 'Reparación'}</td>
      <td>${UM.esc(o.reason)}</td><td class="muted">${o.origin === 'AUTOMATIC' ? 'Automática' : 'Manual'}</td>
      <td>${badge(...ORDER_STATES[o.status])}</td>
      <td class="muted">${UM.dateTime(o.created_at)}</td>
      <td><div class="actions">${(next[o.status] || (() => ''))(o)}</div></td></tr>`).join('') || '<tr><td colspan="7" class="empty">No hay órdenes.</td></tr>';
  };
  view.querySelector('#rows').onclick = (e) => {
    const { a, id } = e.target.dataset;
    if (!a) return;
    if (a === 'return') {
      ask('Devolver a la calle', `<label class="field"><span>Zona donde se deja</span><select name="zoneId">${zoneOptions()}</select></label>`,
        (data) => UM.run(() => UM.api(`/api/maintenance-orders/${id}/return`, { method: 'POST', body: { zoneId: data.get('zoneId') } }), 'Vehículo devuelto al servicio').then(render));
      return;
    }
    const labels = { withdraw: 'Vehículo retirado', start: 'Trabajo iniciado', complete: 'Listo para volver a la calle', cancel: 'Orden cancelada' };
    UM.run(() => UM.api(`/api/maintenance-orders/${id}/${a}`, { method: 'POST' }), labels[a]).then(render);
  };
  view.querySelector('#new').onsubmit = (e) => {
    e.preventDefault();
    UM.run(() => UM.api('/api/maintenance-orders', { method: 'POST', body: Object.fromEntries(new FormData(e.target)) }), 'Orden abierta').then(render);
  };
  return every(4000, () => render().catch((e) => UM.toast(e.message, 'error')));
}

// -------------------------------------------------------------------- incidencias
function incidencias(view) {
  view.innerHTML = `<div class="panel"><h2>Incidencias</h2><div class="table-wrap"><table>
    <thead><tr><th>Vehículo</th><th>Tipo</th><th>Descripción</th><th>Fecha</th><th>Estado</th><th></th></tr></thead><tbody id="rows"></tbody></table></div></div>`;
  const types = { DAMAGE: 'Daño', BATTERY: 'Batería', PARKING: 'Aparcamiento', OTHER: 'Otro' };
  const render = async () => {
    const list = await UM.api('/api/incidents');
    view.querySelector('#rows').innerHTML = list.map((i) => `<tr>
      <td class="mono">${UM.esc(code(i.vehicle_id))}</td><td>${types[i.type] || i.type}</td>
      <td>${UM.esc(i.description || '—')}${i.resolution ? `<div class="muted" style="font-size:12px">Resolución: ${UM.esc(i.resolution)}</div>` : ''}</td>
      <td class="muted">${UM.dateTime(i.created_at)}</td><td>${UM.state(i.status)}</td>
      <td>${i.status === 'OPEN' ? `<button class="btn small" data-id="${i.incident_id}">Resolver</button>` : ''}</td></tr>`).join('')
      || '<tr><td colspan="6" class="empty">No hay incidencias.</td></tr>';
  };
  view.querySelector('#rows').onclick = (e) => {
    const id = e.target.dataset.id;
    if (!id) return;
    ask('Resolver incidencia', '<label class="field"><span>Resolución</span><textarea name="resolution" required></textarea></label>',
      (data) => UM.run(() => UM.api(`/api/incidents/${id}/resolve`, { method: 'POST', body: { resolution: data.get('resolution') } }), 'Incidencia resuelta').then(render));
  };
  return every(5000, () => render().catch((e) => UM.toast(e.message, 'error')));
}

// -------------------------------------------------------------------- recolocación
function recolocacion(view) {
  view.innerHTML = `<div class="cols">
    <div class="stack">
      <div class="panel"><h2>Demanda por zona <span id="build"></span></h2><div id="demand"><p class="empty">Esperando al primer build de marts…</p></div></div>
      <form class="panel" id="new"><h2>Orden manual</h2>
        <div class="row"><label class="field"><span>Desde</span><select name="fromZoneId">${zoneOptions()}</select></label>
        <label class="field"><span>Hacia</span><select name="toZoneId">${zoneOptions(ctx.zones[1]?.zoneId)}</select></label></div>
        <label class="field"><span>Vehículos</span><input name="quantity" type="number" min="1" max="10" value="1"></label>
        <button class="btn">Crear orden</button>
      </form>
    </div>
    <div class="panel"><h2>Órdenes de recolocación</h2><div class="table-wrap"><table>
      <thead><tr><th>Ruta</th><th class="num">Uds.</th><th>Origen</th><th>Estado</th><th>Asignada</th><th></th></tr></thead><tbody id="rows"></tbody></table></div></div></div>`;
  const next = {
    CREATED: (o) => `<button class="btn small" data-a="assign" data-id="${o.order_id}">Asignarme</button><button class="btn small ghost" data-a="cancel" data-id="${o.order_id}">Cancelar</button>`,
    ASSIGNED: (o) => `<button class="btn small" data-a="start" data-id="${o.order_id}">Iniciar</button>`,
    IN_PROGRESS: (o) => `<button class="btn small" data-a="complete" data-id="${o.order_id}">Completar</button>`,
  };
  let orders = [];
  const render = async () => {
    const [list, dash] = await Promise.all([UM.api('/api/relocation-orders'), UM.api('/api/analytics/dashboard').catch(() => null)]);
    orders = list;
    view.querySelector('#rows').innerHTML = list.map((o) => `<tr>
      <td>${UM.esc(zoneName(o.from_zone_id))} → ${UM.esc(zoneName(o.to_zone_id))}<div class="muted" style="font-size:12px">${UM.esc(o.reason || '')}</div></td>
      <td class="num">${o.quantity}</td><td class="muted">${o.origin === 'AUTOMATIC' ? 'Automática' : 'Manual'}</td>
      <td>${UM.state(o.status)}</td><td class="muted">${UM.esc(o.assignee || '—')}</td>
      <td><div class="actions">${(next[o.status] || (() => ''))(o)}</div></td></tr>`).join('') || '<tr><td colspan="6" class="empty">No hay órdenes.</td></tr>';
    if (dash?.demand?.length) {
      view.querySelector('#build').textContent = dash.build ? `build ${UM.time(dash.build.built_at)}` : '';
      view.querySelector('#demand').innerHTML = `<table><thead><tr><th>Zona</th><th class="num">Disp.</th><th class="num">Demanda 1 h</th><th>Estado</th></tr></thead><tbody>
        ${dash.demand.map((d) => `<tr><td>${UM.esc(d.zone_name)}</td><td class="num">${d.available_now}</td><td class="num">${d.demand_last_hour}</td><td>${UM.state(d.status)}</td></tr>`).join('')}</tbody></table>`;
    }
  };
  view.querySelector('#rows').onclick = (e) => {
    const { a, id } = e.target.dataset;
    if (!a) return;
    if (a === 'complete') {
      const order = orders.find((o) => o.order_id === id);
      const candidates = ctx.vehicles.filter((v) => v.zoneId === order.from_zone_id && ['AVAILABLE', 'LOW_BATTERY'].includes(v.state));
      ask(`Completar · ${UM.esc(zoneName(order.from_zone_id))} → ${UM.esc(zoneName(order.to_zone_id))}`,
        candidates.length ? `<p class="muted" style="margin:0 0 8px">Marca los vehículos movidos (${order.quantity} previstos).</p>` + candidates.map((v, i) => `
          <label class="check"><input type="checkbox" name="v" value="${v.vehicleId}" ${i < order.quantity ? 'checked' : ''}><span class="mono">${UM.esc(v.code)}</span> ${UM.type(v.vehicleType)}</label>`).join('')
          : '<p class="empty">No quedan vehículos disponibles en la zona de origen. Puedes completar la orden sin moverlos.</p>',
        (data) => UM.run(() => UM.api(`/api/relocation-orders/${id}/complete`, { method: 'POST', body: { vehicleIds: data.getAll('v') } }), 'Recolocación completada').then(render));
      return;
    }
    const labels = { assign: 'Orden asignada', start: 'Orden en curso', cancel: 'Orden cancelada' };
    UM.run(() => UM.api(`/api/relocation-orders/${id}/${a}`, { method: 'POST', body: {} }), labels[a]).then(render);
  };
  view.querySelector('#new').onsubmit = (e) => {
    e.preventDefault();
    const body = Object.fromEntries(new FormData(e.target));
    body.quantity = Number(body.quantity);
    UM.run(() => UM.api('/api/relocation-orders', { method: 'POST', body }), 'Orden creada').then(render);
  };
  return every(5000, () => render().catch((e) => UM.toast(e.message, 'error')));
}

// -------------------------------------------------------------------- telemetría
function telemetria(view) {
  view.innerHTML = `<div class="cols">
    <form class="panel" id="send"><h2>Simular lectura</h2>
      <label class="field"><span>Vehículo</span><select name="vehicleId">${vehicleOptions((v) => v.batteryPct != null)}</select></label>
      <label class="field"><span>Batería <b class="mono" id="pct">50%</b></span><input type="range" name="batteryPct" min="0" max="100" value="50"></label>
      <div class="actions"><button class="btn">Enviar lectura</button><button class="btn ghost" type="button" id="low">Enviar 12 %</button></div>
      <p class="muted" style="font-size:12px;margin:16px 0 0">Por debajo del 20 % telemetry publica <span class="mono">VehicleBatteryLow</span>: availability lo pasa a batería baja y la Lambda abre una orden de carga.</p>
    </form>
    <div class="panel"><h2>Últimas lecturas <span id="who"></span></h2><div class="table-wrap"><table>
      <thead><tr><th>Hora</th><th>Batería</th></tr></thead><tbody id="rows"></tbody></table></div></div></div>`;
  const form = view.querySelector('#send');
  form.batteryPct.oninput = () => { view.querySelector('#pct').textContent = `${form.batteryPct.value}%`; };
  const load = async () => {
    const id = form.vehicleId.value;
    if (!id) return;
    view.querySelector('#who').textContent = code(id);
    const rows = await UM.api(`/api/telemetry/${id}`);
    view.querySelector('#rows').innerHTML = rows.map((r) => `<tr><td class="mono">${new Date(r.recorded_at).toLocaleTimeString('es-ES')}</td><td>${UM.battery(r.battery_pct)}</td></tr>`).join('')
      || '<tr><td colspan="2" class="empty">Sin lecturas para este vehículo.</td></tr>';
  };
  const send = async (pct) => {
    const v = ctx.vehicles.find((x) => x.vehicleId === form.vehicleId.value);
    const r = await UM.run(() => UM.api('/api/telemetry', { method: 'POST', body: { vehicleId: v.vehicleId, batteryPct: pct, latitude: v.latitude, longitude: v.longitude } }));
    if (r) UM.toast(r.batteryLowDetected ? 'Lectura enviada · batería baja detectada' : 'Lectura enviada');
    load();
  };
  form.vehicleId.onchange = load;
  form.onsubmit = (e) => { e.preventDefault(); send(Number(form.batteryPct.value)); };
  view.querySelector('#low').onclick = () => send(12);
  load().catch((e) => UM.toast(e.message, 'error'));
}

// -------------------------------------------------------------------- eventos
function eventos(view) {
  view.innerHTML = `<div class="panel"><h2>Flujo de eventos <span>history-service</span></h2>
    <div class="filters">
      <label class="field"><span>Tipo de evento</span><input id="type" placeholder="TripRecorded"></label>
      <label class="field"><span>Correlation id</span><input id="corr" placeholder="pulsa uno de la tabla"></label>
    </div>
    <div class="table-wrap"><table class="flow"><thead><tr><th>Hora</th><th>Evento</th><th>Productor</th><th>Vehículo</th><th>Zona</th><th>Correlation</th></tr></thead><tbody id="rows"></tbody></table></div></div>`;
  const render = async () => {
    const params = new URLSearchParams({ limit: 80 });
    const type = view.querySelector('#type').value.trim();
    const corr = view.querySelector('#corr').value.trim();
    if (type) params.set('eventType', type);
    if (corr) params.set('correlationId', corr);
    const events = await UM.api(`/api/history?${params}`);
    view.querySelector('#rows').innerHTML = events.map((e) => `<tr>
      <td class="mono">${new Date(e.occurred_at).toLocaleTimeString('es-ES')}</td><td>${UM.esc(e.event_type)}</td>
      <td class="muted">${UM.esc(e.producer)}</td><td class="mono">${e.vehicle_id ? UM.esc(code(e.vehicle_id)) : '—'}</td>
      <td>${UM.esc(e.zone_id || '—')}</td><td><a href="#" class="link mono" data-corr="${UM.esc(e.correlation_id)}">${UM.short(e.correlation_id)}</a></td></tr>`).join('')
      || '<tr><td colspan="6" class="empty">Sin eventos.</td></tr>';
  };
  view.querySelector('#rows').onclick = (e) => {
    if (!e.target.dataset.corr) return;
    e.preventDefault();
    view.querySelector('#corr').value = e.target.dataset.corr;
    render();
  };
  view.querySelector('#type').onchange = render;
  view.querySelector('#corr').onchange = render;
  return every(4000, () => render().catch((e) => UM.toast(e.message, 'error')));
}

// -------------------------------------------------------------------- inventario (ADMIN)
function inventario(view) {
  view.innerHTML = `<div class="cols">
    <form class="panel" id="buy"><h2>Alta de vehículo</h2>
      <label class="field"><span>Tipo</span><select name="vehicleType"><option value="ELECTRIC_SCOOTER">Patinete eléctrico</option><option value="ELECTRIC_BICYCLE">Bici eléctrica</option><option value="BICYCLE">Bicicleta</option></select></label>
      <label class="field"><span>Zona inicial</span><select name="zoneId">${zoneOptions()}</select></label>
      <label class="field"><span>Modelo (opcional)</span><input name="model"></label>
      <button class="btn">Dar de alta</button>
    </form>
    <div class="panel"><h2>Inventario</h2><div class="table-wrap"><table>
      <thead><tr><th>Código</th><th>Tipo</th><th>Modelo</th><th>Alta</th><th>Estado</th><th></th></tr></thead><tbody id="rows"></tbody></table></div></div></div>`;
  const render = async () => {
    const list = await UM.api('/api/vehicles');
    view.querySelector('#rows').innerHTML = list.map((v) => `<tr>
      <td class="mono">${UM.esc(v.code)}</td><td>${UM.type(v.vehicleType)}</td><td>${UM.esc(v.model)}</td>
      <td class="muted">${UM.dateTime(v.purchasedAt)}</td><td>${v.retiredAt ? badge('RETIRED', 'Baja') : badge('AVAILABLE', 'En flota')}</td>
      <td>${v.retiredAt ? '' : `<button class="btn small ghost" data-id="${v.vehicleId}">Dar de baja</button>`}</td></tr>`).join('');
  };
  view.querySelector('#rows').onclick = (e) => {
    const id = e.target.dataset.id;
    if (!id) return;
    ask('Dar de baja', '<label class="field"><span>Motivo</span><input name="reason" required></label>',
      (data) => UM.run(() => UM.api(`/api/vehicles/${id}/retire`, { method: 'POST', body: { reason: data.get('reason') } }), 'Baja registrada').then(render));
  };
  view.querySelector('#buy').onsubmit = (e) => {
    e.preventDefault();
    UM.run(() => UM.api('/api/vehicles', { method: 'POST', body: Object.fromEntries(new FormData(e.target)) }), 'Vehículo dado de alta').then(render);
  };
  render().catch((e) => UM.toast(e.message, 'error'));
}

// -------------------------------------------------------------------- usuarios (ADMIN)
function usuarios(view) {
  view.innerHTML = `<div class="panel"><h2>Usuarios <span>Keycloak · identity</span></h2><div class="table-wrap"><table>
    <thead><tr><th>Usuario</th><th>Nombre</th><th>Correo</th><th>Roles</th><th>Alta</th><th></th></tr></thead><tbody id="rows"></tbody></table></div></div>`;
  const render = async () => {
    const list = await UM.api('/api/users');
    view.querySelector('#rows').innerHTML = list.map((u) => `<tr>
      <td class="mono">${UM.esc(u.username)}</td><td>${UM.esc(u.full_name)}</td><td class="muted">${UM.esc(u.email)}</td>
      <td class="mono">${UM.esc(u.roles)}</td><td class="muted">${UM.dateTime(u.created_at)}</td>
      <td><div class="actions"><select data-user="${u.user_id}" style="width:auto"><option value="">Añadir rol…</option>
        ${['USER', 'MAINTENANCE', 'ADMIN'].filter((r) => !u.roles.split(',').includes(r)).map((r) => `<option>${r}</option>`).join('')}</select></div></td></tr>`).join('');
  };
  view.querySelector('#rows').onchange = (e) => {
    const userId = e.target.dataset.user;
    if (!userId || !e.target.value) return;
    UM.run(() => UM.api(`/api/users/${userId}/roles`, { method: 'POST', body: { role: e.target.value } }), 'Rol asignado').then(render);
  };
  render().catch((e) => UM.toast(e.message, 'error'));
}
