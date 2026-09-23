# Schedulers — rotinas financeiras

Rotinas agendadas sobre uma base PostgreSQL pré-construída, cobrindo os quatro tipos de gatilho do Spring, agendamento reprogramável em runtime e lock distribuído.

Rodar um `@Scheduled` é trivial. O que este projeto aborda é o que quebra quando a rotina vai para produção: pool de uma thread só serializando tudo em silêncio, execução duplicada quando sobem réplicas, job que demora mais que o próprio intervalo, shutdown no meio de um lote, e reprocessamento de algo que já foi processado.

## Status

✅ Implementado, testado e medido.

## Stack

- Java 21 + Spring Boot 3.5
- PostgreSQL 16 + Spring Data JPA + Flyway
- **ShedLock 6.6** (provider JDBC) para lock distribuído
- Gradle (Kotlin DSL) + wrapper `gradlew` (toolchain Java 21 resolvida automaticamente)
- Lombok nas entidades JPA; DTOs como records
- Testcontainers + Awaitility + JUnit 5 + AssertJ

## As rotinas

| Rotina | Gatilho | Demonstra |
|---|---|---|
| `reconciliation` | cron vindo do banco | Processamento em chunks, idempotência, lock distribuído |
| `interest-accrual` | cron vindo do banco | Idempotência por data, arredondamento monetário, fuso explícito |
| `execution-log-purge` | `@Scheduled(fixedDelay)` | Por que fixedDelay e não fixedRate para trabalho de duração variável |
| demo rate/delay | `@Scheduled(fixedRate)` e `(fixedDelay)` | A diferença entre os dois, medida |

## fixedRate x fixedDelay, medido

Ambas as tarefas levam **300 ms** e estão configuradas com intervalo de **100 ms** — de propósito menor que o trabalho, que é onde as duas deixam de se comportar igual. Saída real de `GET /jobs/rate-vs-delay-demo`:

```
fixedRate  gaps entre inícios : [302, 300, 301, 300] ms
fixedDelay gaps entre inícios : [402, 400, 401, 400] ms
```

- **fixedRate** conta a partir do **início** anterior. Como a execução dura mais que o intervalo, a próxima já está atrasada quando a atual termina: as execuções saem coladas e o intervalo entre inícios desaba para a duração da tarefa (~300 ms). O intervalo configurado **não é respeitado** — ele é apenas um piso. Importante: isso **não** significa rodar duas cópias ao mesmo tempo; `scheduleAtFixedRate` nunca sobrepõe uma tarefa com ela mesma.
- **fixedDelay** conta a partir do **fim** anterior. O intervalo entre inícios é duração + intervalo (~400 ms), e a pausa é sempre honrada.

Na prática: use `fixedDelay` quando a duração varia e você quer garantir respiro entre execuções; use `fixedRate` quando a cadência importa mais que o espaçamento — sabendo que, sob carga, ela vira "o mais rápido possível".

## Armadilhas cobertas de propósito

**1. O pool default tem UMA thread.** Duas rotinas agendadas para o mesmo instante não rodam juntas: a segunda espera a primeira, e uma rotina lenta atrasa todas atrás dela. O sintoma é um job que "roda atrasado" sem motivo aparente, e nada no código sugere isso. Por isso existe um `ThreadPoolTaskScheduler` explícito. Consequência: declarar esse bean faz `spring.task.scheduling.*` deixar de valer, então pool e shutdown são configurados no próprio bean.

**2. `@Scheduled` não protege contra réplicas.** Ele garante que a rotina não se sobreponha **dentro de uma JVM**. Com três réplicas, as três disparam o mesmo cron no mesmo segundo e o juro é cobrado três vezes. O lock vive numa linha de tabela, que é a única coisa que as réplicas compartilham.

Verificado de verdade — 4 chamadas simultâneas, 1 execução registrada:

```
=== 4 disparos simultaneos ===
=== execucoes registradas DEPOIS: 3 (antes: 2) ===
  id=5 status=SUCCESS processados=16000 (2626ms)
```

**3. `usingDbTime()`.** O lock é cronometrado pelo relógio **do banco**, não de cada aplicação. Réplicas derivam, e poucos segundos de deriva bastam para duas delas acharem que o lock expirou.

