# UP Entregas — Independência de infraestrutura
**Plano de migração seguro — 04/11/2026**

## Estado real verificado
- Projeto atual de Rodrigues Açaí e Cia: `rodriguesacaiecia2`, que também mantém parte do UP.
- UP Android ainda aponta sua `driver-api` para esse projeto. O painel UP também acessa `gestor-orders` e `up-manager-data` nele.
- A loja envia pedidos prontos à fila existente `AGUARDANDO_ENTREGADOR` via `gadm-up-handoff` (projeto da loja).
- O acompanhamento do cliente já reconhece `AGUARDANDO_ENTREGADOR` e `BUSCANDO_ENTREGADOR`.
- Banco observado no levantamento: 3 entregadores, 2 cadastros de candidatura, 107 pontos de localização, 0 repasses, 0 movimentos de carteira, 1 ajuste UP. Contagens são um retrato, não um backup.

## Limites definitivos
| Rodrigues Açaí e Cia (loja) | UP Entregas (independente) |
| --- | --- |
| Carrinho, cliente, endereço e pagamento | Motorista, corrida, GPS, ofertas, carteira e repasses |
| Pedido, preparo e status PRONTO | Aceite de corrida, coleta, rota e entrega |
| Envia 1 evento de entrega pronto autenticado | Cria job idempotente e administra a corrida |
| Recebe eventos de estado sem credenciais UP | Assina eventos para atualização do pedido da loja |
| Exibe rastreamento somente ao cliente autorizado | Armazena latitude/longitude frequente e retém por tempo limitado |

O painel GADMINISTRA **não** terá tela de entregas, taxa, cadastro de motoboys ou mapa operacional. Pode exibir apenas o estado recebido do UP no pedido.

## Preparação já versionada
1. `supabase/up-independent-schema.sql`: estrutura do projeto futuro, 10 tabelas operacionais, RLS ligado e sem autorização de acesso direto a `anon` ou `authenticated`.
2. `supabase/functions/up-order-bridge/index.ts`: recepção de `create_job` e leitura de `get_status`, ambas por HMAC e com janela de cinco minutos.
3. `public.up_receive_handoff`: operação transacional e idempotente; rejeita alterações disfarçadas do mesmo pedido.
4. Nenhuma dessas estruturas foi aplicada ao banco da loja. Nenhum dado pessoal de cliente ou motorista foi enviado ao GitHub.

## Fase 1 — Provisionamento do projeto separado
- Usuário confirma organização do Supabase de destino.
- Criar **outro projeto** chamado `up-entregas` em `sa-east-1` se disponível e sem custo, ou na região escolhida.
- Confirmar a cotação antes de assumir qualquer custo. Projeto nunca deve ser criado com cobrança não aprovada.
- Aplicar `up-independent-schema.sql` somente no novo projeto; validar tabelas, RLS, permissões e chamadas de API sem privilégios.
- Configurar secrets `UP_BRIDGE_SECRET` (gerado aleatoriamente, mínimo 32 bytes), `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` nas Edge Functions **exclusivamente no servidor**. Nunca colocar secrets em APK, HTML, GitHub ou resposta do usuário.

## Fase 2 — Modelo de integração
- Loja valida pedido `PRONTO`, modalidade `ENTREGA`, pagamento permitido, endereço e identidade do cliente.
- Servidor da loja emite evento de handoff com ID UUID estável, HMAC SHA-256 sobre `timestamp + "\n" + JSON exato`.
- Novo UP valida tempo de mensagem, assinatura, formato e deduplicação pelo pedido + ID do evento.
- UP cria `WAITING_DRIVER` e devolve confirmação. **Não** cria corrida a partir de chamadas de navegador ou de um PIN do gestor.
- Sempre que houver nova etapa, UP envia uma notificação assinada para o serviço da loja; este apenas reflete estado de entrega e notifica o cliente.
- O rastreamento no site do cliente deve consultar dados autorizados do UP, usando token de acompanhamento de escopo mínimo e sem liberar histórico de outro usuário.

## Fase 3 — Migrar dados existentes
- Exportação criptografada por canal privado; jamais commitar CPF, documentos, telefone, PIX, endereço ou coordenadas.
- Tabelas de origem: `couriers`, `courier_applications`, `tracking_points`, `courier_wallet_transactions`, `courier_payouts`, `up_manager_settings`, `up_manager_audit`.
- Transformar colunas e manter o mapa de IDs antigos para IDs novos. Validar unicidade do `firebase_uid`.
- Dados financeiros e documentos exigem revisão de integridade, auditoria e acesso; pontos antigos de GPS deverão seguir política de minimização e retenção.
- Pedidos históricos permanecem na loja. No UP são armazenados somente os jobs necessários à operação e à auditoria de corrida.

## Fase 4 — Aplicativos UP
- Publicar `driver-api` e `driver-registration` adaptadas ao schema independente e à validação do Firebase Auth existente.
- Trocar URL configurável do backend no Android e no painel UP; **não** transportar `service_role` ao celular.
- Testar cadastro, aceitação de corridas, geolocalização, motoboy offline, push e repasses num ambiente isolado.
- Fazer migração gradual por versão do app, preservando rollback.

## Fase 5 — Virada controlada
Apenas quando todos os itens abaixo forem **verdadeiros**:
1. Novos jobs chegam ao Supabase UP uma vez só, mesmo após repetição e reconexão.
2. App entregador recebe oferta com Firebase Auth válido; usuário não autorizado não recebe.
3. GPS é gravado **somente** no projeto UP e não mais na base da loja.
4. Cliente vê progresso e mapa da própria entrega (não de outra pessoa).
5. Evento de entrega/insucesso retorna à loja com integridade verificada.
6. Cancelamento, pagamento pendente, corrida atribuída e pedidos antigos não podem disparar jobs duplicados.
7. Push, histórico, auditoria, operações offline e estornos foram testados com dados de teste.
8. Snapshot e rollback aprovados; manter legado habilitado para leitura até concluir a reconciliação.

**Status atual:** arquitetura e arquivos de preparação concluídos; projeto UP separado ainda não provisionado e migração de produção NÃO executada.

## Segurança e operação
- Nenhuma posição GPS é pública. Dados do cliente e do entregador devem ser minimizados.
- Dados do driver tratados no servidor com Firebase JWT verificado pelo backend UP.
- Integrações entre projetos usam HMAC com segredo privado e proteção antirreplay.
- RLS em tabelas expostas; `anon` e `authenticated` sem permissões diretas.
- Nunca permitir dupla escrita de GPS entre projetos por tempo indeterminado.
- Retenção de localização, acesso, incidentes e revogação devem estar documentados antes do lançamento.
