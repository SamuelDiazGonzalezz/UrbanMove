/* App de usuario: buscar vehículos, reservar, iniciar y finalizar viajes, historial, perfil e incidencias. */
const EVENT_LABELS = {
  VehicleReserved: 'Reserva creada', ReservationCancelled: 'Reserva cancelada', ReservationExpired: 'Reserva expirada',
  ReservationCompleted: 'Reserva confirmada al iniciar el viaje', ReservationRejected: 'Reserva rechazada',
  VehiclePickedUp: 'Vehículo desbloqueado', VehicleDroppedOff: 'Vehículo devuelto', TripRecorded: 'Viaje registrado',
  VehicleAvailabilityChanged: 'Estado del vehículo actualizado', IncidentReported: 'Incidencia enviada',
  UserRegistered: 'Cuenta creada', UserProfileUpdated: 'Perfil actualizado',
};

UM.gate({
  subtitle: 'Usuario',
  headline: 'Coge una bici. Déjala donde vayas.',
  intro: 'Reserva un vehículo durante unos minutos, desbloquéalo cuando llegues y termina el viaje indicando la zona de destino.',
  roles: ['USER'],
  allowRegister: true,
  hint: 'Cuentas de prueba: ana / urbanmove · luis / urbanmove',
  start,
});

function start(session) {
  const main = document.querySelector('main');
  main.innerHTML = `<nav class="tabs" role="tablist"></nav><section id="view"></section>`;
  const view = main.querySelector('#view');
  let stop = () => {};
  UM.tabs(main.querySelector('.tabs'), [['mapa', 'Mapa'], ['historial', 'Mis viajes'], ['perfil', 'Perfil'], ['incidencias', 'Incidencias']], (tab) => {
    stop();
    stop = { mapa: mapView, historial: historyView, perfil: profileView, incidencias: incidentView }[tab](view, session) || (() => {});
  }, 'mapa');
}

