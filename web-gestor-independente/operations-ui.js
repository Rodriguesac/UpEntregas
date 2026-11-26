"use strict";
// UP Gestor visual layer. All data comes from authenticated UP manager endpoints.
(()=> {
 const hero=(eyebrow,title,description)=>'<div class="hero"><div>'+
 '<div class="hero-eyebrow">'+escapeHtml(eyebrow)+'</div>'+
 '<h2>'+escapeHtml(title)+'</h2><p>'+escapeHtml(description)+'</p></div>'+
 '<div class="hero-mark" aria-hidden="true">UP↗</div></div>';
 const stat=(title,value,sub,icon)=>'<div class="stat"><div class="stat-icon">'+
 svgIcon(icon)+'</div><div class="k">'+escapeHtml(title)+'</div><strong>'+
 escapeHtml(value)+'</strong><div class="foot">'+escapeHtml(sub)+'</div></div>';
 const stats=(...cards)=>'<div class="stat-grid">'+cards.join("")+'</div>';
 const statusAge=raw=>{
   const time=Date.parse(raw||"");
   return Number.isFinite(time)&&time<=Date.now()+30000?Date.now()-time:Infinity;
 };
 const opsLabel=state=>label(state);

 function overview(){
   const metrics=STORE.data.overview?.metrics||{};
   const attention=Array.isArray(STORE.data.overview?.attention)?STORE.data.overview.attention:[];
   const greeting=(STORE.user?.name||"equipe UP").trim().split(/\s+/)[0]||"equipe UP";
   $("#main").innerHTML=hero("UP ENTREGAS • CENTRAL OPERACIONAL",
     "Bem-vindo, "+greeting+".","Suas corridas, entregadores e pendências em uma única visão.")+
    stats(
      stat("Corridas ativas",metrics.active??0,"Ainda não finalizadas","jobs"),
      stat("Sem entregador",metrics.awaiting??0,"Fila e ofertas pendentes","occurrences"),
      stat("Entregadores online",metrics.online??0,"Aprovados e disponíveis","couriers"),
      stat("Cadastros pendentes",metrics.pending??0,"Aguardando análise","applications")
    )+
    '<div class="ops-grid" style="margin-top:16px"><section class="panel">'+
    '<div class="section-head" style="margin-top:0"><h2>Precisa de atenção</h2>'+
    '<span class="pill '+(attention.length?"wait":"good")+'">'+
    (attention.length?attention.length+" registros":"Sem alertas")+'</span></div>'+
    (attention.length?'<div class="priority-list">'+attention.map(j=>
      '<div class="priority-item"><div class="priority-mark">!</div>'+
      '<div style="min-width:0;flex:1"><strong>Corrida #'+
      escapeHtml(j.code||String(j.id).slice(0,8))+'</strong>'+
      '<small>'+escapeHtml(opsLabel(j.status))+' • '+date(j.updated_at)+'</small></div>'+
      '<button type="button" class="button secondary" data-action="view-job" data-id="'+
      escapeHtml(j.id)+'">Detalhes</button></div>'
    ).join("")+'</div>':empty("Nenhuma corrida na lista de atenção."))+
    '</section><section class="panel"><div class="section-head" style="margin-top:0"><h2>Resumo operacional</h2></div>'+
    '<div class="details-grid">'+smallInfo("Banco de dados","UP independente")+
    smallInfo("Repasses solicitados",String(metrics.payout_pending??0))+
    smallInfo("Posições no mapa","Somente GPS atual de quem está online")+
    smallInfo("Atualização","Econômica, com painel aberto")+'</div>'+
    '<p class="meta-line">As posições GPS são consultadas somente quando o gestor abre a área Mapa.</p>'+
    '</section></div>'+
    (STORE.data.overview?.sampled?
    '<div class="warning-note" style="margin-top:14px">Indicadores parciais: o banco limitou os registros desta consulta.</div>':"");
 }

 function jobs(){
   const data=STORE.data.jobs?.jobs||[],f=STORE.filter;
   const validFilter=["all","active","waiting","done","rejected"].includes(f)?f:"all";
   const rows=data.filter(j=>validFilter==="all"||
     (validFilter==="active"&&!["DELIVERED","FAILED","CANCELLED"].includes(j.status))||
     (validFilter==="waiting"&&["WAITING_DRIVER","OFFERED"].includes(j.status))||
     (validFilter==="done"&&["DELIVERED","FAILED","CANCELLED"].includes(j.status)));
   const offers=STORE.data.offer_history?.offers||[];
   $("#main").innerHTML=hero("CENTRAL DE DESPACHO","Corridas e ofertas",
     "Acompanhe cada etapa, recusa e expiração sem misturar valores financeiros.")+
     (isDispatcher()?'<div class="actions" style="margin:16px 0"><button class="button primary" data-action="new-job">Solicitar entrega para uma loja</button></div>':"")+
     '<div class="chips">'+[
       ["all","Todas"],["active","Ativas"],["waiting","Aguardando"],
       ["done","Finalizadas"],["rejected","Recusadas / expiradas"]
     ].map(([id,title])=>'<button type="button" class="chip '+(validFilter===id?"on":"")+
        '" data-action="filter" data-filter="'+id+'">'+title+'</button>').join("")+'</div>'+
     (validFilter==="rejected"?
       (offers.length?'<div class="offers-feed">'+offers.map(o=>
         '<article class="offer-event"><div class="event-copy"><strong>Pedido #'+
         escapeHtml(o.job_code||"—")+'</strong>'+
         '<small>'+escapeHtml(o.courier||"Entregador")+'</small>'+
         '<small>'+date(o.responded_at||o.expires_at||o.created_at)+'</small>'+
         '<button type="button" class="button secondary" data-action="view-job" data-id="'+
         escapeHtml(o.job_id)+'">Ver corrida</button></div>'+badge(o.status)+'</article>'
        ).join("")+'</div>':
        empty(STORE.data.offer_history?"Nenhuma oferta recusada ou expirada registrada.":
          "Consultando histórico de recusas…")):
       (rows.map(jobCard).join("")||empty("Nenhuma corrida neste filtro.")));
 }

 function couriers(){
   const list=STORE.data.couriers?.couriers||[];
   const connected=list.filter(c=>c.online&&c.state==="APPROVED");
   const fresh=connected.filter(c=>statusAge(c.last_location_at)<120000);
   $("#main").innerHTML=hero("EQUIPE DE ENTREGA","Entregadores",
     "Disponibilidade, GPS e informações do aplicativo em um único lugar.")+
   stats(
     stat("Cadastrados",list.length,"Registros na consulta","couriers"),
     stat("Online",connected.length,"Disponíveis e aprovados","map"),
     stat("GPS recente",fresh.length,"Recebido nos últimos 2 min","overview"),
     stat("Suspensos",list.filter(c=>c.state==="SUSPENDED").length,"Sem novas ofertas","occurrences")
   )+'<div style="margin-top:16px">'+
   (list.map(c=>'<article class="record"><div class="record-head"><div><h3>'+
       escapeHtml(c.display_name||"Entregador")+'</h3>'+
       '<div class="tag-row" style="margin-top:9px"><span class="status-line">'+
       '<span class="status-dot'+(c.online?"":" off")+'"></span>'+
       (c.online?"Online":"Offline")+'</span>'+badge(c.state)+'</div></div>'+
       '<span class="pill '+(statusAge(c.last_location_at)<120000?"good":"wait")+'">'+
       (statusAge(c.last_location_at)<120000?"GPS recente":"GPS não recente")+
       '</span></div><p class="meta-line">Último GPS: '+date(c.last_location_at)+'</p>'+
       (c.state==="PENDING"?'<div class="warning-note">Confira o cadastro antes de aprovar.</div>':"")+
       (isManager()?'<div class="actions">'+
        '<button class="button secondary" data-action="driver-diagnostics" data-id="'+
        escapeHtml(c.id)+'">Diagnóstico do app</button>'+
        '<button class="button '+(c.state==="SUSPENDED"||c.state==="PENDING"?"green":"secondary")+
        '" data-action="driver-state" data-id="'+escapeHtml(c.id)+'" data-state="'+
        (c.state==="SUSPENDED"||c.state==="PENDING"?"APPROVED":"SUSPENDED")+
        '" data-review-required="'+(c.state==="PENDING"?"true":"false")+'">'+
        (c.state==="PENDING"?"Revisar e aprovar":c.state==="SUSPENDED"?"Reativar":"Suspender")+
        '</button></div>':"")+'</article>').join("")||
     empty("Nenhum entregador cadastrado."))+'</div>';
 }

 function map(){
   const result=STORE.data.map||{};
   const staff=Array.isArray(result.couriers)?result.couriers:[];
   const online=Number(result.total_online||0);
   $("#main").innerHTML=hero("GPS OPERACIONAL","Mapa dos entregadores",
      "Posições recebidas nos últimos 5 minutos de entregadores aprovados e online.")+
    stats(
      stat("Online",online,"Entregadores conectados","couriers"),
      stat("Com GPS recente",staff.length,"Posições confirmadas","map"),
      stat("GPS desatualizado",Number(result.stale_or_unavailable||0),"Sem mapa até reconectar","occurrences"),
      stat("Em corrida",staff.filter(c=>c.mission_status).length,"Entre os localizados","jobs")
    )+
    (staff.length?'<div class="map-layout" style="margin-top:16px">'+
     '<section class="panel ops-map-panel">'+
     '<div class="map-toolbar"><strong>Posições atuais</strong><div class="map-controls">'+
     '<button class="map-ctrl" type="button" data-action="map-zoom-in" aria-label="Aproximar mapa">+</button>'+
     '<button class="map-ctrl" type="button" data-action="map-zoom-out" aria-label="Afastar mapa">−</button>'+
     '<button class="map-ctrl" type="button" data-action="map-recenter" aria-label="Centralizar mapa">⌖</button>'+
     '</div></div>'+
     '<div id="upMapCanvas" class="up-map-canvas" aria-label="Mapa operacional com os entregadores online"></div>'+
     '<div class="map-footer"><div id="upMapSelected">Selecione um entregador para ver detalhes.</div>'+
     '<small id="upMapZoom"></small></div></section>'+
     '<aside class="map-list">'+staff.map(c=>
      '<button type="button" class="map-driver" data-action="map-driver" data-id="'+escapeHtml(c.id)+'">'+
      '<strong>'+escapeHtml(c.name||"Entregador")+'</strong>'+
      '<span class="status-line"><span class="status-dot"></span>'+
      escapeHtml(c.mission_status?opsLabel(c.mission_status):"Disponível")+'</span>'+
      '<small>GPS: '+date(c.last_location_at)+'</small></button>'
     ).join("")+'</aside></div>':
     '<div class="panel" style="margin-top:16px">'+
      empty("Nenhuma posição GPS recente e autorizada está disponível. O mapa não inventa localização.")+
     '</div>')+
    '<p class="map-note" style="margin-top:12px">Mapa © OpenStreetMap contributors. O provedor recebe as áreas consultadas. O GPS para de aparecer quando o entregador fica offline ou deixa de enviar posição recente.</p>';
   if(staff.length&&window.UPMap)window.UPMap.mount(staff,STORE.mapFocus);
   STORE.mapFocus=null;
 }

 window.UPGestorV3=Object.freeze({overview,jobs,couriers,map});
})();
