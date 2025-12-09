package br.com.danielbgg.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CsvGenerator {

    public static void main(String[] args) throws IOException {
        // Caminho do arquivo e quantidade de registros podem vir por argumento
        String outputPath = args.length > 0 ? args[0] : "input/payments-big.csv";
        long records = args.length > 1 ? Long.parseLong(args[1]) : 10_000_000L; // 10M linhas ≈ perto de 1GB

        Path path = Paths.get(outputPath);
        Files.createDirectories(path.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            // Header
            writer.write("externalId;payerId;payeeId;amount;currency;paymentDate");
            writer.newLine();

            for (long i = 1; i <= records; i++) {
                String externalId = "P" + i;
                String payerId = "C" + String.format("%06d", i % 1_000_000);
                String payeeId = "L" + String.format("%06d", i % 1_000_000);
                double amount = 100.0 + (i % 1000) + ((i % 97) / 100.0);
                int day = (int) ((i % 28) + 1); // 1..28
                String date = "2025-01-" + String.format("%02d", day);

                String line = String.format("%s;%s;%s;%.2f;BRL;%s",
                        externalId, payerId, payeeId, amount, date);

                writer.write(line);
                writer.newLine();

                if (i % 1_000_000 == 0) {
                    System.out.println("Geradas " + i + " linhas...");
                }
            }
        }

        System.out.println("Arquivo gerado em: " + outputPath);
    }
}