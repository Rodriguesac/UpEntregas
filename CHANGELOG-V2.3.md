# UP Entregas 2.3.0 — Material Native

Esta versão consolida o padrão visual e de interação do aplicativo do entregador sem alterar o Protocolo UP V3 das rotas.

## Interface própria do UP
- Removidas as janelas `Dialog` do fluxo de corrida, retirada, entrega, recusa, ocorrência, Pix, acertos e saída.
- Todos esses fluxos agora usam `InAppPanel`, anexado à própria árvore da Activity.
- Mensagens operacionais deixam de usar Toast e passam a usar Snackbar visual do próprio aplicativo.
- As únicas telas externas permitidas são as obrigatórias do Android: permissões, seletor de arquivos/fotos, localização e bateria.

## Material 3
- Tema migrado para `Theme.Material3.DayNight.NoActionBar`.
- Botões usam `MaterialButton`, cantos e espaçamentos padronizados.
- Switches operacionais usam `SwitchMaterial`.
- Campos novos e de confirmação usam `TextInputLayout`/`TextInputEditText` com rótulo e validação inline.
- Mapa da missão ganhou área maior e acabamento arredondado.

## Login e cadastro
- Login redesenhado com hierarquia visual limpa, campo de senha Material e recuperação de senha dentro da tela.
- Consulta do cadastro tem estado de carregamento próprio.
- Cadastro redesenhado em 6 etapas com barra de progresso, seleção interna de veículo e tipo Pix, revisão e tela de sucesso.
- Correção de cadastro reaproveita os dados existentes para reduzir redigitação.
- Nenhuma confirmação do cadastro depende de Toast ou AlertDialog.

## Home
- Diagnóstico técnico não ocupa mais a Home.
- A Home mostra apenas um aviso compacto quando uma permissão essencial estiver faltando.
- Configuração detalhada continua na Central de Permissões.

## Operação preservada
- Rota simples, rota múltipla, complemento antes da retirada, códigos, mapa, GPS, troco, maquininha e financeiro continuam sobre o Protocolo UP V3.
