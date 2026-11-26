"use strict";

// Never store Firebase ID/refresh tokens or a manager password in localStorage,
// sessionStorage, URLs or persistent cookies. A refresh requires signing in again.
const CONFIG=window.UP_MANAGER_CONFIG||{};
const STORE={token:"",refreshToken:"",expires:0,user:null,page:"overview",
 busy:false,filter:"all",data:{},active:false,mapFocus:null};
const MENU=[
 {id:"overview",label:"Visão geral",short:"Início",icon:"⌂",desc:"Indicadores operacionais"},
 {id:"jobs",label:"Corridas",short:"Corridas",icon:"▤",desc:"Ofertas, eventos e andamento"},
 {id:"pricing",label:"Tarifas e rotas",icon:"◉",desc:"Valores, espera e agrupamento"},
 {id:"map",label:"Mapa operacional",short:"Mapa",icon:"⌖",desc:"GPS recente de entregadores online"},
 {id:"merchants",label:"Lojas",icon:"▦",desc:"Estabelecimentos e pontos de coleta"},
 {id:"couriers",label:"Entregadores",short:"Entregadores",icon:"♙",desc:"Disponibilidade e situação"},
 {id:"applications",label:"Cadastros",icon:"▣",desc:"Análise e aprovação"},
 {id:"finance",label:"Financeiro",icon:"◈",desc:"Saldos e repasses registrados"},
 {id:"occurrences",label:"Ocorrências",icon:"⚑",desc:"Registros da operação"},
 {id:"audit",label:"Auditoria",icon:"▥",desc:"Histórico administrativo"},
 {id:"integrations",label:"Integrações",icon:"⌁",desc:"Verificação segura dos serviços"},
 {id:"releases",label:"Atualizações do app",icon:"⇧",desc:"Canal oficial e publicação de APK"}
];
const MOBILE=["overview","jobs","map","couriers","more"];
const LABELS={
 WAITING_DRIVER:"Aguardando entregador",OFFERED:"Oferta enviada",ACCEPTED:"Aceita",
 TO_STORE:"Indo à loja",AT_STORE:"Na loja",TO_CUSTOMER:"Em rota",
 AT_CUSTOMER:"No cliente",DELIVERED:"Entregue",CANCELLED:"Cancelada",FAILED:"Falha na corrida",
 APPROVED:"Aprovado",SUSPENDED:"Suspenso",PENDING:"Pendente",
 NEEDS_CORRECTION:"Correção solicitada",REJECTED:"Reprovado",
 REQUESTED:"Solicitado",PAID:"Pago",DECLINED:"Recusada pelo entregador",EXPIRED:"Oferta expirada"
};
const ICON_PATHS={
 overview:'<path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z"/>',
 jobs:'<rect x="4" y="4" width="16" height="16" rx="3"/><path d="M8 9h8M8 13h8M8 17h4"/>',
 map:'<path d="M3 6 9 3l6 3 6-3v15l-6 3-6-3-6 3zM9 3v15M15 6v15"/>',
 couriers:'<circle cx="12" cy="7" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/>',
 merchants:'<path d="M3 10h18l-2-6H5l-2 6zm2 0v11h14V10M9 21v-7h6v7"/>',
 applications:'<rect x="4" y="3" width="16" height="18" rx="2"/><path d="M9 8h6M8 13h8M8 17h5"/>',
 finance:'<rect x="2" y="6" width="20" height="14" rx="3"/><path d="M2 10h20M17 15h1"/>',
 occurrences:'<path d="M12 3 2 21h20L12 3zM12 9v5M12 18h.01"/>',
 audit:'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
 integrations:'<path d="M7 7 3 12l4 5M17 7l4 5-4 5M14 5l-4 14"/>',
 releases:'<path d="M12 16V3m-5 5 5-5 5 5M4 16v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4"/>',
 more:'<circle cx="5" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="19" cy="12" r="1.5"/>'
};
const svgIcon=id=>'<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">'+(ICON_PATHS[id]||ICON_PATHS.more)+'</svg>';
const $=sel=>document.querySelector(sel);
const escapeHtml=value=>String(value??"").replace(/[&<>"']/g,ch=>({
 "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"
})[ch]);
const money=value=>Number(value||0).toLocaleString("pt-BR",{style:"currency",currency:"BRL"});
const date=value=>{if(!value)return"—";const d=new Date(value);return Number.isNaN(+d)?"—":d.toLocaleString("pt-BR");};
const text=value=>String(value??"").trim();
const allowedIds=new Set(MENU.map(x=>x.id).concat("more"));
const isManager=()=>["OWNER","ADMIN"].includes(STORE.user?.role);
const isDispatcher=()=>isManager()||STORE.user?.role==="DISPATCHER";
const label=s=>LABELS[text(s).toUpperCase()]||text(s).replaceAll("_"," ")||"—";
const badge=s=>{
 const st=text(s).toUpperCase();
 const cls=["APPROVED","DELIVERED","PAID"].includes(st)?"good":
  ["WAITING_DRIVER","PENDING","REQUESTED","OFFERED","NEEDS_CORRECTION"].includes(st)?"wait":
  ["REJECTED","DECLINED","CANCELLED","FAILED","SUSPENDED"].includes(st)?"bad":"blue";
 return '<span class="pill '+cls+'">'+escapeHtml(label(st))+'</span>';
};
const smallInfo=(title,value)=>'<div class="detail"><small>'+escapeHtml(title)+'</small><strong>'+escapeHtml(value??"—")+'</strong></div>';
const empty=message=>'<div class="empty">'+escapeHtml(message)+'</div>';
function notice(message,error=false){
 const e=$("#toast");e.textContent=message;e.classList.remove("hidden");
 e.style.background=error?"#9c2331":"#10274d";
 clearTimeout(notice.timeout);notice.timeout=setTimeout(()=>e.classList.add("hidden"),4000);
}
function configOkay(){
 try{
  const url=new URL(CONFIG.managerApiUrl),project=new URL(CONFIG.projectUrl);
  return CONFIG.enabled===true&&typeof CONFIG.firebaseApiKey==="string"&&
   CONFIG.firebaseApiKey.startsWith("AIza")&&
   typeof CONFIG.publishableKey==="string"&&CONFIG.publishableKey.startsWith("sb_publishable_")&&
   url.protocol==="https:"&&project.protocol==="https:"&&
   url.hostname===project.hostname&&
   project.hostname===CONFIG.projectRef+".supabase.co"&&
   url.pathname==="/functions/v1/up-manager-independent"&&
   !url.search&&!url.hash&&!url.username&&!url.password&&
   CONFIG.firebaseProjectId==="rodrigues-d6566";
 }catch{return false;}
}
function show(id){["#configuration","#login","#app"].forEach(s=>$(s).classList.add("hidden"));$("#"+id).classList.remove("hidden");}
function clearSession(){
 STORE.token="";STORE.refreshToken="";STORE.expires=0;STORE.user=null;STORE.data={};
 STORE.active=false;STORE.mapFocus=null;
 $("#password").value="";
 show(configOkay()?"login":"configuration");
}
async function firebaseRequest(path,form){
 const response=await fetch("https://identitytoolkit.googleapis.com/v1/"+path+"?key="+
 encodeURIComponent(CONFIG.firebaseApiKey),{
  method:"POST",headers:{"content-type":"application/json"},body:JSON.stringify(form)
 });
 const data=await response.json().catch(()=>({}));
 if(!response.ok||!data.idToken)throw Error("E-mail ou senha incorretos, ou conta indisponível.");
 return data;
}
async function refreshIdentity(){
 if(!STORE.token)throw Error("Entre novamente.");
 if(STORE.expires>Date.now()+120000)return;
 if(!STORE.refreshToken)throw Error("Sessão expirada. Entre novamente.");
 const response=await fetch("https://securetoken.googleapis.com/v1/token?key="+
 encodeURIComponent(CONFIG.firebaseApiKey),{
  method:"POST",headers:{"content-type":"application/x-www-form-urlencoded"},
  body:new URLSearchParams({grant_type:"refresh_token",refresh_token:STORE.refreshToken}).toString()
 });
 const data=await response.json().catch(()=>({}));
 if(!response.ok||!data.id_token)throw Error("Sessão expirada. Entre novamente.");
 STORE.token=data.id_token;STORE.refreshToken=data.refresh_token;
 STORE.expires=Date.now()+Number(data.expires_in||3600)*1000;
}
async function call(action,extra={}){
 await refreshIdentity();
 const ctrl=new AbortController();
 const t=setTimeout(()=>ctrl.abort(),18000);
 try{
  const response=await fetch(CONFIG.managerApiUrl,{
   method:"POST",signal:ctrl.signal,
   headers:{"content-type":"application/json",
    "apikey":CONFIG.publishableKey,"authorization":"Bearer "+STORE.token},
   body:JSON.stringify({action,...extra})
  });
  const data=await response.json().catch(()=>({}));
  if(!response.ok||!data.ok){
   const errors={
    manager_not_authorized:"Sua conta não possui acesso administrativo ao UP.",
    invalid_firebase_token:"Sessão expirada. Entre novamente.",
    insufficient_permissions:"Seu perfil não tem permissão para esta ação.",
    courier_not_available:"Entregador indisponível ou não aprovado.",
    courier_busy:"Este entregador já possui uma corrida ativa.",
    job_unavailable:"Corrida não está disponível para oferta.",
    job_already_offered:"Esta corrida já tem uma oferta ativa.",
    driver_has_active_job:"Há uma corrida ativa com este entregador.",
    insufficient_wallet_balance:"Saldo insuficiente para o repasse.",
    suspend_existing_driver_separately:"Entregador aprovado deve ser suspenso na área Entregadores.",
    incomplete_application:"Dados cadastrais incompletos.",
    independent_manager_disabled:"O backend independente ainda não foi ativado."
   };
   if(response.status===401){clearSession();}
   throw Error(errors[data.error]||data.error||"Não foi possível concluir a solicitação.");
  }
  return data;
 }catch(e){
  if(e.name==="AbortError")throw Error("Conexão demorou demais. Tente novamente.");
  throw e;
 }finally{clearTimeout(t);}
}
async function login(event){
 event.preventDefault();
 const button=$("#loginButton"),error=$("#loginError");
 if(button.disabled)return;
 error.textContent="";button.disabled=true;button.textContent="Entrando…";
 try{
  const email=$("#email").value.trim(),password=$("#password").value;
  if(!email||!password)throw Error("Preencha e-mail e senha.");
  const result=await firebaseRequest("accounts:signInWithPassword",{
   email,password,returnSecureToken:true
  });
  STORE.token=result.idToken;STORE.refreshToken=result.refreshToken;
  STORE.expires=Date.now()+Number(result.expiresIn||3600)*1000;
  $("#password").value="";
  const r=await call("bootstrap");
  STORE.user=r.user;STORE.active=true;show("app");
  $("#signedUser").textContent=r.user.name+" • "+r.user.role;
  await navigate("overview");
 }catch(e){
  STORE.token="";STORE.refreshToken="";STORE.user=null;
  error.textContent=e.message||"Falha na autenticação.";
 }finally{button.disabled=false;button.textContent="Entrar no UP Gestor";}
}
function navigation(){
 const pages=MENU.filter(x=>x.id==="releases"&&STORE.user?.role!=="OWNER"?false:x.id==="pricing"&&!isDispatcher()?false:x.id==="map"&&!isDispatcher()?false:["finance","audit","integrations","merchants"].includes(x.id)?
   (x.id==="audit"?STORE.user?.role==="OWNER":isManager()):true);
 $("#sideNav").innerHTML=pages.map(x=>'<button type="button" data-action="go" data-page="'+x.id+'" '+
  'class="'+(STORE.page===x.id?"active":"")+'"><span class="nav-icon">'+svgIcon(x.id)+
  '</span>'+x.label+'</button>').join("");
 const more=["overview","jobs","map","couriers"].includes(STORE.page)?STORE.page:"more";
 $("#bottomNav").innerHTML=MOBILE.filter(id=>id==="more"||pages.some(x=>x.id===id)).map(id=>{
  const p=pages.find(x=>x.id===id);
  const obj=id==="more"?{short:"Mais",icon:"⋯"}:p;
  return '<button type="button" data-action="go" data-page="'+id+
   '" class="'+(more===id?"active":"")+'"><span class="nav-icon">'+svgIcon(id)+
   '</span>'+obj.short+'</button>';
 }).join("");
 $("#pageTitle").textContent=STORE.page==="more"?"Mais":pages.find(x=>x.id===STORE.page)?.label||"UP Gestor";
}
async function navigate(next,reload=true){
 if(!allowedIds.has(next))return;
 if(["audit","releases"].includes(next)&&STORE.user?.role!=="OWNER")return;
 if(next==="map"&&!isDispatcher())return;
 if(next==="pricing"&&!isDispatcher())return;
 if(["finance","integrations","merchants"].includes(next)&&!isManager())return;
 STORE.page=next;
 navigation();
 if(next==="more"){renderMore();return;}
 if(reload||!STORE.data[next])await load(next);else render();
}
async function load(page=STORE.page,silent=false){
 if(page==="releases"&&STORE.user?.role!=="OWNER")return;
 if(STORE.busy)return;
 STORE.busy=true;$("#refresh").disabled=true;
 $("#connection").textContent="Atualizando";$("#connection").classList.remove("offline");
 if(!silent)$("#main").innerHTML='<div class="loading-block">Carregando dados do UP…</div>';
 const calls={overview:"dashboard",jobs:"jobs_list",map:"operations_map",merchants:"merchants_list",couriers:"couriers_list",
  applications:"applications_list",finance:"finance",occurrences:"occurrences",audit:"audit",integrations:"integration_health",pricing:"pricing_policy_get"};
 try{
  STORE.data[page]=page==="releases"?await readOfficialRelease():await call(calls[page]);
  render();
  $("#connection").textContent="Atualizado";
 }catch(e){
  $("#connection").textContent="Falha";$("#connection").classList.add("offline");
  $("#main").innerHTML='<div class="panel"><div class="warning-note">'+escapeHtml(e.message)+
  '</div><button type="button" class="button secondary" data-action="refresh">Tentar novamente</button></div>';
 }finally{STORE.busy=false;$("#refresh").disabled=false;}
}
function kpi(title,value,description){
 return '<div class="stat"><div class="k">'+escapeHtml(title)+'</div><strong>'+
  escapeHtml(value)+'</strong><div class="foot">'+escapeHtml(description)+'</div></div>';
}
function renderOverview(){
 const d=STORE.data.overview?.metrics||{};
 $("#main").innerHTML=
  '<p class="page-intro">A operação das entregas fica inteiramente no UP. Dados são atualizados apenas quando você solicita.</p>'+
  '<div class="stat-grid">'+kpi("Corridas ativas",d.active||0,"Ainda não concluídas")+
  kpi("Sem motorista",d.awaiting||0,"Na fila ou com oferta")+
  kpi("Entregadores online",d.online||0,"Aprovados e disponíveis")+
  kpi("Cadastros pendentes",d.pending||0,"Aguardando análise")+'</div>'+
  '<div class="section-head"><h2>Operação independente</h2></div>'+
  '<div class="panel"><div class="details-grid">'+
  smallInfo("Administração","Exclusiva do UP Entregas")+
  smallInfo("Banco de dados","Supabase exclusivo do UP")+
  smallInfo("Atualizações","Somente sob comando")+
  smallInfo("Pendências financeiras",String(d.payout_pending||0))+
  '</div></div>'+
  (STORE.data.overview?.sampled?'<div class="warning-note">Indicadores calculados a partir de uma amostra limitada de registros.</div>':"");
}
function renderJobs(){
 const jobs=STORE.data.jobs?.jobs||[];
 const f=STORE.filter;
 const rows=jobs.filter(j=>f==="all"||(f==="active"&&!["DELIVERED","FAILED","CANCELLED"].includes(j.status))||
  (f==="waiting"&&["WAITING_DRIVER","OFFERED"].includes(j.status))||
  (f==="done"&&["DELIVERED","FAILED","CANCELLED"].includes(j.status)));
 $("#main").innerHTML='<p class="page-intro">Gestão de corridas, ofertas e andamento. O pedido e o pagamento do cliente continuam na loja.</p>'+ 
 (isDispatcher()?'<div class="actions"><button class="button primary" data-action="new-job">Solicitar entrega para uma loja</button></div>':"")+
 '<div class="chips">'+[
  ["all","Todas"],["active","Ativas"],["waiting","Aguardando"],["done","Finalizadas"]
 ].map(([id,name])=>'<button class="chip '+(id===f?"on":"")+'" data-action="filter" data-filter="'+id+'">'+name+'</button>').join("")+'</div>'+
 (rows.map(jobCard).join("")||empty("Nenhuma corrida nesta situação."));
}
function jobCard(j){
 const status=j.status,canOffer=isDispatcher()&&["WAITING_DRIVER","OFFERED"].includes(status);
 return '<article class="record"><div class="record-head"><div><h3>Corrida #'+
 escapeHtml(j.merchant_order_code||j.id.slice(0,8))+'</h3><p>'+
 escapeHtml(j.recipient_name||"Cliente")+" • "+date(j.received_at)+'</p></div>'+
 badge(status)+'</div><div class="details-grid" style="margin-top:12px">'+
 smallInfo("Cobrança na entrega",money(j.collection_amount))+
 smallInfo("Entregador",j.assigned_courier_id?"Atribuído":"Não atribuído")+'</div>'+
 '<div class="actions"><button class="button secondary" data-action="view-job" data-id="'+j.id+
 '">Detalhes</button>'+(canOffer?'<button class="button primary" data-action="send-offer" data-id="'+j.id+'">Oferecer corrida</button>':"")+
 '</div></article>';
}
function renderMerchants(){
 const merchants=STORE.data.merchants?.merchants||[];
 $("#main").innerHTML=
  '<p class="page-intro">Cadastre e acompanhe as lojas contratantes sem misturar seus pedidos com o Rodrigues Açaí.</p>'+
  (STORE.user?.role==="OWNER"?'<div class="actions"><button class="button primary" data-action="merchant-draft">Cadastrar nova loja</button></div>':"")+
  '<div class="stat-grid">'+kpi("Lojas cadastradas",merchants.length,"Todas as etapas de contratação")+
  kpi("Em rascunho",merchants.filter(m=>m.state==="DRAFT").length,"Não podem solicitar entregadores")+
  kpi("Lojas ativas",merchants.filter(m=>m.state==="ACTIVE").length,"Liberadas pela administração")+'</div>'+
  '<div class="section-head"><h2>Estabelecimentos</h2></div><div class="record-grid">'+
  (merchants.length?merchants.map(m=>'<article class="record">'+
   '<div class="record-head"><h3>'+escapeHtml(m.display_name)+'</h3>'+badge(m.state)+'</div>'+
   '<p class="small">'+escapeHtml(m.slug)+'</p>'+
   (m.locations||[]).map(l=>'<div class="details-grid">'+
    smallInfo("Retirada",l.label)+smallInfo("Status do ponto",label(l.state))+
    smallInfo("Endereço",[l.street,l.number,l.neighborhood,l.city,l.state_code].filter(Boolean).join(", "))+
   '</div>').join("")+
   '</article>').join(""):empty("Nenhuma loja cadastrada."))+'</div>'+
  '<div class="warning-note">Novas lojas entram como rascunho, sem integração, cobrança ou corridas automáticas. A ativação requer conferência contratual e operacional.</div>';
}
function renderCouriers(){
 const couriers=STORE.data.couriers?.couriers||[];
 $("#main").innerHTML='<p class="page-intro">Presença, aprovação e disponibilidade pertencem exclusivamente à operação do UP.</p>'+
 (couriers.map(c=>'<article class="record"><div class="record-head"><div><h3>'+
  escapeHtml(c.display_name)+'</h3><p>'+escapeHtml(c.phone||"Sem telefone")+'</p></div>'+
  badge(c.state)+'</div><div class="details-grid" style="margin-top:13px">'+
  smallInfo("Presença",c.online?"Online":"Offline")+
  smallInfo("Último GPS",date(c.last_location_at))+
  '</div>'+(c.state==="PENDING"?'<div class="warning-note">Importação pendente. Confira identificação e documentos no cadastro anterior antes de aprovar. O entregador não pode receber ofertas.</div>':"")+
 (isManager()?'<div class="actions"><button class="button secondary" data-action="driver-diagnostics" data-id="'+c.id+'">Diagnóstico do app</button><button class="button '+(c.state==="SUSPENDED"||c.state==="PENDING"?"green":"secondary")+
  '" data-action="driver-state" data-id="'+c.id+'" data-state="'+(c.state==="SUSPENDED"||c.state==="PENDING"?"APPROVED":"SUSPENDED")+
  '" data-review-required="'+(c.state==="PENDING"?"true":"false")+'">'+
  (c.state==="PENDING"?"Revisar e aprovar":c.state==="SUSPENDED"?"Reativar":"Suspender")+'</button></div>':"")+
  '</article>').join("")||empty("Nenhum entregador cadastrado."));
}
function renderApplications(){
 const arr=STORE.data.applications?.applications||[],filter=STORE.filter;
 const rows=arr.filter(x=>filter==="all"||x.state===filter);
 $("#main").innerHTML='<p class="page-intro">Cadastros com revisão manual e registro de responsabilidade.</p>'+
 '<div class="chips">'+[
  ["all","Todos"],["PENDING","Pendentes"],["NEEDS_CORRECTION","Correções"],
  ["APPROVED","Aprovados"],["REJECTED","Reprovados"]
 ].map(([id,name])=>'<button class="chip '+(id===filter?"on":"")+
 '" data-action="filter" data-filter="'+id+'">'+name+'</button>').join("")+'</div>'+
 (rows.map(a=>'<article class="record"><div class="record-head"><div><h3>'+escapeHtml(a.full_name)+
 '</h3><p>'+escapeHtml(a.phone||"")+" • "+date(a.created_at)+'</p></div>'+
 badge(a.state)+'</div><div class="actions">'+
 (isManager()?'<button class="button secondary" data-action="review-application" data-id="'+a.id+
 '">Analisar cadastro</button>':'<span class="muted small">Somente consulta</span>')+
 '</div></article>').join("")||empty("Nenhum cadastro nesta situação."));
}
function renderFinance(){
 const d=STORE.data.finance||{},balance=d.balances||[],payouts=d.payouts||[];
 const available=balance.reduce((a,x)=>a+Number(x.available||0),0);
 $("#main").innerHTML='<p class="page-intro">Registro e conciliação de repasses. O UP Gestor <strong>não transfere Pix automaticamente</strong>.</p>'+
 (d.sampled?'<div class="warning-note">Resultados financeiros parciais: limite de registros atingido. Não confirme pagamentos sem conciliação.</div>':"")+
 '<div class="two-col">'+kpi("Disponível para repasse",money(available),"Saldo não reservado")+
 kpi("Solicitações registradas",payouts.filter(x=>x.status==="REQUESTED").length,"Aguardando confirmação")+
 '</div><div class="section-head"><h2>Entregadores</h2></div>'+
 (balance.map(b=>'<article class="record"><div class="record-head"><div><h3>'+
  escapeHtml(b.name)+'</h3><p>Movimentações registradas: '+money(b.earned)+'</p></div>'+
  '<strong class="value">'+money(b.available)+'</strong></div><div class="actions">'+
  '<button class="button secondary" data-action="wallet" data-id="'+b.courier_id+'">Extrato</button>'+
  (!d.sampled?'<button class="button primary" data-action="payout" data-id="'+b.courier_id+'">Solicitar repasse</button>':"")+
  '</div></article>').join("")||empty("Nenhum saldo encontrado."))+
 '<div class="section-head"><h2>Repasses registrados</h2></div>'+
 (payouts.map(p=>'<article class="record"><div class="record-head"><div><h3>'+
  money(p.amount)+'</h3><p>'+date(p.requested_at)+'</p></div>'+badge(p.status)+'</div>'+
  '<div class="actions">'+(p.status==="REQUESTED"?'<button class="button green" data-action="mark-paid" data-id="'+p.id+
  '">Registrar pagamento realizado</button>':"")+'</div></article>').join("")||empty("Nenhum repasse registrado."));
}
function renderOccurrences(){
 const items=STORE.data.occurrences?.occurrences||[];
 $("#main").innerHTML='<p class="page-intro">Registros de ocorrências enviados pelos entregadores.</p>'+
 (items.map(x=>'<article class="record"><div class="record-head"><h3>Corrida '+
  escapeHtml(x.entity_id||"—")+'</h3><span class="pill bad">Ocorrência</span></div>'+
 '<p>'+escapeHtml(x.metadata?.reason||"Sem motivo detalhado")+'</p>'+
 '<p>'+date(x.created_at)+'</p></article>').join("")||empty("Nenhuma ocorrência registrada."));
}
function renderAudit(){
 const items=STORE.data.audit?.entries||[];
 $("#main").innerHTML='<p class="page-intro">Registro restrito ao proprietário do UP.</p>'+
 '<div class="panel table-scroll"><table class="table"><thead><tr><th>Data</th><th>Ação</th><th>Responsável</th><th>Registro</th></tr></thead><tbody>'+
 (items.map(x=>'<tr><td>'+date(x.created_at)+'</td><td>'+escapeHtml(x.action)+
 '</td><td>'+escapeHtml(x.actor)+'</td><td>'+escapeHtml(x.entity_id||"—")+'</td></tr>').join("")||empty("Nenhum evento administrativo."))+
 '</tbody></table></div>';
}
function renderIntegrations(){
 const info=STORE.data.integrations||{};
 const checks=Array.isArray(info.checks)?info.checks:[];
 const flags=Array.isArray(info.flags)?info.flags:[];
 const probe=info.probe||null;
 const renderEntry=(label,active,readyLabel,waitingLabel)=>
  '<article class="record"><div class="record-head"><h3>'+escapeHtml(label||"Integração")+
  '</h3><span class="pill '+(active?"good":"wait")+'">'+(active?readyLabel:waitingLabel)+'</span></div></article>';
 const summary=probe?('<div class="record-grid">'+
  renderEntry("Assinatura entre UP e loja",probe.connection_ok===true,"Conectados","Não verificado")+
  renderEntry("Retorno automático de status",probe.callback_enabled===true,"Habilitado","Desativado")+
  '</div><p class="small">'+escapeHtml(probe.message||"")+'</p>'):
  '<div class="warning-note">A chave pode estar cadastrada, mas a comunicação entre os dois projetos ainda não foi testada.</div>';
 $("#main").innerHTML='<p class="page-intro">Diagnóstico exclusivo do administrador. As chaves e dados dos clientes não aparecem aqui.</p>'+
 '<div class="section-head"><h2>Teste de comunicação UP ↔ Loja</h2></div>'+
 '<div class="actions"><button type="button" class="button primary" data-action="probe-integration">Testar conexão segura</button></div>'+
 '<p class="small">Teste criptográfico de leitura: não cria corrida, não altera pedido e não habilita entregas.</p>'+
 summary+
 '<div class="section-head"><h2>Integrações privadas</h2></div><div class="record-grid">'+
 (checks.map(x=>renderEntry(x.label,x.ready===true,"Configurado","Pendente")).join("")||empty("Nenhuma integração listada."))+'</div>'+
 '<div class="section-head"><h2>Ativação operacional</h2></div><div class="record-grid">'+
 (flags.map(x=>renderEntry(x.label,x.enabled===true,"Habilitado","Desativado")).join("")||empty("Nenhum recurso listado."))+'</div>'+
 '<div class="warning-note">Conexão válida não comprova uma entrega real. O lançamento depende de teste completo do Android, autorização dos entregadores e callback à loja.</div>';
}
const UP_RELEASE_BASE="https://gkwmqwtktedarodvqqtn.supabase.co/storage/v1/object/public/up-entregador-releases";
const UP_RELEASE_REPO="https://github.com/Rodriguesac/up-delivery";
async function readOfficialRelease(){
 const ctrl=new AbortController(),timer=setTimeout(()=>ctrl.abort(),12000);
 try{
  const r=await fetch(UP_RELEASE_BASE+"/update.json",{cache:"no-store",credentials:"omit",signal:ctrl.signal});
  if(!r.ok)throw Error("Canal indisponível (HTTP "+r.status+").");
  const j=await r.json();
  if(j.applicationId!=="com.rodriguesacai.updriver.independente"||
    !Number.isInteger(j.versionCode)||j.versionCode<=0||j.versionCode>99999||
    !/^\d+\.\d+\.\d+$/.test(j.version)||
    !/^[a-f0-9]{64}$/i.test(j.sha256)||
    !Number.isSafeInteger(j.sizeBytes)||j.sizeBytes<1||j.sizeBytes>100*1024*1024||
    j.downloadUrl!==UP_RELEASE_BASE+"/up-entregador-"+j.versionCode+".apk")
   throw Error("Manifesto oficial inválido.");
  return {manifest:j};
 }catch(e){return {error:"Não foi possível confirmar a versão publicada agora. "+(e.name==="AbortError"?"Tempo de consulta esgotado.":e.message)};}
 finally{clearTimeout(timer);}
}
function renderReleases(){
 if(STORE.user?.role!=="OWNER"){$("#main").innerHTML=empty("Acesso reservado ao proprietário.");return;}
 const d=STORE.data.releases||{},j=d.manifest;
 const link=(href,label,style="secondary")=>'<a class="button '+style+'" href="'+escapeHtml(href)+'" target="_blank" rel="noopener noreferrer">'+escapeHtml(label)+'</a>';
 $("#main").innerHTML='<p class="page-intro">Central do UP Entregador • controle de versões e publicação segura pelo proprietário.</p>'+
  '<div class="panel"><div class="section-head"><h2>Canal oficial UP</h2></div>'+
  (j?'<div class="record-grid">'+kpi("Versão publicada",j.version,"Manifesto oficial consultado")+
    kpi("Código da versão",String(j.versionCode),"A próxima versão deve ser superior")+
    kpi("Tamanho",(j.sizeBytes/1048576).toFixed(2)+" MiB","APK registrado no canal")+'</div>'+
    '<div class="detail"><small>SHA-256 do manifesto</small><div style="overflow-wrap:anywhere">'+escapeHtml(j.sha256)+'</div></div>'+
    '<p>'+escapeHtml(String(j.notes||"").slice(0,1200))+'</p><div class="actions">'+link(j.downloadUrl,"Baixar APK publicado")+'</div>':
    '<div class="warning-note">'+escapeHtml(d.error||"Versão ainda não consultada.")+'</div>')+
  '<div class="actions"><button class="button secondary" data-action="refresh">Consultar versão</button>'+link(UP_RELEASE_BASE+"/update.json","Abrir manifesto")+'</div></div>'+
  '<div class="panel"><div class="section-head"><h2>Publicar novo APK</h2></div>'+
  '<p>Envie somente APK de produção assinado com o certificado original do aplicativo. O canal recusa versões iguais ou inferiores, pacote incompatível e assinatura incorreta.</p>'+
  '<ol><li>Renomeie o APK para <strong>up-entregador-'+(j?String(j.versionCode+1):"CODIGO")+'.apk</strong> usando o código real da nova versão.</li>'+
  '<li>Abra o envio privado pelo botão abaixo. Entre no GitHub com sua conta proprietária, selecione um único APK (até 20 MiB) e confirme o commit na branch main.</li>'+
  '<li>Acompanhe a rotina de publicação. Aguarde o resultado verde e consulte a versão oficial novamente.</li></ol>'+
  '<div class="actions">'+link(UP_RELEASE_REPO+"/upload/main/release-inbox","Enviar APK pelo GitHub","primary")+
  link(UP_RELEASE_REPO+"/actions","Acompanhar publicação")+'</div>'+
  '<div class="info-note">A publicação atualiza o manifesto automaticamente após as verificações. Não é necessário editar o Supabase. O upload ocorre no GitHub autenticado, sem compartilhar chaves com este painel.</div></div>'+
  '<div class="panel"><div class="section-head"><h2>Documentação e segurança</h2></div>'+
  '<p>Mantenha a chave de assinatura e sua senha em backup privado. Não desinstale o aplicativo para atualizar. O APK instalado continua sujeito à verificação de assinatura do Android.</p>'+
  '<div class="actions">'+link(UP_RELEASE_REPO+"/tree/main/release-inbox","Arquivos enviados")+
  link("https://supabase.com/dashboard/project/gkwmqwtktedarodvqqtn/storage/buckets","Armazenamento oficial")+'</div></div>';
}
function renderMore(){
 $("#main").innerHTML='<p class="page-intro">Cada função administrativa está organizada na área correspondente.</p>'+
 '<div class="more-grid">'+MENU.filter(x=>!["overview","jobs","map","couriers"].includes(x.id)&&
  (!["finance","integrations","merchants"].includes(x.id)||isManager())&&(!["audit","releases"].includes(x.id)||STORE.user?.role==="OWNER"))
 .map(x=>'<button class="more-card" data-action="go" data-page="'+x.id+'">'+
  svgIcon(x.id)+" "+x.label+"<span>"+escapeHtml(x.desc)+'</span></button>').join("")+
 '<button class="more-card" data-action="logout">Sair da conta<span>Encerrar sessão administrativa</span></button></div>';
}
function render(){
 if(STORE.page==="overview")window.UPGestorV3?window.UPGestorV3.overview():renderOverview();
 else if(STORE.page==="jobs")window.UPGestorV3?window.UPGestorV3.jobs():renderJobs();
 else if(STORE.page==="map")window.UPGestorV3?.map();
 else if(STORE.page==="pricing")pricingView();
 else if(STORE.page==="merchants")renderMerchants();
 else if(STORE.page==="couriers")window.UPGestorV3?window.UPGestorV3.couriers():renderCouriers();
 else if(STORE.page==="applications")renderApplications();
 else if(STORE.page==="finance")renderFinance();
 else if(STORE.page==="occurrences")renderOccurrences();
 else if(STORE.page==="audit")renderAudit();
 else if(STORE.page==="integrations")renderIntegrations();
 else if(STORE.page==="releases")renderReleases();
 else renderMore();
}
function closeDialog(){
 window.UPManualAddress?.cleanup();
 $("#dialogBackdrop").classList.add("hidden");$("#dialogContent").innerHTML="";
 $("#dialogActions").innerHTML="";
}
function dialog(title,html,okLabel,onSave){
 $("#dialogTitle").textContent=title;
 $("#dialogContent").innerHTML=html;
 $("#dialogActions").innerHTML='<button class="button secondary" type="button" data-action="close-dialog">Fechar</button>'+
  (onSave?'<button class="button primary" id="dialogSave" type="button">'+escapeHtml(okLabel||"Salvar")+'</button>':"");
 $("#dialogBackdrop").classList.remove("hidden");
 if(onSave)$("#dialogSave").onclick=async()=>{
  const btn=$("#dialogSave");if(btn.disabled)return;
  btn.disabled=true;btn.textContent="Salvando…";
  try{const saved=await onSave();if(saved!==false)closeDialog();}
  catch(e){notice(e.message,true)}
  finally{btn.disabled=false;btn.textContent=okLabel||"Salvar";}
 };
}
async function jobDetail(jobId){
 const r=await call("job_detail",{job_id:jobId}),j=r.job;
 const address=o=>[text(o?.street),text(o?.number),text(o?.neighborhood),text(o?.city)].filter(Boolean).join(", ")||"Não informado";
 const info='<div class="details-grid">'+
 smallInfo("Pedido",j.merchant_order_code)+smallInfo("Status",label(j.status))+
 smallInfo("Cliente",j.recipient_name)+smallInfo("Telefone",j.recipient_phone)+
 smallInfo("Coleta",address(j.pickup))+smallInfo("Destino",address(j.dropoff))+
 smallInfo("A receber na entrega",money(j.collection_amount))+
 smallInfo("Entregador",j.assigned_courier_id?"Atribuído":"Não atribuído")+
 '</div>';
 const offers='<div class="section-head"><h2>Ofertas enviadas</h2></div>'+
 (r.offers.length?r.offers.map(o=>'<p class="small">'+badge(o.status)+
 ' • '+date(o.created_at)+' • Expira: '+date(o.expires_at)+'</p>').join(""):empty("Nenhuma oferta."));
 const events='<div class="section-head"><h2>Linha do tempo</h2></div>'+
 (r.events.length?r.events.map(x=>'<p class="small"><strong>#'+escapeHtml(x.sequence)+
 " — "+escapeHtml(label(x.status))+'</strong> • '+date(x.occurred_at)+'</p>').join(""):empty("Nenhum evento."));
 dialog("Detalhes da corrida",info+offers+events);
}
async function merchantDraftDialog(){
 if(STORE.user?.role!=="OWNER"){notice("Cadastro de lojas exige o proprietário do UP.",true);return}
 const field=(id,title,max=120,attributes="")=>
  '<label for="'+id+'">'+escapeHtml(title)+'</label>'+
  '<input class="field" id="'+id+'" maxlength="'+max+'" autocomplete="off" '+attributes+'>';
 dialog("Nova loja no UP",
  '<p class="small">O cadastro fica em RASCUNHO, sem gerar corridas ou cobranças.</p>'+
  field("merchantName","Nome comercial",150,'required')+
  field("merchantSlug","Identificador único (letras, números e hífens)",70,'placeholder="nome-da-loja"')+
  field("merchantPickup","Nome do ponto de retirada",120,'value="Loja principal"')+
  field("merchantStreet","Rua do estabelecimento",180,'required')+
  field("merchantNumber","Número",20)+
  field("merchantNeighborhood","Bairro",120)+
  field("merchantCity","Cidade",120,'required')+
  field("merchantUF","UF",2,'placeholder="MS" maxlength="2"'),
  "Salvar rascunho",async()=>{
   const display_name=text($("#merchantName").value);
   const slug=text($("#merchantSlug").value).toLowerCase()||
    display_name.normalize("NFD").replace(/[\u0300-\u036f]/g,"")
     .toLowerCase().replace(/[^a-z0-9]+/g,"-").replace(/^-|-$/g,"").slice(0,70);
   const location={
    label:text($("#merchantPickup").value),
    street:text($("#merchantStreet").value),
    number:text($("#merchantNumber").value),
    neighborhood:text($("#merchantNeighborhood").value),
    city:text($("#merchantCity").value),
    state_code:text($("#merchantUF").value).toUpperCase()
   };
   if(display_name.length<3||display_name.length>150||
      !/^[a-z0-9][a-z0-9-]{2,69}$/.test(slug)||
      location.label.length<2||location.street.length<3||
      location.city.length<2||!/^[A-Z]{2}$/.test(location.state_code))
    throw Error("Informe nome, identificador, retirada, rua, cidade e UF válidos.");
   if(!confirm("Salvar esta loja como RASCUNHO, sem autorizar corridas?"))return false;
   const result=await call("merchant_draft_create",{display_name,slug,location});
   if(!result.merchant_id)throw Error("O UP não confirmou o registro.");
   notice("Loja cadastrada como rascunho; nenhuma entrega foi contratada.");
   await load("merchants");
  });
}
async function driverDiagnosticsDialog(courierId){
 if(!isManager())throw Error("Somente administradores podem ver diagnóstico de operação.");
 const result=await call("driver_diagnostics",{courier_id:courierId});
 const d=result.driver;
 if(!d)throw Error("Entregador não encontrado no banco independente.");
 const row=(text,ok,details="")=>
  '<article class="record"><div class="record-head"><h3>'+escapeHtml(text)+'</h3>'+
  '<span class="pill '+(ok?"good":"wait")+'">'+(ok?"OK":"Pendente")+'</span></div>'+
  (details?'<p class="small">'+escapeHtml(details)+'</p>':"")+'</article>';
 const connected=d.firebase_linked===true;
 const active=d.online===true;
 const push=d.push_registered===true;
 const batteryRecent=d.battery_recent===true;
 const batterySafe=batteryRecent&&Number(d.battery_percent)>=15;
 const gps=d.gps_recent===true;
 const name=escapeHtml(d.name||"Entregador");
 const html='<p class="small">Indicadores reais, sem divulgar localização precisa ou tokens do aparelho.</p>'+
  '<div class="record-grid">'+
  row("Cadastro aprovado",d.state==="APPROVED","Estado: "+label(d.state))+
  row("Identidade Firebase",connected,"Vinculação com a conta autorizada")+
  row("Disponibilidade",active,"Toque em Online no aplicativo do motoboy")+
  row("Notificação FCM",push,push?"Aparelho registrado":"Abrir o app, conceder notificações e ficar online")+
  row("Bateria",batterySafe,batteryRecent?
   String(d.battery_percent)+"% (mínimo 15%)":"Sem telemetria recente do aplicativo")+
  row("GPS recente",gps,gps?"GPS recebido nos últimos 2 minutos":"Sem posição recente comprovada")+
  row("Corrida ativa",!!d.active_job_status,d.active_job_status?label(d.active_job_status):"Nenhuma corrida atribuída")+
  '</div><p class="small">Último GPS informado: '+escapeHtml(date(d.gps_last_at))+
  '</p><p class="small">O GPS do cliente somente aparece após a retirada e com a permissão do pedido.</p>';
 dialog("Diagnóstico UP • "+name,html);
}
async function manualJobDialog(){
 return window.UPManualAddress.open();
}
async function offerDialog(jobId){
 const [list,details,pricing]=await Promise.all([
  call("couriers_list"),call("job_detail",{job_id:jobId}),call("tariff_get")]);
 const couriers=(list.couriers||[]).filter(c=>c.state==="APPROVED"&&c.online);
 if(!couriers.length){notice("Nenhum entregador aprovado online.",true);return}
 const job=details.job||{},origin=job.pickup?.location,dest=job.dropoff?.location;
 const valid=p=>p&&Number.isFinite(+p.lat)&&Number.isFinite(+p.lng)&&Math.abs(+p.lat)<85&&Math.abs(+p.lng)<=180;
 const hasCoords=valid(origin)&&valid(dest),tariff=pricing.tariff||{},canAutomatic=hasCoords&&tariff.enabled===true;
 let route=null;
 dialog("Oferta com remuneração",
 '<div class="info-note">A taxa do cliente é independente do pagamento do entregador.</div>'+
 '<label for="offerCourier">Entregador</label><select class="field" id="offerCourier">'+
 couriers.map(c=>'<option value="'+escapeHtml(c.id)+'">'+escapeHtml(c.display_name)+'</option>').join("")+'</select>'+
 '<label for="offerMode">Tipo de tarifa</label><select id="offerMode" class="field">'+
 '<option value="manual">Valor manual</option>'+(canAutomatic?'<option value="automatic">Automático por quilômetros</option>':'')+'</select>'+
 '<label for="offerQuote">Valor manual do entregador (R$)</label>'+
 '<input class="field" id="offerQuote" placeholder="Ex.: 9,00" inputmode="decimal">'+
 (hasCoords?'<button id="offerRouteButton" type="button" class="button secondary">Calcular rota pelas ruas</button>':'<p class="small">Sem coordenadas. Informe valor manual.</p>')+
 '<p id="offerRouteText" class="small">'+(canAutomatic?"Calcule a rota para habilitar o valor automático.":"Valor automático indisponível; é possível oferecer manualmente.")+'</p>'+
 '<label for="offerMinutes">Expiração</label><select id="offerMinutes" class="field">'+
 '<option value="3">3 minutos</option><option value="5">5 minutos</option><option value="10">10 minutos</option></select>',
 "Enviar oferta",async()=>{
  const mode=$("#offerMode").value,payload={job_id:jobId,courier_id:$("#offerCourier").value,
    minutes:Number($("#offerMinutes").value),quote_mode:mode};
  let amount;
  if(mode==="automatic"){
   if(!route)throw Error("Calcule o percurso antes de enviar tarifa automática.");
   payload.route_distance_km=route.distance_km;payload.route_duration_minutes=route.duration_minutes;
   amount=Math.round(Math.max(Number(tariff.minimum_amount),Number(tariff.per_km)*route.distance_km)*100)/100;
  }else{
   const raw=$("#offerQuote").value.trim().replace(",",".");amount=Number(raw);
   if(!raw||!Number.isFinite(amount)||amount<=0||amount>10000||Math.abs(amount*100-Math.round(amount*100))>.000001)
    throw Error("Informe valor válido para o entregador.");
   payload.quote_amount=amount;
  }
  if(!confirm("Enviar oferta de "+money(amount)+" ao entregador selecionado?"))return false;
  await call("send_offer",payload);
  notice("Oferta com valor registrada no UP.");await load("jobs");
 });
 if(hasCoords){
  $("#offerRouteButton").onclick=async()=>{
   const btn=$("#offerRouteButton");btn.disabled=true;
   $("#offerRouteText").textContent="Calculando trajeto nas ruas…";
   try{
    route=await call("route_preview",{points:[
     {lat:+origin.lat,lng:+origin.lng},{lat:+dest.lat,lng:+dest.lng}]});
    const amount=canAutomatic?Math.round(Math.max(Number(tariff.minimum_amount),Number(tariff.per_km)*route.distance_km)*100)/100:null;
    $("#offerRouteText").textContent=route.distance_km.toFixed(2)+" km • "+route.duration_minutes.toFixed(1)+
     " min estimados. "+(amount!==null?"Valor do entregador: "+money(amount)+". ":"")+
     "Prévia OSRM; não inclui trânsito.";
    if(canAutomatic)$("#offerMode").value="automatic";
   }catch(e){route=null;$("#offerRouteText").textContent="Rota indisponível. Informe o valor manualmente.";notice(e.message,true)}
   finally{btn.disabled=false}
  };
 }
}
function protectedUrl(value){
 try{const u=new URL(value);return u.protocol==="https:"?u.href:null}
 catch{return null}
}
async function reviewDialog(appId){
 const r=await call("application_detail",{application_id:appId}),a=r.application;
 const docs=a.documents||{},durl=protectedUrl(docs.document_url),purl=protectedUrl(docs.photo_url);
 dialog("Analisar cadastro",'<div class="details-grid">'+
 smallInfo("Nome",a.full_name)+smallInfo("Telefone",a.phone)+
 smallInfo("E-mail",a.email)+smallInfo("Veículo",a.vehicle?.type)+
 smallInfo("Placa",a.vehicle?.plate)+smallInfo("Situação",label(a.state))+'</div>'+
 (purl?'<p><a href="'+escapeHtml(purl)+'" target="_blank" rel="noopener noreferrer">Ver foto enviada</a></p>':"")+
 (durl?'<p><a href="'+escapeHtml(durl)+'" target="_blank" rel="noopener noreferrer">Ver documento enviado</a></p>':"")+
 '<label for="reviewDecision">Decisão</label><select id="reviewDecision" class="field">'+
 '<option value="APPROVED">Aprovar</option><option value="NEEDS_CORRECTION">Solicitar correção</option>'+
 '<option value="REJECTED">Reprovar</option></select>'+
 '<label for="reviewReason">Motivo (obrigatório para correção ou reprovação)</label>'+
 '<textarea id="reviewReason" class="field field-textarea" maxlength="500" placeholder="Explique a decisão"></textarea>',
 'Salvar decisão',async()=>{
  const decision=$("#reviewDecision").value,reason=$("#reviewReason").value.trim();
  if(decision!=="APPROVED"&&reason.length<5)throw Error("Informe o motivo com pelo menos 5 caracteres.");
  if(!confirm("Registrar esta decisão sobre o cadastro?"))return false;
  await call("review_application",{application_id:appId,decision,reason});
  notice("Situação do cadastro atualizada.");await load("applications");
 });
}
async function driverState(id,state,reviewRequired=false){
 if(reviewRequired&&state==="APPROVED"){
  const typed=prompt("Cadastro importado: confira os documentos no sistema antigo. Para aprovar, digite APROVAR:");
  if(typed!=="APROVAR"){notice("Aprovação não realizada.");return;}
  if(!confirm("Confirmou a identidade e os documentos do entregador? Autorizar corridas no UP independente?"))return;
 }else{
  const phrase=state==="SUSPENDED"?"suspender":"reativar";
  if(!confirm("Deseja "+phrase+" este entregador no UP?"))return;
 }
 await call("set_driver_state",{courier_id:id,state});
 notice("Situação do entregador atualizada.");await load("couriers");
}
async function walletDialog(id){
 const r=await call("wallet",{courier_id:id});
 dialog("Extrato do entregador",r.transactions.length?
  '<div class="table-scroll"><table class="table"><thead><tr><th>Data</th><th>Movimento</th><th>Valor</th></tr></thead><tbody>'+
  r.transactions.map(t=>'<tr><td>'+date(t.created_at)+'</td><td>'+escapeHtml(t.description||t.kind)+
  '</td><td>'+money(t.amount)+'</td></tr>').join("")+'</tbody></table></div>':empty("Sem movimentações."));
}
function payoutDialog(id){
 dialog("Solicitar repasse",'<div class="warning-note">A solicitação registra o valor no UP. Não executa transferência Pix.</div>'+
 '<label for="payoutAmount">Valor (R$)</label><input id="payoutAmount" class="field" inputmode="decimal" placeholder="25,00">',
 "Registrar solicitação",async()=>{
  const amount=Number($("#payoutAmount").value.trim().replace(",","."));
  if(!Number.isFinite(amount)||amount<=0)throw Error("Informe um valor positivo.");
  if(!confirm("Registrar a solicitação de repasse de "+money(amount)+"?"))return false;
  await call("payout_request",{courier_id:id,amount});
  notice("Solicitação registrada. Nenhum Pix foi enviado.");await load("finance");
 });
}
function confirmPayout(id){
 dialog("Confirmar pagamento já realizado",
 '<div class="warning-note">Confirme somente depois de conferir o pagamento fora do sistema. Esta ação não envia dinheiro.</div>'+
 '<label for="paymentReference">Identificador do comprovante</label>'+
 '<input class="field" id="paymentReference" maxlength="180" placeholder="ID da transação ou referência">',
 'Registrar como pago',async()=>{
  const reference=$("#paymentReference").value.trim();
  if(reference.length<5)throw Error("Informe a referência do pagamento.");
  if(!confirm("Você confirma que este repasse já foi pago?"))return false;
  await call("payout_confirm",{payout_id:id,reference});
  notice("Pagamento registrado no UP.");await load("finance");
 });
}
document.addEventListener("click",async event=>{
 const target=event.target.closest("[data-action]");if(!target)return;
 const action=target.dataset.action;
 if(action==="close-dialog"){closeDialog();return}
 if(action==="logout"){clearSession();return}
 if(action==="refresh"){
  if(STORE.page==="jobs"&&STORE.filter==="rejected"){
   delete STORE.data.offer_history;
   await load();await loadOfferHistory();
  }else await load();
  return;
 }
 if(action==="probe-integration"){
  if(STORE.busy)return;
  STORE.busy=true;target.disabled=true;target.textContent="Verificando assinatura…";
  try{
   const result=await call("integration_probe");
   STORE.data.integrations=STORE.data.integrations||{};
   STORE.data.integrations.probe=result.probe||null;
   renderIntegrations();
   notice(result.probe?.message||"Verificação concluída.",result.probe?.connection_ok!==true);
  }catch(error){notice(error.message||"Falha no teste de comunicação.",true)}
  finally{STORE.busy=false;target.disabled=false;}
  return;
 }

 if(action==="go"){STORE.filter="all";await navigate(target.dataset.page);return}
 if(action==="map-zoom-in"){window.UPMap?.zoom(1);return}
 if(action==="map-zoom-out"){window.UPMap?.zoom(-1);return}
 if(action==="map-recenter"){window.UPMap?.recenter();return}
 if(action==="map-select"){window.UPMap?.select(target.dataset.id);return}
 if(action==="map-driver"){
  if(STORE.page==="map")window.UPMap?.select(target.dataset.id);
  else{STORE.mapFocus=target.dataset.id;await navigate("map")}
  return;
 }
 if(action==="filter"){
  STORE.filter=target.dataset.filter;render();
  if(STORE.page==="jobs"&&STORE.filter==="rejected"){
   try{await loadOfferHistory()}catch(e){notice(e.message||"Falha ao consultar recusas.",true)}
  }
  return;
 }
 if(STORE.busy)return;
 try{
  if(action==="merchant-draft")await merchantDraftDialog();
  else if(action==="driver-diagnostics")await driverDiagnosticsDialog(target.dataset.id);
  else if(action==="view-job")await jobDetail(target.dataset.id);
  else if(action==="new-job")await manualJobDialog();
  else if(action==="send-offer")await offerDialog(target.dataset.id);
  else if(action==="review-application")await reviewDialog(target.dataset.id);
  else if(action==="driver-state")await driverState(target.dataset.id,target.dataset.state,target.dataset.reviewRequired==="true");
  else if(action==="wallet")await walletDialog(target.dataset.id);
  else if(action==="payout")payoutDialog(target.dataset.id);
  else if(action==="mark-paid")confirmPayout(target.dataset.id);
 }catch(e){notice(e.message,true);}
});
$("#loginForm").addEventListener("submit",login);
document.addEventListener("keydown",event=>{
 if(event.key==="Escape"&&!$("#dialogBackdrop").classList.contains("hidden"))closeDialog();
});
async function loadOfferHistory(){
 if(STORE.data.offer_history)return;
 STORE.data.offer_history=await call("offer_history");
 if(STORE.page==="jobs"&&STORE.filter==="rejected")render();
}
// Respect the free-tier quota: only refresh visible operations, at most every 90 s.
const OPERATION_REFRESH_MS=90000;
setInterval(()=>{
 if(!STORE.active||STORE.busy||document.hidden)return;
 if(!$("#dialogBackdrop").classList.contains("hidden"))return;
 if(!["overview","jobs","couriers","map"].includes(STORE.page))return;
 load(STORE.page,true).then(async()=>{
  if(STORE.page==="jobs"&&STORE.filter==="rejected"){
   delete STORE.data.offer_history;
   await loadOfferHistory();
  }
 }).catch(()=>{});
},OPERATION_REFRESH_MS);
show(configOkay()?"login":"configuration");