// -------------------------------------------------------------------- mapa y viaje
function mapView(view, session) {
  view.innerHTML = `
    <div class="layout">
      <div class="panel map-panel"><div id="map"></div></div>
      <div class="side">
        <div class="panel now" id="now"><p class="empty">Cargando…</p></div>
        <div class="panel">
          <h2>Vehículos disponibles <span id="count"></span></h2>
          <label class="field"><span>Zona</span><select id="zone"><option value="">Todas las zonas</option></select></label>
          <div id="vehicles"></div>
        </div>
      </div>
    </div>`;

  const map = L.map('map', { zoomControl: true, attributionControl: true }).setView([28.125, -15.43], 13);
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19, attribution: '&copy; OpenStreetMap',
  }).addTo(map);
  const markers = L.layerGroup().addTo(map);
  const zoneLayer = L.layerGroup().addTo(map);

  let zones = [];
  let vehicles = [];
  let trip = null;
  let reservation = null;
  let focus = null;
  let busy = false;
  let lastSignature = null;

  const zoneSelect = view.querySelector('#zone');
  zoneSelect.onchange = () => { renderVehicles(); };

  async function loadZones() {
    zones = await UM.api('/api/zones', { auth: false });
    zoneSelect.innerHTML = '<option value="">Todas las zonas</option>' + zones.map((z) => `<option value="${z.zoneId}">${UM.esc(z.name)}</option>`).join('');
    zoneLayer.clearLayers();
    zones.forEach((z) => {
      L.circle([z.latitude, z.longitude], { radius: z.radiusMeters, color: '#0b0b0b', weight: 1, fill: false, dashArray: '2 4' })
        .bindTooltip(z.name, { direction: 'top' }).addTo(zoneLayer);
    });
    if (zones.length) map.fitBounds(L.latLngBounds(zones.map((z) => [z.latitude, z.longitude])).pad(0.15));
  }

  async function refresh() {
    try {
      const [v, active, mine] = await Promise.all([
        UM.api('/api/availability'),
        UM.api('/api/rides/me/active'),
        UM.api('/api/reservations/me'),
      ]);
      vehicles = v.filter((x) => x.state !== 'RETIRED');
      trip = active;
      reservation = mine.find((r) => r.status === 'ACTIVE') || null;
      if (!busy) {
        const signature = `${trip?.tripId || ''}|${reservation?.reservationId || ''}|${zones.length}`;
        if (signature !== lastSignature) { lastSignature = signature; renderNow(); }
        renderVehicles();
        renderMarkers();
      }
    } catch (e) {
      view.querySelector('#now').innerHTML = `<p class="error-line">${UM.esc(e.message)}</p>`;
    }
  }

  const zoneName = (id) => zones.find((z) => z.zoneId === id)?.name || id || '—';

  function renderMarkers() {
    markers.clearLayers();
    vehicles.forEach((v) => {
      if (v.latitude == null) return;
      const mine = (trip && trip.vehicleId === v.vehicleId) || (reservation && reservation.vehicleId === v.vehicleId);
      const icon = L.divIcon({ className: '', html: `<div class="pin ${v.state === 'AVAILABLE' ? '' : 'off'} ${mine ? 'mine' : ''}">${UM.esc(v.code)}</div>`, iconSize: null });
      L.marker([v.latitude, v.longitude], { icon }).on('click', () => { focus = v.vehicleId; zoneSelect.value = ''; renderVehicles(); })
        .bindTooltip(`${UM.type(v.vehicleType)} · ${UM.STATES[v.state]}${v.batteryPct != null ? ` · ${v.batteryPct}%` : ''}`, { direction: 'top', offset: [0, -8] })
        .addTo(markers);
    });
  }

  function renderVehicles() {
    const zone = zoneSelect.value;
    const list = vehicles.filter((v) => v.state === 'AVAILABLE' && (!zone || v.zoneId === zone))
      .sort((a, b) => (b.vehicleId === focus) - (a.vehicleId === focus) || a.code.localeCompare(b.code));
    view.querySelector('#count').textContent = list.length;
    const blocked = Boolean(trip || reservation);
    view.querySelector('#vehicles').innerHTML = list.length ? list.map((v) => `
      <div class="vehicle ${v.vehicleId === focus ? 'focus' : ''}">
        <div><span class="code">${UM.esc(v.code)}</span> <span class="muted">${UM.type(v.vehicleType)}</span></div>
        <div class="actions">
          <button class="btn small" data-reserve="${v.vehicleId}" ${blocked ? 'disabled' : ''}>Reservar</button>
          <button class="btn small ghost" data-start="${v.vehicleId}" ${blocked ? 'disabled' : ''}>Desbloquear</button>
        </div>
        <div class="meta"><span>${UM.esc(zoneName(v.zoneId))}</span>${UM.battery(v.batteryPct)}</div>
      </div>`).join('') : '<p class="empty">No hay vehículos disponibles en esta zona ahora mismo.</p>';
  }

  function renderNow() {
    const box = view.querySelector('#now');
    if (trip) {
      const elapsed = (Date.now() - new Date(trip.startedAt).getTime()) / 1000;
      box.innerHTML = `
        <h2>Viaje en curso <span>${UM.esc(trip.vehicleCode)}</span></h2>
        <div class="big" data-clock="${trip.startedAt}">${UM.duration(elapsed)}</div>
        <p class="muted">Desde ${UM.esc(zoneName(trip.originZoneId))} · ${UM.time(trip.startedAt)}</p>
        <hr class="hr">
        <label class="field"><span>Zona de destino</span>
          <select id="dest">${zones.map((z) => `<option value="${z.zoneId}">${UM.esc(z.name)}</option>`).join('')}</select></label>
        <button class="btn block" id="finish">Finalizar viaje</button>`;
      box.querySelector('#dest').value = zones.find((z) => z.zoneId !== trip.originZoneId)?.zoneId || '';
      box.querySelector('#finish').onclick = finish;
      return;
    }
    if (reservation) {
      box.innerHTML = `
        <h2>Reserva activa <span>${UM.esc(reservation.vehicleCode)}</span></h2>
        <div class="big" data-countdown="${reservation.expiresAt}">—</div>
        <p class="muted">Caduca a las ${UM.time(reservation.expiresAt)} · ${UM.esc(zoneName(reservation.zoneId))}</p>
        <hr class="hr">
        <div class="actions"><button class="btn" id="go">Iniciar viaje</button><button class="btn ghost" id="cancel">Cancelar</button></div>`;
      box.querySelector('#go').onclick = () => startTrip(reservation.vehicleId);
      box.querySelector('#cancel').onclick = cancel;
      tick();
      return;
    }
    box.innerHTML = `
      <h2>Hola, ${UM.esc(session.user.fullName.split(' ')[0] || session.user.username)}</h2>
      <p class="muted" style="margin:0">Elige un vehículo en el mapa o en la lista. Puedes reservarlo unos minutos o desbloquearlo directamente.</p>`;
  }

  function tick() {
    const clock = view.querySelector('[data-clock]');
    if (clock) clock.textContent = UM.duration((Date.now() - new Date(clock.dataset.clock).getTime()) / 1000);
    const countdown = view.querySelector('[data-countdown]');
    if (countdown) {
      const left = (new Date(countdown.dataset.countdown).getTime() - Date.now()) / 1000;
      countdown.textContent = left > 0 ? UM.duration(left) : 'Expirando…';
    }
  }

  async function act(fn, message) {
    busy = true;
    await UM.run(fn, message);
    busy = false;
    await refresh();
    setTimeout(refresh, 1200);
  }

  const startTrip = (vehicleId) => act(() => UM.api('/api/rides/start', { method: 'POST', body: { vehicleId } }), 'Vehículo desbloqueado. Buen viaje.');
  const cancel = () => act(() => UM.api(`/api/reservations/${reservation.reservationId}/cancel`, { method: 'POST' }), 'Reserva cancelada');
  const finish = () => act(async () => {
    const t = await UM.api(`/api/rides/${trip.tripId}/finish`, { method: 'POST', body: { destinationZoneId: view.querySelector('#dest').value } });
    UM.toast(`Viaje finalizado · ${UM.duration(t.durationSeconds)} · ${UM.num(t.distanceMeters / 1000, 1)} km · ${UM.money(t.cost)}`);
  });

  view.querySelector('#vehicles').onclick = (e) => {
    const reserve = e.target.dataset.reserve;
    const startId = e.target.dataset.start;
    if (reserve) act(() => UM.api('/api/reservations', { method: 'POST', body: { vehicleId: reserve } }), 'Reserva creada');
    if (startId) startTrip(startId);
  };

  loadZones().then(refresh).catch((e) => UM.toast(e.message, 'error'));
  const poll = setInterval(refresh, 3000);
  const clock = setInterval(tick, 1000);
  setTimeout(() => map.invalidateSize(), 50);
  return () => { clearInterval(poll); clearInterval(clock); map.remove(); };
}

