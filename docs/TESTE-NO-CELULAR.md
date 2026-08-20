# Teste controlado — UP Entregas v1.4.0 PRO

## 1. Instalação e permissões

1. Instale o APK gerado pelo GitHub Actions.
2. Entre com o CPF/ID e senha do entregador.
3. Abra **Permissões do aparelho**.
4. Confirme notificações liberadas.
5. Confirme localização precisa liberada.
6. Confirme GPS ligado.
7. Libere o app das restrições de bateria, quando o aparelho permitir.
8. Use **TESTAR NOTIFICAÇÃO DE CORRIDA** e confirme que aparece heads-up/som/vibração.

## 2. Serviço ONLINE

1. Volte para a Home.
2. Ative ONLINE.
3. Confirme a notificação permanente `UP Entregas • ONLINE`.
4. Coloque o app em segundo plano sem usar Forçar parada.
5. No GADM, envie uma corrida Via UP para esse entregador.
6. Confirme que a notificação de nova corrida aparece e abre o pedido correto.

## 3. Corrida completa

1. Aceite a corrida.
2. Confirme status OCUPADO.
3. Abra mapa para o Rodrigues.
4. Toque **CHEGUEI NO RODRIGUES**.
5. Informe o código de retirada.
6. Toque **RETIREI O PEDIDO**.
7. Confirme notificação de corrida/localização ativa.
8. Confira atualização de localização no GADM/cliente.
9. Abra mapa para o cliente.
10. Toque **CHEGUEI AO CLIENTE**.
11. Informe código de entrega e valor recebido.
12. Finalize.
13. Confirme: pedido entregue, corrida encerrada, acerto criado e entregador volta a Livre/ONLINE.
14. Confirme que a notificação ONLINE volta após o fim da corrida.

## 4. Casos de falha

- Recusar oferta: deve voltar ao gestor, sem encaminhar automaticamente a outro entregador.
- Deixar expirar: deve voltar ao gestor.
- Fechar e reabrir o app durante corrida: deve recuperar a missão.
- Desligar internet e religar: o listener deve restabelecer a sincronização.
- Registrar ocorrência: deve aparecer no GADM.

## 5. Não liberar ainda se

- notificação permanente não aparece ao ficar ONLINE;
- oferta não chega com app em segundo plano;
- código de retirada/entrega não valida;
- GPS não atualiza durante missão;
- conclusão não libera o entregador;
- GADM e cliente não refletem as etapas.
