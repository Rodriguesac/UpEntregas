# UP Gestor — Central Operacional v3

Código-fonte: `web-gestor-independente/`, backend exclusivo em `supabase/functions/up-manager-independent/index.ts`.

## Endereço publicado
https://gkwmqwtktedarodvqqtn.supabase.co/functions/v1/up-gestor-web

A versão 3 está servida como função Edge pública de arquivos estáticos; operações exigem login Firebase e papel ativo no UP. O projeto Firebase `upgestor-rac.web.app` permanece como destino oficial desejado, mas sua rotina de deploy requer o segredo `UP_FIREBASE_SERVICE_ACCOUNT_JSON`, ausente nos últimos testes de publicação.

## Segurança e economia
- Nunca salvar senhas/tokens no navegador; sessão em memória com Firebase JWT validado no servidor
- Nenhum acesso ao banco de pedidos Rodrigues Açaí; ambiente UP exclusivo
- GPS retornado apenas para entregadores aprovados, online, rastreamento habilitado, com posição válida até cinco minutos; não retorna histórico
- Mapa só carrega mosaicos OpenStreetMap quando o operador abre a aba Mapa, com atribuição obrigatória
- Rejeições e expirações independentes das entregas pagas; sem créditos de repasse
- Atualização da aba atual a cada 90 segundos quando visível e autenticada; nenhuma consulta em segundo plano
- CSP restritiva, proteção de iframe, código no GitHub privado, sem service role ou HMAC no frontend

## Limitações e testes
- Verificados sintaxe JS, lógica da UI, regras de autorização, build do servidor Edge e deploy ativo
- Login e navegação em dispositivo real requerem teste com a conta administrativa aprovada
- Rotas reais em ruas não são desenhadas no mapa do gestor; ele mostra posições recentes dos entregadores
- Firebase Hosting oficial não foi atualizado automaticamente devido à ausência da credencial de CI