// -------------------------------------------------------------------- historial
function historyView(view) {
  view.innerHTML = `
    <div class="grid-2">
      <div class="panel"><h2>Viajes</h2><div id="trips"><p class="empty">Cargando…</p></div></div>
      <div>
        <div class="panel"><h2>Reservas</h2><div id="res"></div></div>
        <div class="panel"><h2>Actividad</h2><ul class="list timeline" id="timeline"></ul></div>
      </div>
    </div>`;
  Promise.all([UM.api('/api/rides/me'), UM.api('/api/reservations/me'), UM.api('/api/history?limit=30'), UM.api('/api/zones', { auth: false })])
    .then(([trips, reservations, events, zones]) => {
      const zn = (id) => zones.find((z) => z.zoneId === id)?.name || id || '—';
      const done = trips.filter((t) => t.status === 'COMPLETED');
      view.querySelector('#trips').innerHTML = done.length ? `
        <div class="table-wrap"><table>
          <thead><tr><th>Fecha</th><th>Recorrido</th><th class="num">Duración</th><th class="num">Km</th><th class="num">Coste</th></tr></thead>
          <tbody>${done.map((t) => `<tr>
            <td>${UM.dateTime(t.startedAt)}<div class="muted mono" style="font-size:11px">${UM.esc(t.vehicleCode)}</div></td>
            <td>${UM.esc(zn(t.originZoneId))} → ${UM.esc(zn(t.destinationZoneId))}</td>
            <td class="num">${UM.duration(t.durationSeconds)}</td>
            <td class="num">${UM.num((t.distanceMeters || 0) / 1000, 1)}</td>
            <td class="num">${UM.money(t.cost)}</td></tr>`).join('')}</tbody>
        </table></div>
        <p class="muted" style="margin:14px 0 0">Total: ${done.length} viajes · ${UM.money(done.reduce((s, t) => s + Number(t.cost || 0), 0))}</p>`
        : '<p class="empty">Todavía no has hecho ningún viaje.</p>';
      view.querySelector('#res').innerHTML = reservations.length ? `<ul class="list">${reservations.slice(0, 8).map((r) => `
        <li><div><span class="mono">${UM.esc(r.vehicleCode)}</span><div class="meta">${UM.dateTime(r.createdAt)} · ${UM.esc(zn(r.zoneId))}</div></div>${UM.state(r.status)}</li>`).join('')}</ul>`
        : '<p class="empty">Sin reservas.</p>';
      view.querySelector('#timeline').innerHTML = events.length ? events.map((e) => `
        <li><div>${UM.esc(EVENT_LABELS[e.event_type] || e.event_type)}<div class="meta mono">${UM.esc(e.event_type)}</div></div>
        <span class="muted mono" style="font-size:12px">${UM.dateTime(e.occurred_at)}</span></li>`).join('')
        : '<li class="empty">La actividad aparece aquí unos segundos después de cada acción.</li>';
    }).catch((e) => UM.toast(e.message, 'error'));
}

