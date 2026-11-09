"use strict";

// Never store Firebase ID/refresh tokens or a manager password in localStorage,
// sessionStorage, URLs or persistent cookies. A refresh requires signing in again.
const CONFIG=window.UP_MANAGER_CONFIG||{};
const STORE={token:"",refreshToken:"",expires:0,user:null,page:"overview",
 busy:false,filter:"all",data:{},active:false};
const MENU=[
 {id:"overview",label:"Visão geral",short:"Início",icon:"⌂",desc:"Indicadores operacionais"},
 {id:"jobs",label:"Corridas",short:"Corridas",icon:"▤",desc:"Ofertas, eventos e andamento"},
 {id:"couriers",label:"Entregadores",short:"Entregadores",icon:"♙",desc:"Disponibilidade e situação"},
 {id:"applications",label:"Cadastros",icon:"▣",desc:"Análise e aprovação"},
 {id:"finance",label:"Financeiro",icon:"◈",desc:"Saldos e repasses registrados"},
 {id:"occurrences",label:"Ocorrências",icon:"⚑",desc:"Registros da operação"},
 {id:"audit",label:"Auditoria",icon:"▥",desc:"Histórico administrativo"},
 {id:"integrations",label:"Integrações",icon:"⌁",desc:"Verificação segura dos serviços"}
];
const MOBILE=["overview","jobs","couriers","more"];
const LABELS={
 WAITING_DRIVER:"Aguardando entregador",OFFERED:"Oferta enviada",ACCEPTED:"Aceita",
 TO_STORE:"Indo à loja",AT_STORE:"Na loja",TO_CUSTOMER:"Em rota",
 AT_CUSTOMER:"No cliente",DELIVERED:"Entregue",CANCELLED:"Cancelada",FAILED:"Falha na corrida",
 APPROVED:"Aprovado",SUSPENDED:"Suspenso",PENDING:"Pendente",
 NEEDS_CORRECTION:"Correção solicitada",REJECTED:"Reprovado",
 REQUESTED:"Solicitado",PAID:"Pago"
};
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
  ["REJECTED","CANCELLED","FAILED","SUSPENDED"].includes(st)?"bad":"blue";
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
 STORE.active=false;
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
 const pages=MENU.filter(x=>["finance","audit","integrations"].includes(x.id)?
   (x.id==="audit"?STORE.user?.role==="OWNER":isManager()):true);
 $("#sideNav").innerHTML=pages.map(x=>'<button type="button" data-action="go" data-page="'+x.id+'" '+
  'class="'+(STORE.page===x.id?"active":"")+'"><span class="nav-icon">'+x.icon+
  '</span>'+x.label+'</button>').join("");
 const more=["overview","jobs","couriers"].includes(STORE.page)?STORE.page:"more";
 $("#bottomNav").innerHTML=MOBILE.map(id=>{
  const p=pages.find(x=>x.id===id);
  const obj=id==="more"?{short:"Mais",icon:"⋯"}:p;
  return '<button type="button" data-action="go" data-page="'+id+
   '" class="'+(more===id?"active":"")+'"><span class="nav-icon">'+obj.icon+
   '</span>'+obj.short+'</button>';
 }).join("");
 $("#pageTitle").textContent=STORE.page==="more"?"Mais":pages.find(x=>x.id===STORE.page)?.label||"UP Gestor";
}
async function navigate(next,reload=true){
 if(!allowedIds.has(next))return;
 if(next==="audit"&&STORE.user?.role!=="OWNER")return;
 if(["finance","integrations"].includes(next)&&!isManager())return;
 STORE.page=next;
 navigation();
 if(next==="more"){renderMore();return;}
 if(reload||!STORE.data[next])await load(next);else render();
}
async function load(page=STORE.page){
 if(STORE.busy)return;
 STORE.busy=true;$("#refresh").disabled=true;
 $("#connection").textContent="Atualizando";$("#connection").classList.remove("offline");
 $("#main").innerHTML='<div class="loading-block">Carregando dados do UP…</div>';
 const calls={overview:"dashboard",jobs:"jobs_list",couriers:"couriers_list",
  applications:"applications_list",finance:"finance",occurrences:"occurrences",audit:"audit",integrations:"integration_health"};
 try{
  STORE.data[page]=await call(calls[page]);
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
function renderCouriers(){
 const couriers=STORE.data.couriers?.couriers||[];
 $("#main").innerHTML='<p class="page-intro">Presença, aprovação e disponibilidade pertencem exclusivamente à operação do UP.</p>'+
 (couriers.map(c=>'<article class="record"><div class="record-head"><div><h3>'+
  escapeHtml(c.display_name)+'</h3><p>'+escapeHtml(c.phone||"Sem telefone")+'</p></div>'+
  badge(c.state)+'</div><div class="details-grid" style="margin-top:13px">'+
  smallInfo("Presença",c.online?"Online":"Offline")+
  smallInfo("Último GPS",date(c.last_location_at))+
  '</div>'+(c.state==="PENDING"?'<div class="warning-note">Importação pendente. Confira identificação e documentos no cadastro anterior antes de aprovar. O entregador não pode receber ofertas.</div>':"")+
 (isManager()?'<div class="actions"><button class="button '+(c.state==="SUSPENDED"||c.state==="PENDING"?"green":"secondary")+
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
function renderMore(){
 $("#main").innerHTML='<p class="page-intro">Cada função administrativa está organizada na área correspondente.</p>'+
 '<div class="more-grid">'+MENU.filter(x=>!["overview","jobs","couriers"].includes(x.id)&&
  (!["finance","integrations"].includes(x.id)||isManager())&&(x.id!=="audit"||STORE.user?.role==="OWNER"))
 .map(x=>'<button class="more-card" data-action="go" data-page="'+x.id+'">'+
  x.icon+" "+x.label+"<span>"+escapeHtml(x.desc)+'</span></button>').join("")+
 '<button class="more-card" data-action="logout">Sair da conta<span>Encerrar sessão administrativa</span></button></div>';
}
function render(){
 if(STORE.page==="overview")renderOverview();
 else if(STORE.page==="jobs")renderJobs();
 else if(STORE.page==="couriers")renderCouriers();
 else if(STORE.page==="applications")renderApplications();
 else if(STORE.page==="finance")renderFinance();
 else if(STORE.page==="occurrences")renderOccurrences();
 else if(STORE.page==="audit")renderAudit();
 else if(STORE.page==="integrations")renderIntegrations();
 else renderMore();
}
function closeDialog(){
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
async function manualJobDialog(){
 const result=await call("merchant_locations");
 const points=(result.merchants||[]).flatMap(m=>(m.locations||[]).map(l=>({
  value:m.id+":"+l.id,label:m.display_name+" — "+l.label+" ("+(l.city||"Sem cidade")+")"
 })));
 if(!points.length){notice("Nenhuma loja ativa possui ponto de retirada ativo.",true);return;}
 const requestId=crypto.randomUUID();
 const opts=points.map(p=>'<option value="'+p.value+'">'+escapeHtml(p.label)+'</option>').join("");
 const field=(id,label,max=120,extra="")=>
  '<label for="'+id+'">'+label+'</label><input class="field" id="'+id+'" maxlength="'+max+'" autocomplete="off" '+extra+'>';
 dialog("Solicitar entrega",
  '<div class="warning-note">Sem cobrança automática. O preço do frete ainda depende da configuração de tarifas.</div>'+
  '<label for="manualPickup">Loja e ponto de coleta</label><select class="field" id="manualPickup">'+opts+'</select>'+
  field("manualCode","Número do pedido (somente números)",32,'inputmode="numeric"')+
  field("manualCustomer","Nome do cliente",150)+
  field("manualPhone","Telefone do cliente",20,'type="tel" inputmode="tel"')+
  field("manualStreet","Rua de entrega",180)+field("manualNumber","Número",20)+
  field("manualNeighborhood","Bairro",120)+field("manualCity","Cidade",120)+
  field("manualState","UF",2,'placeholder="MS"'),
  "Criar solicitação",async()=>{
   const [merchant_id,location_id]=$("#manualPickup").value.split(":");
   const order_code=text($("#manualCode").value);
   const recipient_name=text($("#manualCustomer").value);
   const recipient_phone=text($("#manualPhone").value).replace(/\D/g,"");
   const dropoff={
    street:text($("#manualStreet").value),number:text($("#manualNumber").value),
    neighborhood:text($("#manualNeighborhood").value),city:text($("#manualCity").value),
    state:text($("#manualState").value).toUpperCase()
   };
   if(!/^\d{3,32}$/.test(order_code))throw Error("Número de pedido inválido: use somente 3 a 32 dígitos.");
   if(recipient_name.length<2||recipient_phone.length<10||recipient_phone.length>15||
      dropoff.street.length<3||dropoff.city.length<2)
    throw Error("Preencha nome, telefone, rua e cidade.");
   if(!confirm("Solicitar entrega sem cobrança automática?"))return false;
   const r=await call("create_manual_job",{
    merchant_id,location_id,request_id:requestId,order_code,recipient_name,recipient_phone,dropoff
   });
   if(!r.job?.job_id)throw Error("UP não confirmou a solicitação.");
   notice(r.job.already_applied?"Solicitação existente confirmada.":"Solicitação registrada no UP.");
   await load("jobs");
  });
}
async function offerDialog(jobId){
 const r=await call("couriers_list");
 const couriers=r.couriers.filter(x=>x.state==="APPROVED"&&x.online);
 if(!couriers.length){notice("Nenhum entregador aprovado está online.",true);return}
 const options=couriers.map(c=>'<option value="'+c.id+'">'+escapeHtml(c.display_name)+'</option>').join("");
 dialog("Enviar oferta ao entregador",'<div class="warning-note">A corrida será oferecida exclusivamente ao motorista selecionado, por tempo limitado.</div>'+
 '<label for="offerCourier">Entregador disponível</label><select class="field" id="offerCourier">'+options+'</select>'+
 '<label for="offerMinutes">Validade da oferta</label><select class="field" id="offerMinutes">'+
 '<option value="3">3 minutos</option><option value="5">5 minutos</option>'+
 '<option value="10">10 minutos</option></select>',
 "Enviar oferta",async()=>{
  const id=$("#offerCourier").value,mins=Number($("#offerMinutes").value);
  if(!confirm("Enviar esta corrida para o entregador escolhido?"))return false;
  await call("send_offer",{job_id:jobId,courier_id:id,minutes:mins});
  notice("Oferta registrada no UP.");
  await load("jobs");
 });
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
 if(action==="refresh"){await load();return}
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
 if(action==="filter"){STORE.filter=target.dataset.filter;render();return}
 if(STORE.busy)return;
 try{
  if(action==="view-job")await jobDetail(target.dataset.id);
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
show(configOkay()?"login":"configuration");
