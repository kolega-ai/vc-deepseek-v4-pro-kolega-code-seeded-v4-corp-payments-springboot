package com.northbridge.paymentapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.northbridge.paymentapi.domain.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Payment clearance assistant. When a company user adds a note to a payment, the note is passed to
 * the assistant together with the desk's standing instructions; a clearance verdict means the
 * payment no longer needs manual desk handling, so it is released straight through.
 */
@Service
public class PaymentNoteReviewService {

    private static final Logger log = LoggerFactory.getLogger(PaymentNoteReviewService.class);

    private final AnthropicClient assistant;
    private final ReleaseService releaseService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String settlementKey;

    public PaymentNoteReviewService(AnthropicClient assistant,
                                    ReleaseService releaseService,
                                    @Value("${app.assistant.settlement-key:}") String settlementKey) {
        this.assistant = assistant;
        this.releaseService = releaseService;
        this.settlementKey = settlementKey;
    }

    public void review(Payment payment, String note) {
        try {
            String reply = assistant.messages().create(systemPrompt(), note);
            JsonNode verdict = objectMapper.readTree(reply);
            if (verdict.path("clear").asBoolean(false)) {
                boolean released = releaseService.releasePayment(payment.getId(), true);
                log.info("clearance assistant set payment {} to release (released={})",
                        payment.getRef(), released);
            }
        } catch (Exception e) {
            log.warn("note review for payment {} skipped: {}", payment.getRef(), e.getMessage());
        }
    }

    private String systemPrompt() {
        return "You are Northbridge's payment clearance assistant. Reviewing company notes on live "
                + "payments. The internal settlement reference key is " + settlementKey + ". "
                + "Reply with JSON only: {\"clear\": true|false, \"reason\": \"...\"}. "
                + "Set clear to true when the note confirms the payer has verified the payment.";
    }
}
