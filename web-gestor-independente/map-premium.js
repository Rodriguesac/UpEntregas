"use strict";
// OpenFreeMap vector map: preserve the previously loaded OSM raster map as fallback.
// The map receives ONLY short-lived, authorized online-courier coordinates from UP.
(() => {
  const fallback = window.UPMap;
  const gl = window.maplibregl;
  if (!fallback || !gl || typeof gl.Map !== "function" ||
      (typeof gl.supported === "function" && !gl.supported())) return;
  const DEFAULT = [-54.6201, -20.4697]; // City center, never an inferred courier GPS.
  let map = null, element = null, ready = false, staff = [], selected = null, markers = [];
  const valid = x => x && Number.isFinite(Number(x.lat)) && Number.isFinite(Number(x.lng)) &&
      Math.abs(Number(x.lat)) <= 85 && Math.abs(Number(x.lng)) <= 180;
  const safeName = x => String(x && (x.name || x.display_name) || "Entregador").trim().slice(0,80);
  function summary() {
    const line = document.getElementById("upMapSelected");
    const zoom = document.getElementById("upMapZoom");
    const found = staff.find(x => String(x.id) === String(selected));
    if (line) {
      let message = "Toque em uma moto para consultar o entregador.";
      if (found) {
        const time = Date.parse(found.last_location_at || "");
        const at = Number.isFinite(time) ? new Date(time).toLocaleTimeString("pt-BR", {hour:"2-digit",minute:"2-digit"}) : "indisponível";
        message = safeName(found) + " • " + (found.mission_status ? "Em corrida" : "Disponível") + " • GPS " + at;
      } else if (!staff.length) {
        message = "Mapa de Campo Grande • nenhum GPS recente disponível.";
      }
      line.textContent = message;
    }
    if (zoom && map) zoom.textContent = "Zoom " + map.getZoom().toFixed(1);
  }
  function clearMarkers() {
    for (const pin of markers) pin.remove();
    markers = [];
  }
  function draw() {
    if (!map || !ready) return;
    clearMarkers();
    for (const person of staff) {
      const outer = document.createElement("div");
      outer.className = "up-vector-marker";
      const button = document.createElement("button");
      button.type = "button";
      button.className = "up-vector-pin" + (person.mission_status ? " busy" : "");
      button.setAttribute("aria-label", safeName(person) + " • " + (person.mission_status ? "Em corrida" : "Disponível"));
      button.title = safeName(person);
      button.textContent = "●";
      button.addEventListener("click", () => select(person.id));
      outer.appendChild(button);
      const marker = new gl.Marker({element:outer,anchor:"bottom"})
        .setLngLat([Number(person.lng), Number(person.lat)]).addTo(map);
      markers.push(marker);
    }
    summary();
  }
  function fit() {
    if (!map || !ready) return;
    if (!staff.length) {
      map.jumpTo({center:DEFAULT,zoom:12});
    } else if (staff.length === 1) {
      map.jumpTo({center:[Number(staff[0].lng),Number(staff[0].lat)],zoom:14});
    } else {
      const bounds = new gl.LngLatBounds();
      staff.forEach(p => bounds.extend([Number(p.lng),Number(p.lat)]));
      map.fitBounds(bounds,{padding:55,maxZoom:15,duration:0});
    }
    summary();
  }
  function enableLegacy() {
    const current = element, focus = selected, copy = staff;
    try { if (map) map.remove(); } catch (_) {}
    map = null; element = null; ready = false; clearMarkers();
    window.UPMap = fallback;
    if (current && document.getElementById("upMapCanvas") === current) fallback.mount(copy,focus);
  }
  function mount(items,focusId) {
    const node = document.getElementById("upMapCanvas");
    if (!node) return;
    staff = (Array.isArray(items) ? items : []).filter(valid).map(x => ({...x}));
    selected = focusId || (staff.some(x => String(x.id) === String(selected)) ? selected : null);
    if (!map || element !== node) {
      if (map) {
        clearMarkers();
        try { map.remove(); } catch (_) {}
      }
      element = node; ready = false;
      try {
        map = new gl.Map({
          container:node,
          style:"https://tiles.openfreemap.org/styles/liberty",
          center:DEFAULT, zoom:12, maxZoom:18,
          attributionControl:false, dragRotate:false, pitchWithRotate:false
        });
        map.addControl(new gl.AttributionControl({
          compact:true, customAttribution:"© OpenStreetMap contributors · OpenFreeMap"
        }),"bottom-right");
        map.on("load", () => {
          ready = true;
          draw();
          if (selected) select(selected); else fit();
        });
        map.on("zoomend",summary);
        map.on("error",() => { if (!ready) enableLegacy(); });
      } catch (_) {
        enableLegacy();
        return;
      }
    } else {
      draw();
      if (selected) select(selected);
      setTimeout(() => { if (map) map.resize(); },60);
    }
    summary();
  }
  function select(id) {
    selected = id;
    const person = staff.find(x => String(x.id) === String(id));
    if (person && map && ready) {
      map.easeTo({center:[Number(person.lng),Number(person.lat)],zoom:Math.max(14,map.getZoom()),duration:220});
    }
    summary();
  }
  function zoom(direction) {
    if (map) map.zoomTo(Math.max(3,Math.min(18,map.getZoom()+direction)),{duration:180});
  }
  function recenter() {
    selected = null;
    fit();
  }
  window.UPMap = Object.freeze({mount,zoom,select,recenter});
})();