**4. Nunca apague linhas da tabela `shedlock` com a aplicação no ar.** O `JdbcTemplateLockProvider` mantém um registro em memória dos nomes de lock que já inseriu, para que as próximas aquisições precisem só de um `UPDATE`. Apagar a linha por baixo dele deixa esse registro obsoleto: o `UPDATE` não casa com nada, o `INSERT` é pulado, e **toda** aquisição seguinte é reportada como "já travado" — a rotina simplesmente para de rodar, sem erro nenhum. Para liberar um lock preso, expire-o:

```sql
UPDATE shedlock SET lock_until = now() WHERE name = 'reconciliation';
```

Esse foi um bug real encontrado durante o desenvolvimento deste projeto: os testes faziam `DELETE`, o primeiro rodava e todos os seguintes eram ignorados em silêncio.

**5. A auditoria precisa sobreviver à falha que ela relata.** Se o `JobExecutionRecorder` dividisse a transação da rotina, o rollback causado pela exceção levaria junto o registro `FAILED` — e a única evidência de que a rotina noturna está quebrada há três semanas seria o estrago que ela deixou de evitar. Todo método do recorder é `REQUIRES_NEW`, e há teste cobrindo exatamente isso.

**6. Idempotência.** Cada rotina tem um marcador que a torna segura para reexecutar:

| Rotina | Marcador | Resultado da 2ª execução |
|---|---|---|
| `reconciliation` | `transactions.reconciled_at` | 16.000 → **0 processados** |
| `interest-accrual` | `invoices.interest_applied_on` | 851 → **0 processados**, total de juros intacto em R$ 127,74 |

**7. Chunks.** 16.000 transações em 2,8 s, em blocos de 500 com um commit por bloco. Carregar o backlog inteiro numa transação só mantém locks abertos por minutos e faz uma queda perder tudo em vez de um bloco. O `ReconciliationChunkProcessor` fica em bean próprio de propósito: chamar um método `@Transactional` de dentro da mesma classe ignora o proxy e a anotação não faz nada — a transação por chunk simplesmente não existiria.

**8. Fuso horário explícito.** Um cron sem zona usa o default da JVM, que em container é quase sempre UTC: o "job das 3h" roda à meia-noite e ninguém percebe até um relatório sair no dia errado. Com `America/Sao_Paulo` configurado, `GET /jobs` mostra `"nextExecution": "2026-09-24T06:00:00Z"` para o cron `0 0 3 * * *` — 03:00 local. O mesmo vale para `LocalDate.now()` dentro da rotina de juros.

**9. Graceful shutdown.** Sem `setWaitForTasksToCompleteOnShutdown` e `awaitTerminationSeconds`, um SIGTERM mata o lote pela metade.

**10. Exceção não pode cancelar o agendamento.** O `ErrorHandler` do scheduler trata a falha e mantém o gatilho vivo; a exceção continua propagando da rotina para que o handler (e qualquer alerta ligado a ele) enxergue o problema.

**11. Cron validado antes de gravar.** Uma expressão quebrada persistida derruba a rotina no próximo restart, muito depois de quem digitou ter ido embora. A validação acontece antes da escrita, e o teste verifica que a expressão anterior fica intacta.

**12. Mensagem de validação não pode depender do locale da máquina.** As mensagens padrão do Bean Validation vêm de um resource bundle resolvido pelo locale da JVM — a mesma API responde `"must not be blank"` numa máquina e `"não deve estar em branco"` em outra. As mensagens estão escritas explicitamente nos DTOs, e há teste cobrindo.

## Agendamento dinâmico

Um `@Scheduled("0 0 3 * * *")` é constante de compilação: mudar o horário exige código, build e deploy. Aqui o cron mora em `scheduled_job_config`, e o `DynamicJobScheduler` guarda o `ScheduledFuture` de cada tarefa para cancelar e re-registrar com um gatilho novo — sem restart:

```bash
curl -s -X PUT localhost:8080/jobs/interest-accrual/schedule \
  -H "Content-Type: application/json" -d '{"cronExpression":"0 30 4 * * *"}'
# {"cronExpression":"0 30 4 * * *","enabled":true,"scheduled":true,"nextExecution":"2026-09-24T07:30:00Z"}
```

