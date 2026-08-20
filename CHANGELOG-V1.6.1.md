# UP Entregas V1.6.1 — Recuperação de cadastro

- corrige o erro de e-mail já existente no Firebase Authentication;
- se a conta já existe e a senha está correta, o app reaproveita o mesmo UID;
- se ainda não houver ficha de entregador, conclui o cadastro sem criar outra conta;
- se o cadastro estiver pendente ou em correção, reenvia os dados para análise;
- se a conta já estiver aprovada, não sobrescreve nem volta o entregador para pendente;
- mensagens de erro de autenticação ficaram mais claras.
