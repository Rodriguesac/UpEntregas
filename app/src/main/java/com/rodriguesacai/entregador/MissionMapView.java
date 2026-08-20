package com.rodriguesacai.entregador;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class MissionMapView {
    public static final class Point {
        public final String label;
        public final String subtitle;
        public final double lat;
        public final double lng;
        public final String kind;
        public final int number;

        public Point(String label, String subtitle, double lat, double lng, String kind, int number) {
            this.label = label == null ? "" : label;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.lat = lat;
            this.lng = lng;
            this.kind = kind == null ? "stop" : kind;
            this.number = number;
        }

        public boolean valid() { return Math.abs(lat) > 0.000001 && Math.abs(lng) > 0.000001; }
    }

    private MissionMapView() {}

    public static LinearLayout premiumCard(Context c, List<Point> source, int activeStop) {
        return card(c, null, source, activeStop);
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static LinearLayout card(Context c, String title, List<Point> source, int activeStop) {
        ArrayList<Point> points = new ArrayList<>();
        if (source != null) for (Point p : source) if (p != null && p.valid()) points.add(p);

        boolean minimal = title == null;
        LinearLayout card = Ui.card(c);
        if (minimal) {
            card.setPadding(Ui.dp(c, 0), Ui.dp(c, 0), Ui.dp(c, 0), Ui.dp(c, 0));
        } else {
            card.addView(Ui.eyebrow(c, "Mapa da missão"));
            card.addView(Ui.text(c, title == null || title.isEmpty() ? "Rota" : title, 18, true));
        }

        if (points.isEmpty()) {
            card.addView(Ui.muted(c, "A localização desta missão ainda não tem coordenadas. Use o botão Navegar para abrir o endereço.", 12));
            return card;
        }

        WebView web = new WebView(c);
        web.setBackground(Ui.rounded(c, R.color.up_surface_alt, 18, R.color.up_border, 1));
        web.setClipToOutline(true);
        web.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
        WebSettings st = web.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setLoadsImagesAutomatically(true);
        st.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient());

        JSONArray arr = new JSONArray();
        try {
            for (Point p : points) {
                JSONObject o = new JSONObject();
                o.put("label", p.label);
                o.put("subtitle", p.subtitle);
                o.put("lat", p.lat);
                o.put("lng", p.lng);
                o.put("kind", p.kind);
                o.put("number", p.number);
                arr.put(o);
            }
        } catch (Exception ignored) {}

        boolean dark = (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        String tiles = dark
                ? "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
                : "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png";
        String attribution = dark ? "© OpenStreetMap © CARTO" : "© OpenStreetMap";

        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'>" +
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'>" +
                "<style>html,body,#map{height:100%;margin:0;background:#07111F} .leaflet-control-attribution{font-size:8px;background:#07111fcc!important;color:#9AA9BD!important} .leaflet-control-attribution a{color:#80A8FF!important} " +
                ".pin{width:32px;height:32px;border-radius:50%;display:flex;align-items:center;justify-content:center;color:#fff;font:700 13px sans-serif;border:2px solid #fff;box-shadow:0 4px 12px #0009;background:#667085}.store{background:#FFC928;color:#07111F}.active{background:#2877FF;color:#fff;box-shadow:0 0 0 7px #2877FF33,0 4px 14px #000b}</style>" +
                "</head><body><div id='map'></div><script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script><script>" +
                "const pts=" + arr.toString() + ";const active=" + activeStop + ";" +
                "const h=s=>String(s||'').replace(/[&<>\"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]));" +
                "const map=L.map('map',{zoomControl:true,attributionControl:true});" +
                "L.tileLayer('" + tiles + "',{maxZoom:19,attribution:'" + attribution + "'}).addTo(map);" +
                "const bounds=[];pts.forEach((p,i)=>{bounds.push([p.lat,p.lng]);" +
                "let cls=p.kind==='store'?'store':((p.number===active+1||p.kind==='active')?'active':'');let txt=p.kind==='store'?'L':(p.kind==='driver'?'●':(p.number||i));" +
                "const ic=L.divIcon({className:'',html:`<div class=\"pin ${cls}\">${txt}</div>`,iconSize:[36,36],iconAnchor:[18,18]});" +
                "L.marker([p.lat,p.lng],{icon:ic}).addTo(map).bindPopup(`<b>${h(p.label)}</b><br>${h(p.subtitle||'')}`);});" +
                "const driver=pts.find(p=>p.kind==='driver'),store=pts.find(p=>p.kind==='store'),stops=pts.filter(p=>p.kind==='stop'||p.kind==='active').sort((a,b)=>(a.number||0)-(b.number||0));" +
                "const target=active<0?store:stops.find(p=>(p.number||0)===active+1);" +
                "const future=active<0?stops:stops.filter(p=>(p.number||0)>active+1);" +
                "const drawRoute=(arr,color,weight,opacity)=>{if(!arr||arr.length<2)return;const fallback=()=>L.polyline(arr.map(p=>[p.lat,p.lng]),{color,weight,opacity,dashArray:color==='#2877FF'?null:'7 7'}).addTo(map);const coords=arr.map(p=>`${p.lng},${p.lat}`).join(';');fetch(`https://router.project-osrm.org/route/v1/driving/${coords}?overview=full&geometries=geojson`).then(r=>r.ok?r.json():Promise.reject()).then(j=>{const g=j&&j.routes&&j.routes[0]&&j.routes[0].geometry;if(g)L.geoJSON(g,{style:{color,weight,opacity}}).addTo(map);else fallback();}).catch(fallback);};" +
                "if(driver&&target)drawRoute([driver,target],'#2877FF',6,.96);else if(store&&target&&active>=0)drawRoute([store,target],'#2877FF',6,.96);" +
                "if(target&&future.length)drawRoute([target,...future],'#7F8A99',4,.62);else if(active<0&&store&&future.length)drawRoute([store,...future],'#7F8A99',4,.62);" +
                "if(bounds.length===1)map.setView(bounds[0],15);else map.fitBounds(bounds,{padding:[24,24]});" +
                "</script></body></html>";
        web.loadDataWithBaseURL("https://unpkg.com/", html, "text/html", "UTF-8", null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, minimal ? 300 : 310));
        lp.setMargins(0, minimal ? 0 : Ui.dp(c, 12), 0, 0);
        web.setLayoutParams(lp);
        card.addView(web);
        return card;
    }
}
