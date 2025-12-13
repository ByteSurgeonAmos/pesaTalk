package com.pesatalk.service.intent;

import com.pesatalk.model.enums.Intent;

import java.util.Optional;

public interface IntentParser {

    Intent getSupportedIntent();

    int getPriority();

    Optional<ParsedIntent> parse(String text);
}
