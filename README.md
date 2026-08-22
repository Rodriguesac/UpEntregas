# UP Entregas 2.5 — Rastreamento opcional

Aplicativo Android nativo do entregador do ecossistema Rodrigues Açaí e Cia / UP Entregas.

## Versão
`2.5.0-rastreamento-opcional` — `versionCode 250`

## Princípio do produto
O entregador deve enxergar a **próxima ação da entrega** com o mínimo de distração possível. Cadastro, login, formulários, confirmações, rotas, pagamentos e ocorrências são interfaces do próprio UP.

O Android só assume a tela quando a plataforma exige: permissões, seletor protegido, configurações de GPS/bateria e notificações quando o aplicativo estiver em segundo plano.

## Fluxo operacional
Gestor direciona → UP recebe oferta → entregador aceita → vai à loja → confirma chegada → confirma retirada → rota fecha → segue ao cliente → confirma chegada → código de entrega → financeiro/acerto.

Em rota múltipla, pedidos adicionais podem entrar **somente antes da retirada**, seguindo o Protocolo UP V3.

## Visual
- Material 3
- identidade UP azul/amarela
- mapa central durante missão
- uma ação principal por etapa
- sem `AlertDialog`, `Dialog` ou Toast para dados e ações de negócio
- painéis internos `InAppPanel`
- formulários com `TextInputLayout`

Veja `docs/PADRAO-VISUAL-UP.md` e `CHANGELOG-V2.3.md`.

## Build
O GitHub Actions gera `UP-Entregas.apk` no workflow **Gerar APK UP Entregas**.

Para publicar pelo Termux, veja `TERMUX-PUBLICAR-V2.3.txt`.

## V2.5.0 — rastreamento opcional do cliente
O GPS continua restrito à missão ativa. Depois da retirada, a localização é espelhada somente nos pedidos cujo mapa foi habilitado pelo Gestor; rotas múltiplas agora sincronizam as coordenadas com cada pedido autorizado. Consulte `CHANGELOG-V2.5.md`.


## v2.3.1 — retirada em rota múltipla
Rota múltipla não solicita um PIN extra de retirada. O entregador confere os pedidos, confirma **Retirei todos os pedidos**, a rota fecha para complementos e o app avança para a primeira parada.

## V2.4.0 — Rota Premium
A rota múltipla ativa agora usa a nova hierarquia visual do UP: mapa em destaque, parada atual expandida, próximas paradas recolhidas, pagamento contextual e ação principal por etapa. Consulte `CHANGELOG-V2.4.md` e `docs/UP-ROTA-PREMIUM-V2.4.md`.
