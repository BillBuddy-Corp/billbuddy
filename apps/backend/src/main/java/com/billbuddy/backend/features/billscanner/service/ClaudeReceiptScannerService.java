package com.billbuddy.backend.features.billscanner.service;

import com.billbuddy.backend.exception.ReceiptScanFailedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

// Uses Claude's tool-use feature for structured output: the "tool" input schema is the shape we
// want, tool_choice forces Claude to call it, and the response's tool_use.input is already a
// proper JSON object -- no double-parse of a text string, unlike the Gemini implementation.
@Slf4j
@Service
public class ClaudeReceiptScannerService implements ReceiptScannerService {

    private static final String TOOL_NAME = "record_receipt_data";
    private static final String EXTRACTION_PROMPT = """
            Extract structured data from this receipt image using the record_receipt_data tool.

            Only include a field if you can confidently read it. Never guess or infer a value that \
            isn't actually printed on the receipt -- omit the field instead, especially transactionDate.

            Report every printed number exactly as it appears -- do not do any arithmetic yourself \
            (no subtracting discounts, no combining duplicate lines, no adding quantities together). \
            All of that math happens afterward in code; your only job is to transcribe and identify.

            Items: report each item's price exactly as printed on its own line, before any discount \
            specific to that item. If the same item is printed on more than one separate line, report \
            it as separate line items too -- do not merge them yourself. An item is always something \
            actually purchased -- never a discount line, coupon line, promotional adjustment, payment \
            method marker (e.g. "Employee Card Swiped"), loyalty points line, or any other non-product \
            text. Item prices are never negative: however a discount is printed, it always belongs in \
            itemDiscount or otherDiscounts, never as its own entry in items.

            Item discounts: if a discount line is printed directly under one specific item (or a \
            multi-buy offer covering a specific group of units of that same item), report that \
            discount's printed amount in that item's itemDiscount field -- do not subtract it from \
            amount yourself, and do not report it as a separate item. If the same item name is \
            printed on more than one separate physical line, check each line independently -- one \
            copy may have its own discount printed directly under it while another doesn't; never \
            assume all copies share the same discount just because the name matches.

            Whole-receipt discounts: if one or more discounts appear separately (typically near the \
            total) with no specific item tied to them (e.g. Promotions, a Colleague/Staff/Loyalty \
            discount), report each one as its own entry in otherDiscounts -- one number per printed \
            line, exactly as printed. If there's more than one, list them all separately; do not add \
            them together yourself. Some receipts print a running balance (subtotal, then discount, \
            then balance-to-pay, then further adjustments, then a final balance) where the same \
            discount figure can appear more than once as the balance is recalculated step by step -- \
            if you see the exact same discount amount repeated this way, it's one discount, not \
            several; report it only once.

            Quantities: if a single line explicitly states a multiplier (e.g. "3 @ €0.43 each", \
            "2 x"), set quantity to that number and amount to the total price shown for that line. If \
            no multiplier is printed on the line, omit quantity.

            Vouchers: if part of the total was paid using a voucher or gift card (a separate payment \
            method covering part of the bill, not a discount off the price), report that amount in \
            voucherAmount instead of otherDiscounts -- even if the printed line is labeled "discount \
            voucher" or similar, the word "discount" there doesn't make it a price reduction; a \
            voucher is always a payment method.
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String workspaceId;

    public ClaudeReceiptScannerService(
            ObjectMapper objectMapper,
            @Value("${claude.api-key}") String apiKey,
            @Value("${claude.model}") String model,
            @Value("${claude.workspace-id:}") String workspaceId
    ) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com/v1")
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.workspaceId = workspaceId;
    }

    @Override
    public ScannedReceipt scan(byte[] imageBytes, String mimeType) {
        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .headers(headers -> {
                        if (!workspaceId.isBlank()) {
                            headers.set("anthropic-workspace-id", workspaceId);
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequestBody(imageBytes, mimeType))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            log.warn("Claude API call failed, status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ReceiptScanFailedException("Could not reach the receipt scanning service");
        } catch (RestClientException ex) {
            log.warn("Claude API call failed", ex);
            throw new ReceiptScanFailedException("Could not reach the receipt scanning service");
        }

        JsonNode extracted = extractToolInput(response);

        if (!extracted.path("isReceipt").asBoolean(false)) {
            throw new ReceiptScanFailedException("This doesn't look like a receipt");
        }

        return toScannedReceipt(extracted);
    }

    // ===================== REQUEST BUILDING =====================

    private String buildRequestBody(byte[] imageBytes, String mimeType) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", 1024);

        ArrayNode tools = root.putArray("tools");
        ObjectNode tool = tools.addObject();
        tool.put("name", TOOL_NAME);
        tool.put("description", "Record structured data extracted from a receipt image");
        tool.set("input_schema", buildInputSchema());

        ObjectNode toolChoice = root.putObject("tool_choice");
        toolChoice.put("type", "tool");
        toolChoice.put("name", TOOL_NAME);

        ArrayNode messages = root.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        ArrayNode content = userMessage.putArray("content");

        ObjectNode imageBlock = content.addObject();
        imageBlock.put("type", "image");
        ObjectNode source = imageBlock.putObject("source");
        source.put("type", "base64");
        source.put("media_type", mimeType);
        source.put("data", Base64.getEncoder().encodeToString(imageBytes));

        ObjectNode textBlock = content.addObject();
        textBlock.put("type", "text");
        textBlock.put("text", EXTRACTION_PROMPT);

        return root.toString();
    }

    private JsonNode buildInputSchema() {
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
        otherDiscounts.put("description", "Each whole-receipt discount printed separately, not tied to any specific item -- one entry per line (e.g. Promotions, Colleague/Staff/Loyalty discount)");
        ObjectNode voucherAmount = properties.putObject("voucherAmount");
        voucherAmount.put("type", "number");
        voucherAmount.put("description", "Amount of the total paid using a voucher or gift card, if printed");

        ObjectNode items = properties.putObject("items");
        items.put("type", "array");
        ObjectNode itemSchema = items.putObject("items");
        itemSchema.put("type", "object");
        ObjectNode itemProperties = itemSchema.putObject("properties");
        itemProperties.putObject("name").put("type", "string");
        ObjectNode itemAmount = itemProperties.putObject("amount");
        itemAmount.put("type", "number");
        itemAmount.put("description", "This item's price exactly as printed, before its own itemDiscount if any");
        ObjectNode quantity = itemProperties.putObject("quantity");
        quantity.put("type", "integer");
        quantity.put("description", "The multiplier printed on this line, if any (e.g. \"3 @ ... each\")");
        ObjectNode itemDiscount = itemProperties.putObject("itemDiscount");
        itemDiscount.put("type", "number");
        itemDiscount.put("description", "A discount printed directly under this item, if any -- do not subtract it yourself");
        itemSchema.putArray("required").add("name").add("amount");

        schema.putArray("required").add("isReceipt");
        return schema;
    }

    // ===================== RESPONSE PARSING =====================

    private JsonNode extractToolInput(JsonNode response) {
        if (response == null) {
            throw new ReceiptScanFailedException("The receipt scanning service returned an empty response");
        }
        for (JsonNode block : response.path("content")) {
            if ("tool_use".equals(block.path("type").asText())) {
                return block.path("input");
            }
        }
        throw new ReceiptScanFailedException("The receipt scanning service did not return a result");
    }

    private ScannedReceipt toScannedReceipt(JsonNode extracted) {
        String merchant = textOrNull(extracted, "merchant");
        BigDecimal amount = numberOrNull(extracted, "amount");
        String currency = textOrNull(extracted, "currency");
        LocalDate transactionDate = dateOrNull(extracted, "transactionDate");
        BigDecimal otherDiscount = sumArray(extracted, "otherDiscounts");
        BigDecimal voucherAmount = numberOrNull(extracted, "voucherAmount");

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

    // Sums a list of separately-printed discount lines -- Claude only enumerates them, this adds
    // them up, same "no arithmetic from the model" rule as everywhere else in this extraction.
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
