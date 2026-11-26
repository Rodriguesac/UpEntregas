"use strict";
// Lightweight OpenStreetMap tile viewer. Loaded only after an authorized operator
// explicitly opens the map. Never tracks the operator's own location.
// No CDN JavaScript, no map library key, no automatic tracking of offline drivers.
(()=> {
 const TILE=256,MIN=3,MAX=17;
 // Campo Grande is the operational region default, not an inferred driver position.
 const DEFAULT_CENTER={lat:-20.4697,lng:-54.6201};
 const state={center:null,zoom:13,fit:true,items:[],target:null,drag:null};
 const limit=(n,a,b)=>Math.max(a,Math.min(b,n));
 const valid=p=>p&&p.lat!=null&&p.lng!=null&&Number.isFinite(Number(p.lat))&&Number.isFinite(Number(p.lng))&&
   Number(p.lat)>=-85&&Number(p.lat)<=85&&Number(p.lng)>=-180&&Number(p.lng)<=180;
 const projection=(lat,lng,z)=>{
   const n=2**z,rad=limit(lat,-85,85)*Math.PI/180;
   return {x:(Number(lng)+180)/360*n*TILE,
     y:(1-Math.asinh(Math.tan(rad))/Math.PI)/2*n*TILE};
 };
 const inverse=(x,y,z)=>{
   const size=2**z*TILE;
   return {lng:x/size*360-180,
     lat:Math.atan(Math.sinh(Math.PI*(1-2*y/size)))*180/Math.PI};
 };
 const centerPoint=()=>projection(state.center.lat,state.center.lng,state.zoom);
 const ele=(tag,cls)=>{const el=document.createElement(tag);if(cls)el.className=cls;return el;};
 const labelTime=value=>{
   if(!value)return"Horário indisponível";
   const t=new Date(value);
   return Number.isFinite(+t)?t.toLocaleTimeString("pt-BR",{hour:"2-digit",minute:"2-digit"}):"Horário indisponível";
 };
 function fit(items){
   const coords=items.filter(valid);
   if(!coords.length){state.center={...DEFAULT_CENTER};state.zoom=12;return}
   const mean=coords.reduce((a,p)=>({lat:a.lat+Number(p.lat),lng:a.lng+Number(p.lng)}),{lat:0,lng:0});
   state.center={lat:mean.lat/coords.length,lng:mean.lng/coords.length};
   const el=document.getElementById("upMapCanvas");
   const w=Math.max(el?.clientWidth||400,260),h=Math.max(el?.clientHeight||320,230);
   for(let z=15;z>=MIN;z--){
     const arr=coords.map(p=>projection(p.lat,p.lng,z));
     const dx=Math.max(...arr.map(p=>p.x))-Math.min(...arr.map(p=>p.x));
     const dy=Math.max(...arr.map(p=>p.y))-Math.min(...arr.map(p=>p.y));
     if(dx<=w-110&&dy<=h-100){state.zoom=z;break}
     if(z===MIN)state.zoom=MIN;
   }
 }
 function draw(){
   const el=document.getElementById("upMapCanvas");
   if(!el||!state.center)return;
   const width=el.clientWidth,height=el.clientHeight;
   if(!width||!height)return;
   const center=centerPoint(),world=2**state.zoom;
   const frag=document.createDocumentFragment();
   const startX=Math.floor((center.x-width/2)/TILE);
   const endX=Math.ceil((center.x+width/2)/TILE);
   const startY=Math.floor((center.y-height/2)/TILE);
   const endY=Math.ceil((center.y+height/2)/TILE);
   // Only currently visible tiles. Browser caching handles repeat visits.
   for(let x=startX;x<=endX;x++)for(let y=startY;y<=endY;y++){
     if(y<0||y>=world)continue;
     const tile=ele("img","up-map-tile");
     tile.alt="";tile.decoding="async";tile.loading="lazy";tile.referrerPolicy="no-referrer";
     tile.src="https://tile.openstreetmap.org/"+state.zoom+"/"+((x%world+world)%world)+"/"+y+".png";
     tile.style.left=Math.round(x*TILE-center.x+width/2)+"px";
     tile.style.top=Math.round(y*TILE-center.y+height/2)+"px";
     frag.append(tile);
   }
   for(const item of state.items.filter(valid)){
     const pos=projection(item.lat,item.lng,state.zoom);
     const x=pos.x-center.x+width/2,y=pos.y-center.y+height/2;
     if(x<-50||y<-50||x>width+50||y>height+50)continue;
     const pin=ele("button","up-map-pin"+(item.mission_status?" busy":""));
     pin.type="button";pin.dataset.action="map-select";pin.dataset.id=item.id;
     pin.setAttribute("aria-label",item.name+" • "+(item.mission_status?"Em entrega":"Disponível"));
     pin.title=item.name;
     pin.textContent=item.name.trim().charAt(0).toUpperCase()||"UP";
     pin.style.left=Math.round(x)+"px";pin.style.top=Math.round(y)+"px";
     frag.append(pin);
   }
   const attr=ele("a","up-map-credit");
   attr.href="https://www.openstreetmap.org/copyright";
   attr.target="_blank";attr.rel="noopener noreferrer";
   attr.textContent="© OpenStreetMap contributors";
   frag.append(attr);
   el.replaceChildren(frag);
   const current=state.items.find(x=>x.id===state.target);
   const summary=document.getElementById("upMapSelected");
   if(summary){
     summary.textContent=current?
       current.name+" • "+(current.mission_status?"Em entrega":"Disponível")+
       " • GPS "+labelTime(current.last_location_at):
       "Toque em um marcador para consultar o entregador.";
   }
   const zoomText=document.getElementById("upMapZoom");
   if(zoomText)zoomText.textContent="Zoom "+state.zoom;
 }
 function mount(items,focusId){
   const el=document.getElementById("upMapCanvas");
   state.items=Array.isArray(items)?items.filter(valid):[];
   if(!el)return;
   if(!state.center||state.fit)fit(state.items);
   if(!state.items.length)state.target=null;
   state.fit=false;
   if(focusId)select(focusId);else draw();
   el.onpointerdown=e=>{
     if(e.target.closest("button,a"))return;
     state.drag={x:e.clientX,y:e.clientY,center:{...state.center}};
     el.setPointerCapture(e.pointerId);
   };
   el.onpointerup=e=>{
     if(!state.drag)return;
     const start=projection(state.drag.center.lat,state.drag.center.lng,state.zoom);
     state.center=inverse(start.x-(e.clientX-state.drag.x),start.y-(e.clientY-state.drag.y),state.zoom);
     state.center.lat=limit(state.center.lat,-85,85);
     state.center.lng=((state.center.lng+540)%360)-180;
     state.drag=null;draw();
   };
   el.onpointercancel=()=>{state.drag=null;};
   el.onwheel=e=>{
     e.preventDefault();
     zoom(e.deltaY<0?1:-1);
   };
 }
 function zoom(delta){
   if(!state.center)return;
   state.zoom=limit(state.zoom+delta,MIN,MAX);
   draw();
 }
 function select(id){
   state.target=id;
   const item=state.items.find(x=>x.id===id);
   if(item)state.center={lat:Number(item.lat),lng:Number(item.lng)};
   draw();
 }
 function recenter(){state.fit=true;fit(state.items);state.fit=false;state.target=null;draw()}
 window.UPMap=Object.freeze({mount,zoom,select,recenter});
})();