// -------------------------------------------------------------------- perfil
function profileView(view, session) {
  view.innerHTML = `<div class="grid-2"><form class="panel" id="profile"><h2>Perfil</h2><p class="empty">Cargando…</p></form>
    <div class="panel"><h2>Cuenta</h2><ul class="list">
      <li><span class="muted">Usuario</span><span class="mono">${UM.esc(session.user.username)}</span></li>
      <li><span class="muted">Correo</span><span>${UM.esc(session.user.email)}</span></li>
      <li><span class="muted">Identificador</span><span class="mono">${UM.short(session.user.userId)}</span></li>
    </ul></div></div>`;
  const form = view.querySelector('#profile');
  Promise.all([UM.api('/api/profiles/me'), UM.api('/api/zones', { auth: false })]).then(([p, zones]) => {
    form.innerHTML = `<h2>Perfil</h2>
      <label class="field"><span>Nombre</span><input name="fullName" value="${UM.esc(p.full_name)}"></label>
      <label class="field"><span>Teléfono</span><input name="phone" value="${UM.esc(p.phone)}" inputmode="tel"></label>
      <label class="field"><span>Zona habitual</span><select name="preferredZoneId"><option value="">Sin preferencia</option>
        ${zones.map((z) => `<option value="${z.zoneId}" ${z.zoneId === p.preferred_zone_id ? 'selected' : ''}>${UM.esc(z.name)}</option>`).join('')}</select></label>
      <button class="btn">Guardar</button>`;
  }).catch((e) => { form.innerHTML = `<h2>Perfil</h2><p class="empty">${UM.esc(e.message)}</p>`; });
  form.onsubmit = (e) => {
    e.preventDefault();
    UM.run(() => UM.api('/api/profiles/me', { method: 'PUT', body: Object.fromEntries(new FormData(form)) }), 'Perfil guardado');
  };
}

// -------------------------------------------------------------------- incidencias
function incidentView(view) {
  view.innerHTML = `<div class="grid-2">
    <form class="panel" id="report"><h2>Reportar una incidencia</h2>
      <label class="field"><span>Vehículo</span><select name="vehicleId" required></select></label>
      <label class="field"><span>Tipo</span><select name="type">
        <option value="DAMAGE">Daño o avería</option><option value="BATTERY">Batería</option>
        <option value="PARKING">Mal aparcado</option><option value="OTHER">Otro</option></select></label>
      <label class="field"><span>Descripción</span><textarea name="description" maxlength="500" placeholder="Qué ha pasado y dónde"></textarea></label>
      <button class="btn">Enviar</button>
    </form>
    <div class="panel"><h2>Mis incidencias</h2><ul class="list" id="mine"></ul></div></div>`;
  const form = view.querySelector('#report');
  const load = async () => {
    const [vehicles, mine] = await Promise.all([UM.api('/api/availability'), UM.api('/api/incidents/me')]);
    const codes = Object.fromEntries(vehicles.map((v) => [v.vehicleId, v.code]));
    form.vehicleId.innerHTML = vehicles.filter((v) => v.state !== 'RETIRED').sort((a, b) => a.code.localeCompare(b.code))
      .map((v) => `<option value="${v.vehicleId}">${UM.esc(v.code)} · ${UM.type(v.vehicleType)}</option>`).join('');
    view.querySelector('#mine').innerHTML = mine.length ? mine.map((i) => `
      <li><div><span class="mono">${UM.esc(codes[i.vehicle_id] || UM.short(i.vehicle_id))}</span> · ${UM.esc(i.type)}
      <div class="meta">${UM.esc(i.description || '')}</div></div>${UM.state(i.status)}</li>`).join('')
      : '<li class="empty">No has reportado incidencias.</li>';
  };
  form.onsubmit = async (e) => {
    e.preventDefault();
    const ok = await UM.run(() => UM.api('/api/incidents', { method: 'POST', body: Object.fromEntries(new FormData(form)) }), 'Incidencia enviada. Mantenimiento la revisará.');
    if (ok) { form.description.value = ''; load(); }
  };
  load().catch((e) => UM.toast(e.message, 'error'));
}
