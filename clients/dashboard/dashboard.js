/* Dashboard operativo: lee los data marts (analytics-service) y la salud de la plataforma. */
UM.gate({
  subtitle: 'Dashboard',
  headline: 'Lo que pasa en la ciudad, cada cinco segundos.',
  intro: 'Indicadores construidos desde el Data Lake: flota, reservas, viajes, demanda por zona, mantenimiento, recolocación y salud de los servicios.',
  roles: ['ADMIN'],
  allowRegister: false,
  hint: 'Cuenta de prueba: gerencia / urbanmove',
  start,
});

const ZONES = {};

function start() {
  const main = document.querySelector('main');
  main.innerHTML = `
    <p class="stamp" id="stamp">Cargando marts…</p>
    <section class="kpis" id="kpis"></section>
    <section class="grid">
      <div class="panel c5"><h2>Flota por estado</h2><div id="fleet-state"></div></div>
      <div class="panel c7"><h2>Flota por zona</h2><div id="fleet-zone"></div>
        <div class="legend"><span><i class="seg-AVAILABLE"></i>Disponible</span><span><i class="seg-RESERVED"></i>Reservado</span><span><i class="seg-IN_USE"></i>En uso</span><span><i class="seg-LOW_BATTERY"></i>Batería baja</span><span><i class="seg-MAINT"></i>Mantenimiento</span></div></div>
      <div class="panel c8"><h2>Viajes por hora <span>hoy</span></h2><div id="trips-hour"></div></div>
      <div class="panel c4"><h2>Reservas <span>hoy</span></h2><div id="reservations"></div></div>
      <div class="panel c7"><h2>Demanda por zona <span>última hora</span></h2><div id="demand"></div></div>
      <div class="panel c5"><h2>Rutas más usadas</h2><div id="routes"></div></div>
      <div class="panel c6"><h2>Mantenimiento</h2><div id="maintenance"></div></div>
      <div class="panel c6"><h2>Recolocación</h2><div id="relocation"></div></div>
      <div class="panel c8"><h2>Salud de los servicios <span id="svc-count"></span></h2><div class="services" id="services"></div>
        <div class="table-wrap" style="margin-top:18px"><table><thead><tr><th>Cola</th><th class="num">Mensajes</th></tr></thead><tbody id="queues"></tbody></table></div></div>
      <div class="panel c4"><h2>Eventos <span>última hora</span></h2><div id="events"></div></div>
    </section>`;
  UM.api('/api/zones', { auth: false }).then((zones) => zones.forEach((z) => { ZONES[z.zoneId] = z.name; })).catch(() => {});
  const tick = () => Promise.all([load(), health()]);
  tick();
  setInterval(tick, 5000);
}

const zn = (id) => ZONES[id] || id || '—';
const set = (id, html) => { document.getElementById(id).innerHTML = html; };

function hbars(rows, labelOf, valueOf) {
  const max = Math.max(1, ...rows.map(valueOf));
  return rows.map((r) => `<div class="hbar"><span>${labelOf(r)}</span><span class="track"><span class="fill" style="width:${(valueOf(r) / max) * 100}%"></span></span><span class="n">${UM.num(valueOf(r))}</span></div>`).join('');
}

