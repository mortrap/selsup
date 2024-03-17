package ru.jobtest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.json.JSONObject;

public class CrptApiV2 {
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final ConcurrentHashMap<String, JSONObject> requestsInProgress = new ConcurrentHashMap<>();
    private final Lock lock = new ReentrantLock();

    public CrptApiV2(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
    }

    public void createDocument(JSONObject document, String signature) throws IOException, InterruptedException {
        if (lock.tryLock()) {
            try {
                Instant currentTime = Instant.now();
                String key = currentTime.toString();

                if (requestsInProgress.size() >= requestLimit) {
                    waitForRequestToComplete(key);
                }

                requestsInProgress.putIfAbsent(key, new JSONObject(document.toString()));

                HttpClient client = HttpClient.newBuilder().build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(document.toString()))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new IOException("API returned error: " + response.statusCode());
                }

                requestsInProgress.remove(key);

                // Process the response here, if needed
                System.out.println("Response from API: " + response.body());
            } finally {
                lock.unlock();
            }
        } else {
            throw new InterruptedException("Cannot acquire lock to make a request.");
        }
    }

    private void waitForRequestToComplete(String key) throws InterruptedException {
        while (requestsInProgress.containsKey(key)) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        CrptApiV2 crptApi = new CrptApiV2(TimeUnit.MINUTES, 5); // Create a new instance with a request limit of 5 requests per minute

        JSONObject jsonDocument = new JSONObject("{\"description\":{\"participantInn\": \"string\"}," +
                "\"doc_id\":\"string\",\"doc_status\":\"string\",\"doc_type\":\"LP_INTRODUCE_GOODS, 109\"," +
                "\"importRequest\": true,\"owner_inn\": \"string\",\"participant_inn\": \"string\",\"producer_inn\": \"string\",\"production_date\": \"2020-01-23\",\"production_type\": \"string\"," +
                "\"products\":[{\"certificate_document\": \"string\",\"certificate_document_date\": \"2020-01-23\",\"certificate_document_number\": \"string\",\"owner_inn\": \"string\",\"producer_inn\": \"string\",\"production_date\": \"2020-01-23\",\"tnved_code\": \"string\",\"uit_code\": \"string\",\"uitu_code\": \"string\"}],\"reg_date\": \"2020-01-23\",\"reg_number\": \"string\"}");

        String signature = "signature"; // Replace with a valid signature

        List<JSONObject> productsList = new ArrayList<>();

        for (int i = 0; i < 6; i++) { // Test creating 6 documents within the limit
            crptApi.createDocument(jsonDocument, signature);
            System.out.println("Created document #" + (i+1));
        }

        jsonDocument.put("doc_id", "documentId2"); // Update document ID for testing

        try {
            crptApi.createDocument(jsonDocument, signature); // Test exceeding the limit
            System.out.println("Exceeded request limit but no exception was thrown!");
        } catch (InterruptedException e) {
            System.out.println("Expected InterruptedException: " + e.getMessage());
        }

        jsonDocument.put("doc_id", "documentId3"); // Update document ID for testing

        try {
            Thread.sleep(TimeUnit.MINUTES.toMillis(1)); // Wait for 1 minute to let the rate limit reset
        } catch (InterruptedException e) {
            throw new RuntimeException("Thread interrupted during sleep", e);
        }

        crptApi.createDocument(jsonDocument, signature); // Test creating a document after the limit has reset
        System.out.println("Created document #7");
    }
}