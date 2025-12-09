package br.com.danielbgg.config;

import br.com.danielbgg.model.Payment;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.PartitionHandler;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.data.MongoItemWriter;
import org.springframework.batch.item.data.builder.MongoItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;

@Configuration
public class BatchConfig {

    private static final int GRID_SIZE = 4; // número de partições/threads

    // ----------------------------
    // Reader step-scoped (por faixa)
    // ----------------------------
    @Bean
    @StepScope
    public FlatFileItemReader<Payment> paymentItemReader(
            @Value("#{stepExecutionContext['fileName']}") String fileName,
            @Value("#{stepExecutionContext['start']}") Long start,
            @Value("#{stepExecutionContext['end']}") Long end
    ) {

        FileSystemResource resource = new FileSystemResource(fileName);

        FlatFileItemReader<Payment> reader = new FlatFileItemReaderBuilder<Payment>()
                .name("paymentItemReader")
                .resource(resource)
                .linesToSkip(1) // header
                .delimited()
                .delimiter(";")
                .names("externalId", "payerId", "payeeId", "amount", "currency", "paymentDate")
                .fieldSetMapper(fieldSet -> {
                    Payment p = new Payment();
                    p.setExternalId(fieldSet.readString("externalId"));
                    p.setPayerId(fieldSet.readString("payerId"));
                    p.setPayeeId(fieldSet.readString("payeeId"));
                    BigDecimal amount = fieldSet.readBigDecimal("amount");
                    p.setAmount(amount);
                    p.setCurrency(fieldSet.readString("currency"));

                    String dateStr = fieldSet.readString("paymentDate");
                    LocalDate date = LocalDate.parse(dateStr); // yyyy-MM-dd
                    p.setPaymentDate(date);

                    p.setStatus("RECEIVED");
                    p.setReconciliationStatus("PENDING");
                    return p;
                })
                .build();

        // start/end são índices 0-based nas linhas de dados (sem header)
        reader.setCurrentItemCount(start.intValue());
        reader.setMaxItemCount(end.intValue());

        return reader;
    }

    // ----------------------------
    // Processor com log opcional
    // ----------------------------
    @Bean
    public ItemProcessor<Payment, Payment> paymentItemProcessor() {
        return payment -> {

            // Log de progresso: 1 a cada 1.000.000 registros
            if (payment.getExternalId() != null) {
                try {
                    long idNum = Long.parseLong(payment.getExternalId().substring(1)); // P123 -> 123
                    if (idNum % 1_000_000 == 0) {
                        String threadName = Thread.currentThread().getName();
                        org.slf4j.LoggerFactory.getLogger("ProcessorLogger")
                                .info("Processando externalId={} na thread={}",
                                        payment.getExternalId(), threadName);
                    }
                } catch (Exception ignore) {
                }
            }

            if (payment.getAmount() == null || payment.getAmount().signum() <= 0) {
                payment.setReconciliationStatus("INVALID");
            } else {
                payment.setReconciliationStatus("RECONCILED");
            }
            payment.setStatus("PROCESSED");
            return payment;
        };
    }

    // ----------------------------
    // Writer para MongoDB
    // ----------------------------
    @Bean
    public MongoItemWriter<Payment> paymentItemWriter(MongoTemplate mongoTemplate) {
        return new MongoItemWriterBuilder<Payment>()
                .template(mongoTemplate)
                .collection("payments")
                .build();
    }

    // ----------------------------
    // Partitioner (um arquivo grande em ranges)
    // ----------------------------
    @Bean
    public Partitioner rangePartitioner(
            @Value("file:input/payments-big.csv") Resource file
    ) {
        return new RangePartitioner(file);
    }

    // ----------------------------
    // TaskExecutor (threads paralelas)
    // ----------------------------
    @Bean
    public TaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("payment-range-");
        executor.setConcurrencyLimit(GRID_SIZE);
        return executor;
    }

    @Bean
    public PartitionHandler partitionHandler(TaskExecutor taskExecutor, Step slaveStep) {
        TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
        handler.setTaskExecutor(taskExecutor);
        handler.setStep(slaveStep);
        handler.setGridSize(GRID_SIZE);
        return handler;
    }

    // ----------------------------
    // Listener de logging por step
    // ----------------------------
    @Bean
    public StepExecutionListener loggingStepExecutionListener() {
        return new LoggingStepExecutionListener();
    }

    // ----------------------------
    // Slave Step (processa um range do arquivo)
    // ----------------------------
    @Bean
    public Step slaveStep(JobRepository jobRepository,
                          PlatformTransactionManager transactionManager,
                          FlatFileItemReader<Payment> reader,
                          ItemProcessor<Payment, Payment> processor,
                          MongoItemWriter<Payment> writer,
                          StepExecutionListener loggingStepExecutionListener) {

        return new StepBuilder("slaveStep", jobRepository)
                .<Payment, Payment>chunk(5_000, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .listener(loggingStepExecutionListener)
                .build();
    }

    // ----------------------------
    // Master Step (partitioning)
    // ----------------------------
    @Bean
    public Step masterStep(JobRepository jobRepository,
                           Partitioner rangePartitioner,
                           PartitionHandler partitionHandler) {

        return new StepBuilder("masterStep", jobRepository)
                .partitioner("slaveStep", rangePartitioner)
                .partitionHandler(partitionHandler)
                .build();
    }

    // ----------------------------
    // Job
    // ----------------------------
    @Bean
    public Job paymentJob(JobRepository jobRepository, Step masterStep) {
        return new JobBuilder("paymentJob", jobRepository)
                .start(masterStep)
                .build();
    }
}