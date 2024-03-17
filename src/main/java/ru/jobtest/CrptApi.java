package ru.jobtest;
import java.net.http.HttpClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

public class CrptApi {
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private static final int DEFAULT_REQUEST_LIMIT = 10;
    private static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.MINUTES;

    private final HttpClient httpClient;
    private final ConcurrentHashMap<String, Instant> lastRequestMap;
    private final int requestLimit;
    private final TimeUnit timeUnit;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit != null ? timeUnit : DEFAULT_TIME_UNIT;
        this.requestLimit = requestLimit > 0 ? requestLimit : DEFAULT_REQUEST_LIMIT;
        this.lastRequestMap = new ConcurrentHashMap<>();
        this.httpClient = HttpClient.newHttpClient();
    }

    public void createDocument(Object document, String signature) throws IOException, InterruptedException {
        if (isRateLimited()) {
            throw new InterruptedException("Exceeded the maximum number of requests in the given time interval.");
        }

        Instant currentTime = Instant.now();
        lastRequestMap.put(currentTime.toString(), currentTime);

        JSONObject jsonDocument = new JSONObject(document);

            System.out.println(jsonDocument.keySet());


//        List<JSONObject> productsList = new ArrayList<>();
//
//        JSONArray jsonProducts = (JSONArray) jsonDocument.get("mapType");
//        for (int i = 0; i < jsonProducts.length(); i++) {
//            JSONObject productJson = jsonProducts.getJSONObject(i);
//            productsList.add(productJson);
//        }

        jsonDocument.put("importRequest", true);
        jsonDocument.put("owner_inn", "your_inn"); // Replace with your INN
        jsonDocument.put("participant_inn", "your_inn"); // Replace with your INN
        jsonDocument.put("producer_inn", "producer_inn"); // Replace with producer's INN

        String requestBody = jsonDocument.toString();
        System.out.println(requestBody);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response);
        if (response.statusCode() != 200) {
            throw new IOException("Failed to create document with status code: " + response.statusCode());
        }
    }

    private boolean isRateLimited() {
        long currentInterval = timeUnit.toMillis(1);
        Instant currentTime = Instant.now();

        for (Instant lastRequest : lastRequestMap.values()) {
            if (currentTime.isAfter(lastRequest.plus(Duration.ofMillis(currentInterval)))) {
                continue;
            }

            long elapsedTime = ChronoUnit.MILLIS.between(lastRequest, currentTime);
            if (elapsedTime < timeUnit.toMillis(1)) {
                return true;
            }
        }

        int requestsInInterval = lastRequestMap.size();
        return requestsInInterval >= requestLimit;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 5); // Create a new instance with a request limit of 5 requests per minute

        JSONObject jsonDocument = new JSONObject("{\"description\":{\"participantInn\": \"1234567890\"},\"doc_id\": \"documentId\",\"doc_status\": \"DRAFT\",\"doc_type\": \"LP_INTRODUCE_GOODS\",\"importRequest\": true,\"owner_inn\": \"1234567890\",\"participant_inn\": \"1234567890\",\"producer_inn\": \"1111111111\",\"production_date\": \"2020-01-23\",\"production_type\": \"string\",\"products\": [{\"certificate_document\": \"certificateDocument\",\"certificate_document_date\": \"2020-01-23\",\"certificate_document_number\": \"certificateNumber\",\"owner_inn\": \"1234567890\",\"producer_inn\": \"1111111111\",\"production_date\": \"2020-01-23\",\"tnved_code\": \"string\",\"uit_code\": \"string\",\"uitu_code\": \"string\"}],\"reg_date\": \"2020-01-23\",\"reg_number\": \"string\"}");

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