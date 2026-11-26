"use strict";
const PRICE={map:null,marks:[],pts:{shop:null,a:null,b:null},chosen:"shop"};
const cash=n=>Number(n||0).toLocaleString("pt-BR",{style:"currency",currency:"BRL"});
function pricingView(){
 const d=STORE.data.pricing||{},t=d.tariff||{},w=d.waiting||{},can=isManager();
 $("#main").innerHTML='<div class="hero"><div><div class="hero-eyebrow">UP ENTREGAS • VALORES</div><h2>Tarifas, rotas e espera</h2><p>Remuneração do entregador independente da taxa cobrada pela loja.</p></div><div class="hero-mark">R$</div></div>'+
 '<div class="ops-grid" style="margin-top:15px"><section class="panel"><h2>Tarifa por distância</h2>'+
 '<p class="small">O UP paga o maior valor entre mínimo e quilômetros multiplicados pela tarifa por km.</p>'+
 '<label for="priceMin">Mínimo (R$)</label><input class="field" id="priceMin" type="number" step="0.01" min="0" value="'+(d.tariff?Number(t.minimum_amount).toFixed(2):"")+'" '+(can?"":"disabled")+'>'+
 '<label for="priceKm">R$ por quilômetro</label><input class="field" id="priceKm" type="number" step="0.01" min="0" value="'+(d.tariff?Number(t.per_km).toFixed(2):"")+'" '+(can?"":"disabled")+'>'+
 '<label class="up-priced-check"><input id="priceActive" type="checkbox" '+(t.enabled?"checked ":"")+(can?"":"disabled")+'><span>Ativar em novas ofertas</span></label>'+
 (can?'<button class="button primary" data-action="price-save">Salvar tarifa</button>':'')+
 '<p class="small">Tarifa '+(t.enabled?"ativa":"não habilitada")+'. Nenhuma entrega anterior será recalculada.</p></section>'+
 '<section class="panel"><h2>Espera do entregador</h2>'+
 '<label for="waitBlock">Minutos por bloco</label><input class="field" id="waitBlock" type="number" min="1" max="60" step="1" value="'+(w.block_minutes??5)+'" '+(can?"":"disabled")+'>'+
 '<label for="waitMoney">Valor por bloco completo (R$)</label><input class="field" id="waitMoney" type="number" min="0" max="100" step="0.01" value="'+Number(w.block_amount??1).toFixed(2)+'" '+(can?"":"disabled")+'>'+
 (can?'<button class="button secondary" data-action="wait-save">Guardar espera</button>':'')+
 '<p class="small">Cada agrupamento aceito reinicia a contagem. Valores já conquistados ficam registrados.</p>'+
 '<div class="up-priced-note">Parâmetros para simulação. A cobrança automática ainda NÃO está ligada à carteira.</div></section></div>'+
 '<section class="panel" style="margin-top:14px"><h2>Simulador de rotas</h2>'+
 '<p class="small">Escolha um ponto, toque no mapa e calcule. Cliente B é opcional para simular agrupamento; não despacha nenhuma corrida.</p>'+
 '<div class="actions"><button class="button secondary" data-action="price-point" data-point="shop">Loja</button>'+
 '<button class="button secondary" data-action="price-point" data-point="a">Cliente A</button>'+
 '<button class="button secondary" data-action="price-point" data-point="b">Cliente B</button></div>'+
 '<p id="priceHint" class="small">Selecionado: loja.</p><div id="priceMap" class="up-priced-map"></div>'+
 '<div class="actions" style="margin-top:10px"><button class="button primary" data-action="price-route">Calcular percurso</button>'+
 '<button class="button secondary" data-action="price-clear">Limpar</button></div>'+
 '<div id="priceResult" class="up-priced-note">Nenhuma rota calculada. O sistema só consulta quando solicitado.</div>'+
 '<p class="small">OSRM público é usado apenas na simulação. Não inclui trânsito em tempo real e não constitui serviço de rotas garantido para produção.</p></section>';

 $("#main").insertAdjacentHTML("beforeend",'<section class="panel">'+
  '<h2>Simulador de espera com agrupamento</h2>'+
  '<p class="small">Informe os minutos antes e depois do aceite de outro pedido. O contador reinicia sem perder blocos completos.</p>'+
  '<div class="rate-grid"><label class="rate-label">Minutos antes do agrupamento'+
  '<input type="number" class="field" id="priceBefore" min="0" step="0.1" value="5.1"></label>'+
  '<label class="rate-label">Minutos após o agrupamento'+
  '<input type="number" class="field" id="priceAfter" min="0" step="0.1" value="4"></label></div>'+
  '<button class="button secondary" data-action="wait-sim">Calcular espera</button>'+
  '<div id="priceWaitSim" class="rate-result">Simulação sem efeito no financeiro.</div></section>');
 makePriceMap();
}
function makePriceMap(){
 PRICE.map?.remove();PRICE.map=null;PRICE.marks=[];
 if(!window.maplibregl){$("#priceMap").textContent="Mapa vetorial indisponível. Confira a conexão.";return}
 PRICE.map=new maplibregl.Map({container:"priceMap",style:"https://tiles.openfreemap.org/styles/positron",center:[-54.620,-20.469],zoom:12,attributionControl:true});
 PRICE.map.addControl(new maplibregl.NavigationControl({showCompass:false}),"top-right");
 PRICE.map.on("click",e=>{
  PRICE.pts[PRICE.chosen]={lat:+e.lngLat.lat.toFixed(6),lng:+e.lngLat.lng.toFixed(6)};
  PRICE.marks.forEach(m=>m.remove());PRICE.marks=[];
  for(const [k,p] of Object.entries(PRICE.pts))if(p)PRICE.marks.push(new maplibregl.Marker({color:k==="shop"?"#147b61":"#2457d6"}).setLngLat([p.lng,p.lat]).addTo(PRICE.map));
  $("#priceHint").textContent="Ponto marcado. Escolha outro ou calcule a rota.";
 });
}
function drawPriceRoute(r){
 const map=PRICE.map,coords=r.route_coordinates;if(!map||!coords?.length)return;
 const geo={type:"Feature",properties:{},geometry:{type:"LineString",coordinates:coords}};
 const f=()=>{if(map.getSource("route"))map.getSource("route").setData(geo);
 else{map.addSource("route",{type:"geojson",data:geo});map.addLayer({id:"route",type:"line",source:"route",paint:{"line-color":"#2563eb","line-width":5}});}
 const b=new maplibregl.LngLatBounds();coords.forEach(c=>b.extend(c));map.fitBounds(b,{padding:40,maxZoom:15});};
 if(map.isStyleLoaded())f();else map.once("load",f);
}
async function calculatePriceRoute(){
 const {shop,a,b}=PRICE.pts;if(!shop||!a){notice("Marque a loja e o cliente A.",true);return}
 $("#priceResult").textContent="Calculando pelas ruas…";
 try{
  const layouts=b?[[shop,a,b],[shop,b,a]]:[[shop,a]];
  const results=await Promise.all(layouts.map(points=>call("route_preview",{points})));
  const selected=results.map((r,i)=>({r,i})).sort((x,y)=>x.r.distance_km-y.r.distance_km)[0],r=selected.r;
  drawPriceRoute(r);
  const t=STORE.data.pricing.tariff;
  const pay=t?.enabled?Math.max(Number(t.minimum_amount),Number(t.per_km)*r.distance_km):null;
  $("#priceResult").textContent=r.distance_km.toFixed(2)+" km • "+r.duration_minutes.toFixed(1)+" min estimados. "+
   (b?"Menor trajeto simulado: loja, "+(selected.i?"B, A. ":"A, B. "):"")+
   (pay===null?"Tarifa não ativa. ":"Referência da tarifa: "+cash(pay)+". ")+
   "Agrupamento real ainda não habilitado.";
 }catch(e){$("#priceResult").textContent="Não foi possível obter a rota.";notice(e.message,true)}
}
document.addEventListener("click",async e=>{
 const t=e.target.closest("[data-action]");if(!t||STORE.page!=="pricing")return;
 const a=t.dataset.action;if(!["price-point","price-route","price-clear","price-save","wait-save","wait-sim"].includes(a))return;
 try{

  if(a==="wait-sim"){
   const block=Number($("#waitBlock").value),amt=Number($("#waitMoney").value),
    before=Number($("#priceBefore").value),after=Number($("#priceAfter").value);
   if(![block,amt,before,after].every(Number.isFinite)||block<=0||amt<0||before<0||after<0)
    throw Error("Tempos ou valores inválidos.");
   const first=Math.floor((before*60)/(block*60))*amt;
   const second=Math.floor((after*60)/(block*60))*amt;
   $("#priceWaitSim").textContent="Antes: "+cash(first)+" + novo ciclo: "+cash(second)+
    " = "+cash(first+second)+". Blocos anteriores preservados; nova tolerância iniciada.";
  }
  if(a==="price-point"){PRICE.chosen=t.dataset.point;$("#priceHint").textContent="Selecione no mapa: "+({shop:"loja",a:"cliente A",b:"cliente B"})[PRICE.chosen];}
  if(a==="price-route")await calculatePriceRoute();
  if(a==="price-clear"){PRICE.pts={shop:null,a:null,b:null};PRICE.chosen="shop";makePriceMap();$("#priceResult").textContent="Pontos apagados.";}
  if(a==="price-save"){
   const v=$("#priceMin").value,k=$("#priceKm").value;
   if(!v||!k){notice("Informe os dois valores da tarifa.",true);return}
   if(!confirm("Salvar tarifa para novas ofertas, sem alterar entregas antigas?"))return;
   await call("tariff_save",{minimum_amount:Number(v),per_km:Number(k),enabled:$("#priceActive").checked});
   notice("Tarifa gravada.");STORE.data.pricing=await call("pricing_policy_get");pricingView();
  }
  if(a==="wait-save"){
   await call("waiting_policy_save",{block_minutes:Number($("#waitBlock").value),block_amount:Number($("#waitMoney").value)});
   notice("Espera parametrizada. Cobrança permanece desligada.");
   STORE.data.pricing=await call("pricing_policy_get");pricingView();
  }
 }catch(err){notice(err.message||"Erro ao aplicar configuração.",true)}
});
