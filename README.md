# mdb-spring-batch

# Spring Batch + MongoDB Atlas — POC de Processamento Paralelo de Arquivo Massivo

## 📌 Objetivo da POC

Esta Prova de Conceito demonstra como processar **um arquivo único de grande volume (≈1 GB)** utilizando:

- **Spring Batch 5**
- **Java 21+**
- **Partitioning com range-based splitting**
- **Processamento paralelo real (multithreading)**
- **Persistência em alta velocidade no MongoDB Atlas**

A POC mostra uma arquitetura robusta e escalável usada para cenários como:

- Conciliação de pagamentos
- Processamento de arquivos bancários
- ETL de grandes volumes
- Normalização e carga de dados massivos para sistemas downstream

O destaque está em processar **um único arquivo gigante**, dividindo-o logicamente em faixas (ranges) sem quebrar o arquivo físico.

---

## 🚀 Visão Geral da Arquitetura

```
                         +-------------------------+
                         |   payments-big.csv      |  (~1 GB)
                         +-----------+-------------+
                                     |
                         Divide em N ranges (partitioning)
                                     |
                 ------------------------------------------------
                 |                     |                        |
        partition-0             partition-1             partition-2   ...
        range=[0..2.5M]         range=[2.5M..5M]        range=[5M..7.5M]
                 |                     |                        |
      slaveStep (thread 1)   slaveStep (thread 2)    slaveStep (thread 3)
                 |                     |                        |
                 \__________________ MongoDB Atlas ______________/
```

Cada partição roda em sua própria thread e processa uma fatia do arquivo **sem necessidade de dividir o arquivo fisicamente**.

---

## 🧠 Conceitos Demonstrados

### ✔️ 1. Range-Based Partitioning

- O arquivo é dividido por **intervalos lógicos** baseados no número de linhas.
- Cada partição recebe:
  - `start`: índice inicial de leitura
  - `end`: índice final
  - `fileName`: caminho do arquivo
- Isso permite paralelismo real mesmo com um único arquivo massivo.

### ✔️ 2. Execução Paralela com Thread Pool Controlado

- O `SimpleAsyncTaskExecutor` executa as partições simultaneamente.
- Threads nomeadas (`payment-range-0`, `payment-range-1`, …) facilitam monitoramento.

### ✔️ 3. Persistência de Alta Performance no MongoDB Atlas

- Cada registro processado é escrito na collection `payments`.
- O Atlas lida automaticamente com paralelismo, throughput, escalabilidade e controle de conexões.

### ✔️ 4. Observabilidade

A POC possui logs detalhados, incluindo:

- Qual thread processa qual partição
- Quantos registros cada partição leu/escreveu
- Progresso periódico no `ItemProcessor`
- Range de linhas processadas

Além disso, o Atlas permite visualizar métricas reais:

- Write throughput (OPS)
- CPU / memória do cluster
- Conexões simultâneas
- Sugestões automáticas do Performance Advisor

---

## 📂 Estrutura do Projeto

```
src/
 └── main/java/br/com/danielbgg/
      ├── SpringBatchMongoPocApplication.java
      ├── config/
      │     ├── BatchConfig.java
      │     ├── RangePartitioner.java
      │     └── LoggingStepExecutionListener.java
      ├── model/
      │     └── Payment.java
      └── util/
            └── CsvGenerator.java
```

---

## 📄 Geração do Arquivo Massivo (~1 GB)

A POC inclui um utilitário para criar arquivos grandes para teste:

```bash
mvn compile
java -cp target/classes br.com.danielbgg.util.CsvGenerator input/payments-big.csv 10000000
```

- Gera `payments-big.csv`
- 10 milhões de linhas (ajustável)
- Aproximadamente 1 GB

---

## ⚙️ Como Executar o Job

### 1. Compile o projeto

```bash
mvn clean package
```

### 2. Execute o Spring Boot

```bash
mvn spring-boot:run
```

Durante a execução, o Spring Batch irá:

1. Ler `payments-big.csv`
2. Dividir automaticamente em *N* ranges
3. Criar partições como:
   - `partition-0` → linhas 0 a 2.5M
   - `partition-1` → linhas 2.5M a 5M
4. Executar cada partição em paralelo
5. Gravar no MongoDB Atlas

---

## 📊 Logs de Monitoramento

### Durante o processamento:

```
INFO [payment-range-1] >>> [BEFORE STEP] partition-1 range=[2500000 - 5000000]
INFO [payment-range-1] Processando externalId=P3000000 na thread=payment-range-1
```

### Ao finalizar uma partição:

```
INFO [payment-range-3] <<< [AFTER STEP] partition-3 range=[7500000 - 10000000]
     readCount=2500000 writeCount=2500000 skipCount=0
```

---

## 📊 Monitoramento no MongoDB Atlas

No Atlas é possível observar em tempo real:

- Throughput de escrita (OPS)
- Conexões simultâneas
- Latência de operações
- Recomendações automáticas de índices

Consulta básica:

```js
use paymentsdb
db.payments.countDocuments()
db.payments.find().limit(5)
```

---

## 🧪 Escalabilidade Demonstrada

A POC evidencia:

- Processamento paralelo real com aumento linear de throughput.
- MongoDB Atlas absorvendo carga de escrita sem gargalos.
- Flexibilidade para aumentar o GRID_SIZE (número de partições).
- Fácil adaptação para arquivos ainda maiores (5 GB, 10 GB, etc).

---

## 🔧 Configurações Ajustáveis

### Número de partições / threads:

```java
private static final int GRID_SIZE = 4;
```

Exemplos:

- 8 → clusters maiores
- 16 → alta capacidade de I/O
- 32+ → benchmarks agressivos

### Tamanho dos chunks:

```java
.chunk(5000, transactionManager)
```

---

## 📍 Principais Benefícios para o Cliente

- **Escalabilidade horizontal**
- **Processamento paralelo real**
- **Integração simples e eficiente com o MongoDB Atlas**
- **Observabilidade clara**
- **Arquitetura moderna com Spring Boot + Batch 5**

---

## 📦 Próximos Passos Possíveis

- Criar collection de erros (`payments_errors`)
- Adicionar validações avançadas no `ItemProcessor`
- Criar dashboards no Atlas Charts
- Expor estatísticas via Actuator
- Integrar com Kafka + Atlas Stream Processing

---

## 🏁 Conclusão

Esta POC demonstra como processar **arquivos extremamente grandes** com eficiência, paralelismo e robustez, aproveitando:

- Spring Batch para orquestração de workload
- Partitioning por ranges para paralelismo real
- MongoDB Atlas como datastore escalável

A arquitetura serve como base sólida para evoluções rumo a produção.

