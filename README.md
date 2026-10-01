# Rotinas Financeiras Agendadas

Serviço com rotinas automáticas sobre uma base financeira já populada — 200 contas, 20 mil transações e 1.000 faturas: reconciliação de transações, aplicação de juros e limpeza de histórico.

O projeto cobre o agendamento de tarefas em Spring Boot além do `@Scheduled` básico: pool de threads dedicado, agendamento dinâmico que pode ser alterado em tempo de execução por endpoint, lock distribuído para a rotina não rodar duas vezes quando há mais de uma instância, processamento em blocos e auditoria de cada execução.

## Tecnologias e bibliotecas

| | |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.5, Spring Scheduling |
| Lock distribuído | ShedLock 6.6 (provider JDBC) |
| Persistência | Spring Data JPA, PostgreSQL 16 |
| Migrations | Flyway |
| Validação | Bean Validation |
| Build | Gradle Kotlin DSL (wrapper `gradlew`) |
| Testes | JUnit 5, Awaitility, Testcontainers |
| Apoio | Lombok |

## Pré-requisitos

- JDK 21 ou superior
- Docker

## Como rodar

```bash
docker compose up -d
```

```bash
./gradlew bootRun
```

A primeira subida roda as migrations e semeia a base. A API fica em `http://localhost:8080`.

## As rotinas

| Rotina | Agendamento padrão | O que faz |
|---|---|---|
| `reconciliation` | `0 */5 * * * *` | Reconcilia transações pendentes, em blocos |
| `interest-accrual` | `0 0 3 * * *` | Aplica juros diários sobre faturas em atraso |
| purge | `fixedDelay` de 1h | Limpa registros de execução com mais de 30 dias |

As duas primeiras são agendadas dinamicamente e podem ter o cron trocado por endpoint, sem reiniciar a aplicação. A terceira usa `@Scheduled` comum, de propósito, para comparação.

O fuso do agendamento é declarado explicitamente (`America/Sao_Paulo`), e cada execução é registrada com horário de início, duração, resultado e quantidade de itens processados — em transação própria, então o registro sobrevive mesmo quando a rotina falha e sofre rollback.

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/jobs` | Lista as rotinas, o agendamento atual e a próxima execução |
| `POST` | `/jobs/{jobName}/trigger` | Dispara a rotina na hora |
| `GET` | `/jobs/{jobName}/executions?limit=N` | Histórico de execuções |
| `PUT` | `/jobs/{jobName}/schedule` | Troca a expressão cron sem reiniciar |
| `PUT` | `/jobs/{jobName}/enabled` | Liga ou desliga a rotina |
| `GET` | `/jobs/rate-vs-delay-demo` | Compara `fixedRate` e `fixedDelay` em execução |

## Exemplos de uso

```bash
curl -s localhost:8080/jobs
```

```bash
curl -s -X POST localhost:8080/jobs/reconciliation/trigger
```

```bash
curl -s "localhost:8080/jobs/reconciliation/executions?limit=5"
```

```bash
curl -s -X PUT localhost:8080/jobs/interest-accrual/schedule \
  -H "Content-Type: application/json" -d '{"cronExpression":"0 30 4 * * *"}'
```

```bash
curl -s -X PUT localhost:8080/jobs/reconciliation/enabled \
  -H "Content-Type: application/json" -d '{"enabled":false}'
```

```bash
curl -s localhost:8080/jobs/rate-vs-delay-demo
```

Para ver o lock distribuído em ação, dispare a mesma rotina quatro vezes ao mesmo tempo e confira o histórico — só uma execução é registrada:

```bash
for i in 1 2 3 4; do curl -s -o /dev/null -X POST localhost:8080/jobs/reconciliation/trigger & done; wait
```

## Testes

```bash
./gradlew test
```

21 testes: 3 unitários, sobre cálculo e arredondamento de juros, e 18 de integração contra um PostgreSQL em container, incluindo concorrência do lock, auditoria após rollback e a diferença medida entre `fixedRate` e `fixedDelay`.
