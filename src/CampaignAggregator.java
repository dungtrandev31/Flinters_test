import java.io.*;
import java.lang.management.*;
import java.nio.file.*;
import java.util.*;

public class CampaignAggregator {

    static class CampaignMetrics {
        String campaignId;
        long totalImpressions = 0;
        long totalClicks = 0;
        double totalSpend = 0.0;
        long totalConversions = 0;

        public CampaignMetrics(String id) { this.campaignId = id; }
        public double getCTR() { return totalImpressions == 0 ? 0.0 : (double) totalClicks / totalImpressions; }
        public Double getCPA() { return totalConversions == 0 ? null : totalSpend / totalConversions; }
    }

    public static void main(String[] args) {
        String inputPath = "ad_data.csv";
        String outputPath = "results/";

        for (int i = 0; i < args.length; i++) {
            if ("--input".equals(args[i]) && i + 1 < args.length) inputPath = args[++i];
            else if ("--output".equals(args[i]) && i + 1 < args.length) outputPath = args[++i];
        }

        if (inputPath == null || outputPath == null) {
            System.out.println("Sử dụng: java CampaignAggregator --input ad_data.csv --output results/");
            return;
        }

        long startTime = System.currentTimeMillis();
        Map<String, CampaignMetrics> metricsMap = new HashMap<>();

        // Xử lý File
        long readStart = System.currentTimeMillis();
        try (BufferedReader br = new BufferedReader(new FileReader(inputPath))) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                try {
                    int p1 = line.indexOf(',');
                    int p2 = line.indexOf(',', p1 + 1);
                    int p3 = line.indexOf(',', p2 + 1);
                    int p4 = line.indexOf(',', p3 + 1);
                    int p5 = line.indexOf(',', p4 + 1);
                    if (p1 == -1 || p5 == -1) continue;

                    String id = line.substring(0, p1);
                    long imp = Long.parseLong(line.substring(p2 + 1, p3));
                    long clicks = Long.parseLong(line.substring(p3 + 1, p4));
                    double spend = Double.parseDouble(line.substring(p4 + 1, p5));
                    long conv = Long.parseLong(line.substring(p5 + 1));

                    CampaignMetrics m = metricsMap.computeIfAbsent(id, CampaignMetrics::new);
                    m.totalImpressions += imp;
                    m.totalClicks += clicks;
                    m.totalSpend += spend;
                    m.totalConversions += conv;
                } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            System.err.println("Lỗi: " + e.getMessage());
            return;
        }
        long readEnd = System.currentTimeMillis();

        // Xuất kết quả
        try {
            Files.createDirectories(Paths.get(outputPath));
            long processStart = System.currentTimeMillis();
            processAndWrite(metricsMap, outputPath);
            long processEnd = System.currentTimeMillis();

            // THỐNG KÊ HIỆU NĂNG
            long totalTime = System.currentTimeMillis() - startTime;

            System.out.println("\n" + "=".repeat(30));
            System.out.println("THỐNG KÊ HIỆU NĂNG");
            System.out.println("Processing Summary:");
            System.out.println("- Load & Aggregate: " + (readEnd - readStart) + " ms");
            System.out.println("- Sort & Write Files    : " + (processEnd - processStart) + " ms");
            System.out.println("- Total Time      : " + totalTime + " ms");
            System.out.println("- Peak Memory     : " + getPeakMemoryUsage() + " MB");

        } catch (IOException e) {
            System.err.println("Lỗi ghi file: " + e.getMessage());
        }
    }


    private static void processAndWrite(Map<String, CampaignMetrics> map, String outputDir) throws IOException {
        PriorityQueue<CampaignMetrics> topCtr = new PriorityQueue<>(Comparator.comparingDouble(CampaignMetrics::getCTR));
        PriorityQueue<CampaignMetrics> topCpa = new PriorityQueue<>((a, b) -> Double.compare(b.getCPA(), a.getCPA()));

        for (CampaignMetrics m : map.values()) {
            topCtr.offer(m);
            if (topCtr.size() > 10) topCtr.poll();
            if (m.totalConversions > 0) {
                topCpa.offer(m);
                if (topCpa.size() > 10) topCpa.poll();
            }
        }
        writeCsv(topCtr, Paths.get(outputDir, "top10_ctr.csv").toString());
        writeCsv(topCpa, Paths.get(outputDir, "top10_cpa.csv").toString());

    }

    private static void writeCsv(PriorityQueue<CampaignMetrics> queue, String path) throws IOException {
        List<CampaignMetrics> list = new ArrayList<>();
        while (!queue.isEmpty()) list.add(queue.poll());
        Collections.reverse(list);
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("campaign_id,total_impressions,total_clicks,total_spend,total_conversions,CTR,CPA");
            for (CampaignMetrics m : list) {
                pw.printf(Locale.US, "%s,%d,%d,%.2f,%d,%.4f,%.2f%n",
                        m.campaignId, m.totalImpressions, m.totalClicks, m.totalSpend, m.totalConversions, m.getCTR(), m.getCPA());
            }
        }
    }

    // Hàm lấy Peak Memory Usage từ tất cả các vùng nhớ (Heap + Non-Heap)
    private static long getPeakMemoryUsage() {
        long peak = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            peak += pool.getPeakUsage().getUsed();
        }
        return peak / (1024 * 1024); // Chuyển sang MB
    }
}