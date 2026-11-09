# UP Entregas — Fundação multiestabelecimento e retirada dinâmica
Data: 2026-11-09. Status: estrutura criada; nenhum parceiro externo ou corrida real ativado.

## Princípios

O UP Entregas será uma plataforma independente, com múltiplos estabelecimentos. Rodrigues Açaí e Cia é o primeiro parceiro, mas não é codificado como o único possível. Cada parceiro possui estabelecimentos, locais de retirada, operadores, integrações e permissões próprios.

Cada endereço é editável: sede fixa, trailer móvel, evento ou endereço temporário. Um parceiro pode cadastrar vários pontos, escolher um padrão ativo, editar a posição no mapa e pausar. Cada corrida grava um snapshot de retirada, de modo que uma mudança de endereço não redirecione pedidos anteriores. Para trailer ativo, usar geolocalização validada.

## Banco do UP instalado

- up_merchants: parceiros cadastrados, com situação DRAFT/ACTIVE/SUSPENDED/CLOSED.
- up_merchant_locations: locais FIXED/MOBILE, coordenadas, revisões e ponto padrão.
- up_merchant_members: usuários individuais por parceiro e papéis, sem autoaprovação.
- up_merchant_integrations: provedor, referência externa e referência de segredo, sem armazenar a chave em texto claro.
- up_jobs.merchant_id: todas as corridas têm parceiro definido; integração legada aponta para Rodrigues.
- up_jobs.merchant_location_id: vínculo opcional protegido por FK composta (mesmo parceiro).
- RLS ativo; sem acesso direto de anon/authenticated.
- Rodrigues criada em DRAFT, sem inventar endereço e sem liberar entregas.

## Banco da loja instalado

- up_store_pickup_locations: local FIXED ou MOBILE, endereço e GPS, revisão, estado e seleção padrão.
- up_store_activate_pickup(uuid,text): RPC somente do service_role, via administração verificada.
- GADMINISTRA usará as ações autenticadas pickup_list, pickup_save, pickup_activate, pickup_pause, implementadas na Edge Function gadm-up-handoff.
- O handoff passará a preferir ponto ativo cadastrado. Se nunca houve ponto cadastrado, usa temporariamente UP_STORE_PICKUP_ADDRESS_JSON (compatibilidade). Se o parceiro cadastrou locais mas nenhum está ativo, a operação é bloqueada; nunca recuar para endereço antigo.
- Um trailer pode ser registrado com rua/cidade e coordenadas, sem número, usando S/N no snapshot de entrega.
- Nenhum local foi registrado sem confirmação do operador.

## Interfaces planejadas

GADMINISTRA: Configurações > Entregas UP > Pontos de retirada. Lista sede/trailer/evento, editar rua e mapa, ativar/pausar, mostrar ponto escolhido antes de despachar. Os formulários ainda precisarão ser ligados à API segura.

UP Gestor: Parceiros, locais de retirada, usuários e credenciais, tarifas, zonas atendidas e relatórios separados por estabelecimento. Um entregador poderá atender múltiplos estabelecimentos autorizados sem consultar dados financeiros ou privados dos demais.

## Bloqueios obrigatórios antes do segundo parceiro

A integração atual é monoparceiro: usa UP_BRIDGE_SECRET global, UP_STATUS_STORE_ENDPOINT fixo da Rodrigues e unicidade legada em merchant_order_id. Não habilitar estabelecimentos externos até:
1. autenticação HMAC e rotação de credenciais por estabelecimento em Edge Secrets ou Vault;
2. identificação de parceiro pela credencial assinada verificada (nunca confiar em merchant_id ou slug enviados pelo navegador);
3. idempotência por (merchant_id, merchant_order_id), mantendo compatibilidade com a loja original;
4. callbacks de status assinados e URL validada por parceiro;
5. isolamento de pedidos, usuários, GPS, histórico, auditoria e finanças por parceiro;
6. condições comerciais, aprovação e testes ponta a ponta.

## Operação

O UP continua com banco próprio e a loja mantém o dela. Endereços são snapshots por corrida. Nenhuma chave privada será colocada no frontend ou nas tabelas de parceiros. Até a homologação, handoff real, corridas Android, GPS para clientes e push permanecem desligados.

Próximos trabalhos: ligar UI do GADMINISTRA à API pickup, publicar API validada, criar gestão de parceiros no UP Gestor e só então implementar roteador de credenciais e callbacks multiestabelecimento.