async function load() {
  let d;
  try { d = await UM.api('/api/analytics/dashboard'); } catch (e) { set('stamp', UM.esc(e.message)); return; }
  if (!d.build) {
    set('stamp', 'Todavía no hay ningún build de marts: aparecerá en cuanto la Lambda mart-builder procese los primeros eventos.');
    return;
  }
  set('stamp', `Build ${UM.short(d.build.build_id)} · ${new Date(d.build.built_at).toLocaleTimeString('es-ES')} · ${UM.num(d.build.facts_count)} hechos · ${d.build.duration_ms} ms`);

  const byState = Object.fromEntries(d.fleetByState.map((r) => [r.state, Number(r.vehicles)]));
  const total = Object.entries(byState).filter(([s]) => s !== 'RETIRED').reduce((s, [, n]) => s + n, 0);
  const maint = ['WITHDRAWN', 'CHARGING', 'REPAIRING', 'READY_FOR_SERVICE'].reduce((s, k) => s + (byState[k] || 0), 0);
  const r = d.reservationsToday;
  const t = d.tripsToday;
  const deficits = d.demand.filter((z) => z.status === 'DEFICIT').length;
  set('kpis', `
    <div><div class="label">Disponibles</div><div class="big">${byState.AVAILABLE || 0}</div><div class="sub">de ${total} en flota</div></div>
    <div><div class="label">En uso</div><div class="big">${byState.IN_USE || 0}</div><div class="sub">${byState.RESERVED || 0} reservados</div></div>
    <div><div class="label">Reservas hoy</div><div class="big">${UM.num(r.reserved)}</div><div class="sub">${UM.num(r.expired)} expiradas</div></div>
    <div><div class="label">Viajes hoy</div><div class="big">${UM.num(t.trips)}</div><div class="sub">${UM.duration(t.avg_duration_seconds)} de media</div></div>
    <div><div class="label">Ingresos hoy</div><div class="big">${UM.money(t.revenue).replace(/\s?€/, '')}<span style="font-size:18px"> €</span></div><div class="sub">${UM.num(t.distance_meters / 1000, 1)} km</div></div>
    <div class="${deficits ? 'alert' : ''}"><div class="label">Zonas en déficit</div><div class="big">${deficits}</div><div class="sub">${maint} en mantenimiento</div></div>`);

  const order = ['AVAILABLE', 'RESERVED', 'IN_USE', 'LOW_BATTERY', 'WITHDRAWN', 'CHARGING', 'REPAIRING', 'READY_FOR_SERVICE', 'RETIRED'];
  set('fleet-state', hbars(order.filter((s) => byState[s] != null).map((s) => ({ s, n: byState[s] })), (x) => UM.STATES[x.s], (x) => x.n));

  const zoneMax = Math.max(1, ...d.fleetByZone.map((z) => ['available', 'reserved', 'in_use', 'low_battery', 'maintenance'].reduce((s, k) => s + Number(z[k] || 0), 0)));
  set('fleet-zone', d.fleetByZone.map((z) => {
    const segs = [['AVAILABLE', z.available], ['RESERVED', z.reserved], ['IN_USE', z.in_use], ['LOW_BATTERY', z.low_battery], ['MAINT', z.maintenance]];
    const sum = segs.reduce((s, [, n]) => s + Number(n || 0), 0);
    return `<div class="zone-row"><span>${UM.esc(zn(z.zone_id))}</span>
      <span class="stack-bar" style="width:${Math.max(2, (sum / zoneMax) * 100)}%">${segs.filter(([, n]) => Number(n) > 0).map(([k, n]) => `<span class="seg-${k}" style="flex:${n}" title="${k}: ${n}"></span>`).join('')}</span>
      <span class="mono" style="text-align:right">${sum}</span></div>`;
  }).join('') || '<p class="empty">Sin datos.</p>');

  const hours = Array.from({ length: 24 }, (_, h) => Number(d.tripsByHour.find((x) => Number(x.hour) === h)?.trips || 0));
  const hmax = Math.max(1, ...hours);
  set('trips-hour', `<div class="cols-chart">${hours.map((n, h) => `<div class="${n ? '' : 'zero'}" style="height:${n ? (n / hmax) * 100 : 1}%" title="${h}:00 · ${n} viajes"></div>`).join('')}</div>
    <div class="axis">${hours.map((_, h) => `<span>${h % 3 === 0 ? h : ''}</span>`).join('')}</div>`);

  set('reservations', `${hbars([['Completadas', r.completed], ['Canceladas', r.cancelled], ['Expiradas', r.expired], ['Rechazadas', r.rejected]].map(([l, n]) => ({ l, n: Number(n) })), (x) => x.l, (x) => x.n)}
    <p class="muted" style="font-size:12px;margin:12px 0 0">Conversión a viaje: ${r.reserved ? Math.round((r.completed / r.reserved) * 100) : 0} %</p>`);

  set('demand', `<div class="table-wrap"><table><thead><tr><th>Zona</th><th class="num">Disponibles</th><th class="num">Reservas</th><th class="num">Rechazos</th><th class="num">Viajes</th><th>Estado</th></tr></thead><tbody>
    ${d.demand.map((z) => `<tr class="${z.status === 'DEFICIT' ? 'deficit' : ''}"><td>${UM.esc(z.zone_name)}</td><td class="num">${z.available_now}</td><td class="num">${z.reservations_last_hour}</td><td class="num">${z.rejections_last_hour}</td><td class="num">${z.rides_started_last_hour}</td><td>${UM.state(z.status)}</td></tr>`).join('')}
    </tbody></table></div>`);

  set('routes', d.topRoutes.length ? hbars(d.topRoutes, (x) => `${UM.esc(zn(x.origin_zone_id))} → ${UM.esc(zn(x.destination_zone_id))}`, (x) => Number(x.trips))
    : '<p class="empty">Aún no hay viajes.</p>');
  document.querySelectorAll('#routes .hbar').forEach((el) => { el.style.gridTemplateColumns = 'minmax(0,1.4fr) 1fr 36px'; });

  const m = d.maintenance[0] || {};
  set('maintenance', `<div class="figures">
    <div><div class="big">${m.battery_low || 0}</div><p class="label">Baterías bajas</p></div>
    <div><div class="big">${m.incidents || 0}</div><p class="label">Incidencias</p></div>
    <div><div class="big">${m.orders_opened || 0}</div><p class="label">Órdenes abiertas</p></div>
    <div><div class="big">${m.returned || 0}</div><p class="label">Vueltas a la calle</p></div>
    <div><div class="big">${m.avg_minutes_out != null ? UM.num(m.avg_minutes_out, 1) : '—'}</div><p class="label">Min fuera de servicio</p></div></div>`);

  const rel = d.relocation;
  set('relocation', `<div class="figures">
    <div><div class="big">${rel.created}</div><p class="label">Órdenes creadas</p></div>
    <div><div class="big">${rel.completed}</div><p class="label">Completadas</p></div>
    <div><div class="big">${rel.vehicles_moved}</div><p class="label">Vehículos movidos</p></div>
    <div><div class="big">${rel.avg_minutes != null ? UM.num(rel.avg_minutes, 1) : '—'}</div><p class="label">Min hasta completar</p></div></div>`);

  set('events', d.eventsLastHour.length ? hbars(d.eventsLastHour.slice(0, 12), (x) => `<span class="mono" style="font-size:11px">${UM.esc(x.event_type)}</span>`, (x) => Number(x.total))
    : '<p class="empty">Sin eventos en la última hora.</p>');
  document.querySelectorAll('#events .hbar').forEach((el) => { el.style.gridTemplateColumns = 'minmax(0,1.6fr) 1fr 36px'; });
}

async function health() {
  let h;
  try { h = await UM.api('/api/analytics/health'); } catch { return; }
  const up = h.services.filter((s) => s.status === 'UP').length;
  set('svc-count', `${up}/${h.services.length} activos · ${h.failedEvents} eventos en DLQ`);
  set('services', h.services.map((s) => `<div class="${s.status === 'UP' ? '' : 'down'}"><span>${UM.esc(s.service)}</span><span class="ms">${s.status === 'UP' ? `${s.latencyMs} ms` : 'caído'}</span></div>`).join(''));
  set('queues', h.queues.sort((a, b) => a.queue.localeCompare(b.queue)).map((q) => `<tr><td class="mono" style="font-size:12px">${UM.esc(q.queue)}${q.dlq ? ' <span class="muted">· DLQ</span>' : ''}</td><td class="num">${q.messages}</td></tr>`).join(''));
}
