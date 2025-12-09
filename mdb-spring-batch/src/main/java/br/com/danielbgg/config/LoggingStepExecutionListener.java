package br.com.danielbgg.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ExecutionContext;

public class LoggingStepExecutionListener implements StepExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(LoggingStepExecutionListener.class);

    @Override
    public void beforeStep(StepExecution stepExecution) {
        String threadName = Thread.currentThread().getName();
        ExecutionContext ctx = stepExecution.getExecutionContext();

        Long start = ctx.containsKey("start") ? ctx.getLong("start") : null;
        Long end   = ctx.containsKey("end") ? ctx.getLong("end") : null;
        String fileName = ctx.containsKey("fileName") ? ctx.getString("fileName") : "N/A";

        logger.info(">>> [BEFORE STEP] step='{}', thread='{}', file='{}', range=[{} - {}]",
                stepExecution.getStepName(), threadName, fileName, start, end);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        String threadName = Thread.currentThread().getName();
        ExecutionContext ctx = stepExecution.getExecutionContext();

        Long start = ctx.containsKey("start") ? ctx.getLong("start") : null;
        Long end   = ctx.containsKey("end") ? ctx.getLong("end") : null;

        logger.info("<<< [AFTER STEP] step='{}', thread='{}', range=[{} - {}], " +
                        "readCount={}, writeCount={}, skipCount={}",
                stepExecution.getStepName(),
                threadName,
                start,
                end,
                stepExecution.getReadCount(),
                stepExecution.getWriteCount(),
                stepExecution.getSkipCount()
        );

        return stepExecution.getExitStatus();
    }
}