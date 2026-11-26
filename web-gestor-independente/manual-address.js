"use strict";
// Address locator for manually dispatched jobs. Reuses the same
// Campo Grande autocomplete endpoint as the Rodrigues customer website.
(() => {
 const ADDRESS_API="https://fdqqwdplprzpqufpgdrm.supabase.co/functions/v1/rac-address-autocomplete";
 const MAP_STYLE="https://tiles.openfreemap.org/styles/positron";
 const CITY_CENTER={lat:-20.4697,lng:-54.6201}; // city center only; never a guessed client pin
 let current=null;
 const byId=id=>document.getElementById(id);
 const val=id=>String(byId(id)?.value||"").trim();
 const number=x=>Number(x);
 const valid=p=>p&&Number.isFinite(number(p.lat))&&Number.isFinite(number(p.lng))&&
  Math.abs(number(p.lat))<85&&Math.abs(number(p.lng))<=180&&
  !(number(p.lat)===0&&number(p.lng)===0);
 const cap=(v,n)=>String(v||"").trim().slice(0,n);
 const money=x=>Number(x).toLocaleString("pt-BR",{style:"currency",currency:"BRL"});
 const field=(id,label,max=120,extra="")=>
  '<label for="'+id+'">'+label+'</label><input class="field" id="'+id+
  '" maxlength="'+max+'" autocomplete="off" '+extra+'>';
 const setText=(id,text)=>{const e=byId(id);if(e)e.textContent=text};
 const formHtml=points=>{
  const options=points.map((p,i)=>'<option value="'+i+'">'+escapeHtml(p.label)+'</option>').join("");
  return '<p class="manual-info">Localize o destino usando a mesma busca do site do cliente. Confira o número e confirme o pino antes de solicitar.</p>'+
   '<label for="manualPickup">Loja e ponto de retirada</label><select class="field" id="manualPickup">'+options+'</select>'+
   '<div class="manual-two">'+field("manualCode","Número do pedido (4 dígitos)",4,'inputmode="numeric" placeholder="1234"')+
   field("manualPhone","WhatsApp do cliente",20,'type="tel" inputmode="tel"')+'</div>'+
   field("manualCustomer","Nome do cliente",150)+
   '<label for="manualSearch">Buscar endereço, comércio ou CEP</label>'+
   '<input class="field" type="search" id="manualSearch" autocomplete="off" maxlength="140" placeholder="Ex.: Rua Acrópole, 1353" aria-controls="manualResults" aria-expanded="false">'+
   '<div id="manualResults" class="manual-results" role="listbox" hidden></div>'+
   '<p class="manual-hint" id="manualSearchHint" aria-live="polite">Busca de Campo Grande/MS. Também pode preencher a rua e escolher o pino manualmente.</p>'+
   '<div class="manual-two">'+field("manualStreet","Rua de entrega",180)+field("manualNumber","Número",20)+'</div>'+
   '<div class="manual-two">'+field("manualNeighborhood","Bairro",120)+field("manualCity","Cidade",120)+'</div>'+
   '<div class="manual-two">'+field("manualState","UF",2,'placeholder="MS"')+
   field("manualComplement","Complemento (opcional)",150)+'</div>'+
   field("manualReference","Referência (opcional)",150)+
   '<div class="manual-section-head"><strong>Confirme a localização no mapa</strong><span>Arraste o pino ou toque no ponto correto</span></div>'+
   '<div id="manualMap" class="manual-map" role="region" aria-label="Mapa de localização da entrega"></div>'+
   '<p id="manualMapHint" class="manual-hint" aria-live="polite">O pino ainda não foi confirmado. A localização de uma rua pode ser aproximada.</p>'+
   '<div class="manual-actions"><button class="button secondary" type="button" id="manualConfirmPin">Confirmar pino</button>'+
   '<button class="button secondary" type="button" id="manualPreview">Calcular rota</button></div>'+
   '<div id="manualRouteResult" class="manual-route-result" aria-live="polite">Marque a loja e o destino para estimar distância e percurso.</div>'+
   '<p class="manual-hint">Rotas do OSRM/OpenStreetMap são estimativas; sem trânsito ao vivo. Esta tela não cobra frete nem repassa valores automaticamente.</p>';
 };
 function cleanup(){
  if(!current)return;
  if(current.timer)clearTimeout(current.timer);
  current.abort?.abort();
  try{current.map?.remove()}catch{}
  current=null;
 }
 async function open(){
  cleanup();
  const locations=await call("merchant_locations");
  const points=(locations.merchants||[]).flatMap(m=>(m.locations||[]).map(l=>({
   merchant_id:m.id,location_id:l.id,
   label:m.display_name+" — "+l.label+" ("+(l.city||"Sem cidade")+")",
   city:l.city||"Campo Grande",uf:l.state_code||"MS",
   location:valid({lat:Number(l.latitude),lng:Number(l.longitude)})?
    {lat:Number(l.latitude),lng:Number(l.longitude)}:null
  })));
  if(!points.length){notice("Nenhuma loja ativa com ponto de retirada ativo.",true);return}
  const ctx={requestId:crypto.randomUUID(),points,map:null,marker:null,point:null,
   pinConfirmed:false,selected:null,route:null,timer:null,abort:null,serial:0};
  current=ctx;
  const dropoff=()=>{
   const obj={street:val("manualStreet"),number:val("manualNumber"),
    neighborhood:val("manualNeighborhood"),city:val("manualCity"),
    state:val("manualState").toUpperCase(),complement:val("manualComplement"),
    reference:val("manualReference")};
   if(ctx.pinConfirmed&&valid(ctx.point))
    obj.location={lat:ctx.point.lat,lng:ctx.point.lng};
   return obj;
  };
  dialog("Corrida manual • localizar destino",formHtml(points),"Criar corrida",async()=>{
   const selected=points[Number(val("manualPickup"))];
   const code=val("manualCode"),client=val("manualCustomer");
   const phone=val("manualPhone").replace(/\D/g,"");
   const address=dropoff();
   if(!selected)throw Error("Selecione a loja e o ponto de retirada.");
   if(!/^\d{4}$/.test(code))throw Error("Para novas corridas, use um número de pedido de quatro dígitos.");
   if(client.length<2||phone.length<10||phone.length>15)
    throw Error("Confira o nome e o telefone do cliente.");
   if(address.street.length<3||!address.number||address.city.length<2||address.state.length!==2)
    throw Error("Preencha rua, número, cidade e UF da entrega.");
   if(!ctx.pinConfirmed||!valid(ctx.point))
    throw Error("Localize o destino no mapa e toque em Confirmar pino.");
   if(!confirm("Criar corrida manual para "+client+"? A localização marcada será enviada ao entregador; sem cobrança automática."))return false;
   const response=await call("create_manual_job",{
    merchant_id:selected.merchant_id,location_id:selected.location_id,
    request_id:ctx.requestId,order_code:code,
    recipient_name:client,recipient_phone:phone,dropoff:address
   });
   if(!response.job?.job_id)throw Error("O UP não confirmou a corrida.");
   notice(response.job.already_applied?"Corrida existente confirmada.":"Corrida manual criada com destino no mapa.");
   await load("jobs");
  });
  byId("manualCity").value=points[0].city;
  byId("manualState").value=points[0].uf;
  const active=()=>current===ctx;
  function routeReset(){
   ctx.route=null;
   setText("manualRouteResult","Rota ainda não calculada. Sem cobrança automática.");
   if(ctx.map?.getLayer("manualRouteLine"))ctx.map.removeLayer("manualRouteLine");
   if(ctx.map?.getSource("manualRouteLine"))ctx.map.removeSource("manualRouteLine");
  }
  function mark(point,source){
   if(!active()||!valid(point))return;
   ctx.point={lat:Number(Number(point.lat).toFixed(7)),lng:Number(Number(point.lng).toFixed(7))};
   ctx.pinConfirmed=false;
   routeReset();
   if(ctx.map){
    if(ctx.marker)ctx.marker.setLngLat([ctx.point.lng,ctx.point.lat]);
    else{
     ctx.marker=new maplibregl.Marker({color:"#0b8756",draggable:true})
      .setLngLat([ctx.point.lng,ctx.point.lat]).addTo(ctx.map);
     ctx.marker.on("dragend",()=>{const p=ctx.marker.getLngLat();mark({lat:p.lat,lng:p.lng},"Pino arrastado")});
    }
    if(source!=="Toque no mapa")ctx.map.flyTo({center:[ctx.point.lng,ctx.point.lat],zoom:16});
   }
   setText("manualMapHint",source+": posição aproximada. Confirme o pino no local exato da entrega.");
  }
  function selectedPickup(){
   return points[Number(val("manualPickup"))]||points[0];
  }
  const onPickupChange=()=>{
   const p=selectedPickup();
   byId("manualCity").value=p.city;
   byId("manualState").value=p.uf;
   routeReset();
   if(ctx.map&&p.location)ctx.map.flyTo({center:[p.location.lng,p.location.lat],zoom:12});
  };
  byId("manualPickup").addEventListener("change",onPickupChange);
  for(const id of ["manualStreet","manualNumber","manualNeighborhood","manualCity","manualState"]){
   byId(id).addEventListener("input",()=>{
    ctx.pinConfirmed=false;routeReset();
    setText("manualMapHint","Endereço alterado: confira novamente o pino antes de criar a corrida.");
   });
  }
  byId("manualConfirmPin").addEventListener("click",()=>{
   if(!valid(ctx.point)){notice("Selecione a localização do cliente no mapa.",true);return}
   ctx.pinConfirmed=true;
   setText("manualMapHint","Localização confirmada: "+ctx.point.lat.toFixed(6)+", "+ctx.point.lng.toFixed(6)+
    ". Pino salvo somente ao criar a corrida.");
  });
  byId("manualPreview").addEventListener("click",async()=>{
   const origin=selectedPickup().location;
   if(!origin){notice("O ponto de retirada precisa ter GPS cadastrado para calcular a rota.",true);return}
   if(!ctx.pinConfirmed||!valid(ctx.point)){notice("Confirme o pino do cliente primeiro.",true);return}
   const button=byId("manualPreview");button.disabled=true;
   setText("manualRouteResult","Calculando trajeto pelas ruas…");
   try{
    const route=await call("route_preview",{points:[origin,ctx.point]});
    if(!active())return;
    ctx.route=route;
    setText("manualRouteResult",
     route.distance_km.toFixed(2).replace(".",",")+" km pelas ruas • "+
     route.duration_minutes.toFixed(1).replace(".",",")+" min estimados. Sem cobrança automática.");
    if(ctx.map&&Array.isArray(route.route_coordinates)&&route.route_coordinates.length>1){
     const geometry={type:"Feature",properties:{},
      geometry:{type:"LineString",coordinates:route.route_coordinates}};
     const draw=()=>{
      if(!active()||!ctx.map)return;
      if(ctx.map.getSource("manualRouteLine"))ctx.map.getSource("manualRouteLine").setData(geometry);
      else{
       ctx.map.addSource("manualRouteLine",{type:"geojson",data:geometry});
       ctx.map.addLayer({id:"manualRouteLine",type:"line",source:"manualRouteLine",
        paint:{"line-color":"#1761d8","line-width":5,"line-opacity":.88}});
      }
      const bounds=new maplibregl.LngLatBounds();
      route.route_coordinates.forEach(p=>bounds.extend(p));
      ctx.map.fitBounds(bounds,{padding:48,maxZoom:15});
     };
     if(ctx.map.isStyleLoaded())draw();else ctx.map.once("load",draw);
    }
   }catch(e){
    if(active())setText("manualRouteResult","Rota indisponível agora. O pino confirmado continua válido.");
    notice(e.message||"Não foi possível calcular a rota.",true);
   }finally{button.disabled=false}
  });
  const results=byId("manualResults"),queryInput=byId("manualSearch");
  function setHint(message){setText("manualSearchHint",message)}
  function clearSuggestions(){results.hidden=true;results.replaceChildren();queryInput.setAttribute("aria-expanded","false");}
  function choose(result,query){
   const entry=result||{},geo=entry.latlng;
   const street=cap(entry.rua||entry.titulo,180);
   if(street)byId("manualStreet").value=street;
   const typed=String(query).match(/(?:,|\s)\s*(?:n[º°.]?\s*)?(\d{1,5}[a-z]?)\s*$/i);
   const house=cap(entry.numero||(typed?typed[1]:""),20);
   if(house)byId("manualNumber").value=house;
   if(entry.bairro)byId("manualNeighborhood").value=cap(entry.bairro,120);
   byId("manualCity").value="Campo Grande";
   byId("manualState").value="MS";
   queryInput.value=cap(entry.titulo||query,140);
   clearSuggestions();
   setHint("Endereço encontrado. Confira o número e ajuste o pino: a busca pode localizar apenas a rua.");
   if(valid(geo))mark(geo,"Sugestão selecionada");
   else{
    ctx.pinConfirmed=false;routeReset();
    setText("manualMapHint","Endereço sem coordenadas. Toque no ponto correto no mapa e confirme.");
   }
  }
  const search=async(query,token)=>{
   if(!active()||token!==ctx.serial)return;
   const ctrl=new AbortController();ctx.abort=ctrl;
   setHint("Procurando ruas, números e comércios…");
   try{
    const resp=await fetch(ADDRESS_API,{method:"POST",headers:{"content-type":"application/json"},
     credentials:"omit",cache:"no-store",signal:ctrl.signal,body:JSON.stringify({query})});
    const data=await resp.json().catch(()=>({}));
    if(!active()||token!==ctx.serial||val("manualSearch")!==query)return;
    clearSuggestions();
    if(!resp.ok||!data.ok){
     setHint(data.error==="rate_limited"?"Limite de buscas. Digite a rua e posicione o pino manualmente.":
      "Busca temporariamente indisponível. Digite a rua e posicione o pino manualmente.");
     return;
    }
    const list=(Array.isArray(data.suggestions)?data.suggestions:[]).filter(x=>x&&x.titulo).slice(0,6);
    if(!list.length){setHint("Nenhuma sugestão. Informe a rua manualmente e selecione o ponto no mapa.");return}
    for(const item of list){
     const button=document.createElement("button");
     button.type="button";button.className="manual-suggestion";button.setAttribute("role","option");
     const heading=document.createElement("strong");heading.textContent=cap(item.titulo,140);
     const desc=document.createElement("small");
     desc.textContent=[cap(item.rua,150),cap(item.numero,20),cap(item.bairro,100),"Campo Grande/MS"]
      .filter(Boolean).join(" • ");
     button.append(heading,desc);
     button.addEventListener("click",()=>choose(item,query));
     results.append(button);
    }
    results.hidden=false;queryInput.setAttribute("aria-expanded","true");
    setHint("Selecione a sugestão e confira o número e o pino.");
   }catch(e){
    if(e.name!=="AbortError"&&active()&&token===ctx.serial)
     setHint("Sem conexão com a busca. Digite o endereço e marque o ponto no mapa.");
   }
  };
  queryInput.addEventListener("input",()=>{
   ctx.serial++;
   if(ctx.timer)clearTimeout(ctx.timer);
   ctx.abort?.abort();clearSuggestions();
   const query=val("manualSearch");
   if(query.length<4){setHint("Digite pelo menos quatro caracteres da rua, comércio ou CEP.");return}
   const ticket=ctx.serial;
   ctx.timer=setTimeout(()=>search(query,ticket),450);
  });
  queryInput.addEventListener("keydown",event=>{if(event.key==="Escape")clearSuggestions()});
  const gl=window.maplibregl;
  if(!gl||typeof gl.Map!=="function"||
     (typeof gl.supported==="function"&&!gl.supported())){
   setText("manualMapHint","Mapa não carregou. É necessário um navegador compatível para confirmar o destino.");
   return;
  }
  try{
   const p=points[0].location||CITY_CENTER;
   ctx.map=new maplibregl.Map({container:"manualMap",style:MAP_STYLE,
    center:[p.lng,p.lat],zoom:12,attributionControl:true});
   ctx.map.addControl(new maplibregl.NavigationControl({showCompass:false}),"top-right");
   ctx.map.on("click",e=>mark({lat:e.lngLat.lat,lng:e.lngLat.lng},"Toque no mapa"));
   ctx.map.on("error",()=>{if(active())setText("manualMapHint","Não foi possível carregar uma parte do mapa; verifique a conexão.");});
   ctx.map.on("load",()=>{if(active())ctx.map.resize()});
   setTimeout(()=>{if(active())ctx.map.resize()},200);
  }catch(e){setText("manualMapHint","Mapa indisponível. Tente novamente com a conexão ativa.");}
 }
 window.UPManualAddress=Object.freeze({open,cleanup});
})();
