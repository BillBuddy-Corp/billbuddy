package com.billbuddy.backend.features.billscanner.service;

import com.billbuddy.backend.exception.ReceiptScanFailedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

// Calls the current Gemini Interactions API (generateContent is now documented as legacy).
// Not wired in as the active @Service -- live-tested unreliable (amount/currency/items dropped
// from output across 8+ calls); kept in case a future Gemini release fixes it. See ClaudeReceiptScannerService.
public class GeminiReceiptScannerService implements ReceiptScannerService {

    private static final String EXTRACTION_PROMPT = """
            Look at this image. Determine whether it is a photo of a purchase receipt or invoice.
            If it is not, set isReceipt to false and leave the other fields empty.
            If it is, set isReceipt to true and extract: the merchant/store name, the total amount \
            paid, the currency as a 3-letter ISO code if identifiable (e.g. USD, INR, EUR), the \
            transaction date in ISO-8601 (YYYY-MM-DD) format if visible, and a list of individual \
            line items with their names and prices if legible. Only include fields you can \
            confidently read -- omit fields you cannot determine rather than guessing.
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiReceiptScannerService(
            ObjectMapper objectMapper,
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.model}") String model
    ) {
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public ScannedReceipt scan(byte[] imageBytes, String mimeType) {
        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/interactions")
                    .header("x-goog-api-key", apiKey)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(buildRequestBody(imageBytes, mimeType))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new ReceiptScanFailedException("Could not reach the receipt scanning service");
        }

        String extractedJson = extractModelOutputText(response);
        JsonNode extracted = parseExtractedJson(extractedJson);

        if (!extracted.path("isReceipt").asBoolean(false)) {
            throw new ReceiptScanFailedException("This doesn't look like a receipt");
        }

        return toScannedReceipt(extracted);
    }

    // ===================== REQUEST BUILDING =====================

    private String buildRequestBody(byte[] imageBytes, String mimeType) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);

        ArrayNode input = root.putArray("input");
        ObjectNode imageBlock = input.addObject();
        imageBlock.put("type", "image");
        imageBlock.put("mime_type", mimeType);
        imageBlock.put("data", Base64.getEncoder().encodeToString(imageBytes));

        ObjectNode textBlock = input.addObject();
        textBlock.put("type", "text");
        textBlock.put("text", EXTRACTION_PROMPT);

        ArrayNode responseFormat = root.putArray("response_format");
        ObjectNode formatEntry = responseFormat.addObject();
        formatEntry.put("type", "text");
        formatEntry.put("mime_type", "application/json");
        formatEntry.set("schema", buildResponseSchema());

        return root.toString();
    }

    private JsonNode buildResponseSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        properties.putObject("isReceipt").put("type", "boolean");
        properties.putObject("merchant").put("type", "string");
        properties.putObject("amount").put("type", "number");
        properties.putObject("currency").put("type", "string");
        ObjectNode transactionDate = properties.putObject("transactionDate");
        transactionDate.put("type", "string");
        transactionDate.put("description", "ISO-8601 date (YYYY-MM-DD) if visible on the receipt");
        ObjectNode otherDiscounts = properties.putObject("otherDiscounts");
        otherDiscounts.put("type", "array");
        otherDiscounts.putObject("items").put("type", "number");
        properties.putObject("voucherAmount").put("type", "number");

        ObjectNode items = properties.putObject("items");
        items.put("type", "array");
        ObjectNode itemSchema = items.putObject("items");
        itemSchema.put("type", "object");
        ObjectNode itemProperties = itemSchema.putObject("properties");
        itemProperties.putObject("name").put("type", "string");
        itemProperties.putObject("amount").put("type", "number");
        itemProperties.putObject("quantity").put("type", "integer");
        itemProperties.putObject("itemDiscount").put("type", "number");
        itemSchema.putArray("required").add("name").add("amount");

        schema.putArray("required").add("isReceipt");
        return schema;
    }

    // ===================== RESPONSE PARSING =====================

    private String extractModelOutputText(JsonNode response) {
        if (response == null) {
            throw new ReceiptScanFailedException("The receipt scanning service returned an empty response");
        }
        for (JsonNode step : response.path("steps")) {
            if ("model_output".equals(step.path("type").asText())) {
                for (JsonNode content : step.path("content")) {
                    if ("text".equals(content.path("type").asText())) {
                        return content.path("text").asText();
                    }
                }
            }
        }
        throw new ReceiptScanFailedException("The receipt scanning service did not return a result");
    }

    private JsonNode parseExtractedJson(String extractedJson) {
        try {
            return objectMapper.readTree(extractedJson);
        } catch (Exception ex) {
            throw new ReceiptScanFailedException("Could not parse the receipt scanning result");
        }
    }

    private ScannedReceipt toScannedReceipt(JsonNode extracted) {
        String merchant = textOrNull(extracted, "merchant");
        BigDecimal amount = numberOrNull(extracted, "amount");
        String currency = textOrNull(extracted, "currency");
        LocalDate transactionDate = dateOrNull(extracted, "transactionDate");

        List<ScannedLineItem> items = new ArrayList<>();
        for (JsonNode itemNode : extracted.path("items")) {
            String name = textOrNull(itemNode, "name");
            BigDecimal itemAmount = numberOrNull(itemNode, "amount");
            if (name != null && itemAmount != null) {
                Integer itemQuantity = integerOrNull(itemNode, "quantity");
                BigDecimal itemDiscount = numberOrNull(itemNode, "itemDiscount");
                items.add(new ScannedLineItem(name, itemAmount, itemQuantity != null ? itemQuantity : 1, itemDiscount));
            }
        }

        BigDecimal otherDiscount = sumArray(extracted, "otherDiscounts");
        BigDecimal voucherAmount = numberOrNull(extracted, "voucherAmount");
        return new ScannedReceipt(merchant, amount, currency, transactionDate, otherDiscount, voucherAmount, items);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private BigDecimal numberOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : null;
    }

    private Integer integerOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.intValue() : null;
    }

    private BigDecimal sumArray(JsonNode node, String field) {
        JsonNode array = node.path(field);
        if (!array.isArray() || array.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode value : array) {
            if (value.isNumber()) {
                sum = sum.add(value.decimalValue());
            }
        }
        return sum;
    }

    private LocalDate dateOrNull(JsonNode node, String field) {
        String text = textOrNull(node, field);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
