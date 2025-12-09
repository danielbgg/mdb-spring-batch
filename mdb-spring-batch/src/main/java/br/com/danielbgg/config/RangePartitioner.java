package br.com.danielbgg.config;

// CERTO
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class RangePartitioner implements Partitioner {

    private final Resource file;

    public RangePartitioner(Resource file) {
        this.file = file;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        try {
            long totalLines = countDataLines(); // sem header
            long targetSize = (long) Math.ceil((double) totalLines / gridSize);

            Map<String, ExecutionContext> result = new HashMap<>();

            long start = 0;
            long end;
            int partitionNumber = 0;

            while (start < totalLines) {
                end = Math.min(start + targetSize, totalLines);

                ExecutionContext context = new ExecutionContext();
                context.putString("fileName", file.getFile().getAbsolutePath());
                context.putLong("start", start);
                context.putLong("end", end);

                result.put("partition-" + partitionNumber, context);

                start = end;
                partitionNumber++;
            }

            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Erro ao particionar arquivo", e);
        }
    }

    private long countDataLines() throws Exception {
        long lines = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            // primeira linha é header
            String line = br.readLine(); // header
            if (line == null) {
                return 0;
            }
            while ((line = br.readLine()) != null) {
                lines++;
            }
        }
        return lines;
    }
}