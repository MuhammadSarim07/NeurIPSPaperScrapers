package com.sarim.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.util.concurrent.*;

public class NeurIPSScraper {
    private static final String BASE_URL = "https://papers.nips.cc";
    private static final String STORAGE_DIRECTORY = "/Users/muhammadsarim/Desktop/NeurIPS_Papers-Java/";

    public static void main(String[] args) {
        try {
            prepareStorageDirectory();
            ExecutorService executorService = initializeExecutorService();
            CompletionService<Void> taskCompletionService = new ExecutorCompletionService<>(executorService);

            try (BufferedWriter csvWriter = initializeCSVWriter()) {
                for (int year = 2023; year >= 2019; year--) {
                    fetchAndProcessPapersForYear(year, csvWriter, taskCompletionService);
                }
            } catch (IOException e) {
                System.err.println("Error initializing the CSV writer.");
                e.printStackTrace();
            }

            shutDownExecutorService(executorService);
        } catch (IOException e) {
            System.err.println("Failed to create the storage directory.");
            e.printStackTrace();
        }
    }

    private static void prepareStorageDirectory() throws IOException {
        Files.createDirectories(Paths.get(STORAGE_DIRECTORY));
    }

    private static ExecutorService initializeExecutorService() {
        return Executors.newFixedThreadPool(7);
    }

    private static BufferedWriter initializeCSVWriter() throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(STORAGE_DIRECTORY + "papers_output.csv"));
        writer.write("Year,Title,Authors,Paper URL,PDF URL\n");
        return writer;
    }

    private static void fetchAndProcessPapersForYear(int year, BufferedWriter writer, CompletionService<Void> completionService) {
        String yearPageUrl = BASE_URL + "/paper_files/paper/" + year;
        String yearFolder = STORAGE_DIRECTORY + year + "/";

        try {
            Files.createDirectories(Paths.get(yearFolder));
            Document yearPage = fetchDocumentWithDelay(yearPageUrl);

            Elements paperLinks = yearPage.select("ul.paper-list li.conference a");
            if (paperLinks.isEmpty()) {
                System.err.println("No papers found for year: " + year);
                return;
            }

            for (Element paperLink : paperLinks) {
                String paperTitle = paperLink.text().trim();
                String paperPageUrl = BASE_URL + paperLink.attr("href");
                completionService.submit(new PaperProcessingTask(writer, paperTitle, paperPageUrl, yearFolder, year));
            }
        } catch (IOException e) {
            System.err.println("Error fetching papers for year: " + year);
            e.printStackTrace();
        }
    }

    private static Document fetchDocumentWithDelay(String url) {
        try {
            Thread.sleep(1000 + new Random().nextInt(2000));
            return Jsoup.connect(url).get();
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to fetch document: " + url);
            e.printStackTrace();
            return null;
        }
    }

    private static void shutDownExecutorService(ExecutorService executorService) {
        try {
            executorService.shutdown();
            executorService.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            System.err.println("Executor service interrupted.");
            e.printStackTrace();
        }
    }

    static class PaperProcessingTask implements Callable<Void> {
        private final BufferedWriter csvWriter;
        private final String paperTitle;
        private final String paperPageUrl;
        private final String yearFolder;
        private final int year;

        public PaperProcessingTask(BufferedWriter writer, String paperTitle, String paperPageUrl, String yearFolder, int year) {
            this.csvWriter = writer;
            this.paperTitle = paperTitle;
            this.paperPageUrl = paperPageUrl;
            this.yearFolder = yearFolder;
            this.year = year;
        }

        @Override
        public Void call() throws InterruptedException {
            try {
                System.out.println("Processing paper: " + paperTitle);
                Document paperDocument = Jsoup.connect(paperPageUrl).get();

                String authors = extractAuthors(paperDocument);
                String pdfUrl = extractPdfUrl(paperDocument);

                if (!"N/A".equals(pdfUrl)) {
                    downloadPdf(pdfUrl, yearFolder, paperTitle);
                }

                savePaperDetailsToCSV(authors, pdfUrl);
            } catch (IOException e) {
                System.err.println("Error processing paper: " + paperTitle);
                e.printStackTrace();
            }

            return null;
        }

        private String extractAuthors(Document document) {
            Elements authorElements = document.select("i");
            StringBuilder authors = new StringBuilder();
            for (Element author : authorElements) {
                authors.append(author.text()).append("; ");
            }
            return authors.toString().trim();
        }

        private String extractPdfUrl(Document document) {
            Element pdfElement = document.selectFirst("a[href$=.pdf]");
            return (pdfElement != null) ? BASE_URL + pdfElement.attr("href") : "N/A";
        }

        private void downloadPdf(String pdfUrl, String savePath, String title) throws InterruptedException {
            String sanitizedTitle = title.replaceAll("[^a-zA-Z0-9]", "_") + ".pdf";
            String filePath = savePath + sanitizedTitle;
            System.out.println("Downloading PDF: " + pdfUrl);
            try {
                downloadFile(pdfUrl, filePath);
            } catch (IOException e) {
                System.err.println("Failed to download PDF: " + pdfUrl);
                e.printStackTrace();
            }
        }

        private void downloadFile(String url, String savePath) throws IOException {
            try (InputStream in = new URL(url).openStream(); FileOutputStream out = new FileOutputStream(savePath)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                System.out.println("Downloaded: " + savePath);
            }
        }

        private void savePaperDetailsToCSV(String authors, String pdfUrl) throws IOException {
            synchronized (csvWriter) {
                csvWriter.write(String.format("%d,\"%s\",\"%s\",\"%s\",\"%s\"\n", year, paperTitle, authors, paperPageUrl, pdfUrl));
                csvWriter.flush();
            }
            System.out.println("Processed paper: " + paperTitle);
        }
    }
}