As method references no `JobRegistry` apontam para os **beans injetados**, que são proxies. Esse detalhe é o que mantém o `@SchedulerLock` funcionando: o ShedLock aplica o lock via advice no proxy, então invocar o objeto alvo direto rodaria a rotina sem lock nenhum — a mesma armadilha de chamar um método `@Transactional` em `this`.

## Endpoints

| Método | Rota | Descrição |
|--------|------|-----------|
| GET | `/jobs` | Lista rotinas com cron, estado e próxima execução |
| PUT | `/jobs/{name}/schedule` | Altera o cron em runtime (reagenda na hora) |
| PUT | `/jobs/{name}/enabled` | Liga/desliga a rotina |
| POST | `/jobs/{name}/trigger` | Dispara manualmente — responde **202**, o trabalho é assíncrono |
| GET | `/jobs/{name}/executions` | Histórico auditado (`limit` de 1 a 200) |
| GET | `/jobs/rate-vs-delay-demo` | Os intervalos medidos de fixedRate e fixedDelay |

O trigger responde 202 e não o resultado: a rotina é entregue ao pool do scheduler, e se outra instância já estiver com o lock o ShedLock ignora a execução em silêncio. A resposta para "o que aconteceu?" é sempre o log de execuções.

## Como rodar

```bash
docker compose up -d
```

```bash
./gradlew bootRun
```

A primeira subida roda as migrations e semeia 200 contas, 20.000 transações e 1.000 faturas. A API responde em `http://localhost:8080`.

## Como rodar os testes

```bash
./gradlew test
```

21 testes: 3 unitários (cálculo e arredondamento de juros) e 18 de integração contra um PostgreSQL real via Testcontainers — incluindo o teste de concorrência do ShedLock, o de auditoria sobrevivendo ao rollback e o que mede fixedRate contra fixedDelay.

Os testes rodam com `app.scheduling.bootstrap-enabled: false`. Com os crons ativos, a reconciliação dispararia sozinha a cada cinco minutos e alteraria os dados sob asserção — uma flake que só aparece quando a suíte calha de cruzar um múltiplo de cinco minutos.

### Nota sobre Testcontainers e Docker Engine recente

Se os testes falharem com `client version 1.32 is too old. Minimum supported API version is 1.40`, a causa é o `docker-java` embutido no Testcontainers negociar a API 1.32, abaixo do mínimo aceito pelo Docker Engine 29+. Correção global, de uma linha:

```bash
echo 'api.version=1.44' > ~/.docker-java.properties
```

### Nota sobre o container nos testes

`IntegrationTestBase` usa o padrão **singleton container** — iniciado num bloco `static` e nunca entregue à extensão `@Testcontainers` do JUnit. Aquela extensão amarra o ciclo de vida do container à **classe de teste**, parando-o ao fim da classe e subindo um novo, em outra porta, para a classe seguinte, enquanto o Spring cacheia o contexto entre classes — da segunda em diante o pool apontaria para um container destruído.

## Exemplo de uso

```bash
# Listar rotinas, cron e próxima execução
curl -s localhost:8080/jobs

# Disparar a reconciliação e acompanhar
curl -s -X POST localhost:8080/jobs/reconciliation/trigger
curl -s "localhost:8080/jobs/reconciliation/executions?limit=5"

# Reagendar sem restart
curl -s -X PUT localhost:8080/jobs/interest-accrual/schedule \
  -H "Content-Type: application/json" -d '{"cronExpression":"0 30 4 * * *"}'

# Desligar uma rotina
curl -s -X PUT localhost:8080/jobs/reconciliation/enabled \
  -H "Content-Type: application/json" -d '{"enabled":false}'

# fixedRate x fixedDelay medidos
curl -s localhost:8080/jobs/rate-vs-delay-demo

# Erros esperados
curl -s -X PUT localhost:8080/jobs/reconciliation/schedule \
  -H "Content-Type: application/json" -d '{"cronExpression":"nao eh cron"}'   # 400
curl -s -X POST localhost:8080/jobs/nao-existe/trigger                          # 404
```

Para ver o lock em ação, com a aplicação no ar:

```bash
for i in 1 2 3 4; do curl -s -o /dev/null -X POST localhost:8080/jobs/reconciliation/trigger & done; wait
curl -s "localhost:8080/jobs/reconciliation/executions?limit=5"
```

Quatro disparos, uma execução.
