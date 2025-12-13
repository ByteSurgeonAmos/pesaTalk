package com.pesatalk.controller;

import com.pesatalk.integration.mpesa.dto.STKCallbackPayload;
import com.pesatalk.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/callback")
public class CallbackController {

    private static final Logger log = LoggerFactory.getLogger(CallbackController.class);

    private final TransactionService transactionService;

    public CallbackController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/mpesa/stk")
    public ResponseEntity<Map<String, String>> handleSTKCallback(
        @RequestBody STKCallbackPayload payload
    ) {
        log.info("Received MPesa STK callback for checkout: {}",
            payload.getCheckoutRequestId());

        try {
            transactionService.processSTKCallback(payload);
        } catch (Exception e) {
            log.error("Error processing STK callback", e);
            // Still return success to MPesa to prevent retries
        }

        return ResponseEntity.ok(Map.of(
            "ResultCode", "0",
            "ResultDesc", "Callback received successfully"
        ));
    }

    @PostMapping("/mpesa/confirmation")
    public ResponseEntity<Map<String, String>> handleConfirmation(
        @RequestBody Map<String, Object> payload
    ) {
        log.info("Received MPesa confirmation callback");
        log.debug("Confirmation payload: {}", payload);

        return ResponseEntity.ok(Map.of(
            "ResultCode", "0",
            "ResultDesc", "Confirmation received"
        ));
    }

    @PostMapping("/mpesa/validation")
    public ResponseEntity<Map<String, String>> handleValidation(
        @RequestBody Map<String, Object> payload
    ) {
        log.info("Received MPesa validation callback");
        log.debug("Validation payload: {}", payload);

        return ResponseEntity.ok(Map.of(
            "ResultCode", "0",
            "ResultDesc", "Accepted"
        ));
    }
}
