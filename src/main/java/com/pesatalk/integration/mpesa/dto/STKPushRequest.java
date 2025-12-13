package com.pesatalk.integration.mpesa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record STKPushRequest(
    @JsonProperty("BusinessShortCode") String businessShortCode,
    @JsonProperty("Password") String password,
    @JsonProperty("Timestamp") String timestamp,
    @JsonProperty("TransactionType") String transactionType,
    @JsonProperty("Amount") String amount,
    @JsonProperty("PartyA") String partyA,
    @JsonProperty("PartyB") String partyB,
    @JsonProperty("PhoneNumber") String phoneNumber,
    @JsonProperty("CallBackURL") String callBackURL,
    @JsonProperty("AccountReference") String accountReference,
    @JsonProperty("TransactionDesc") String transactionDesc
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String businessShortCode;
        private String password;
        private String timestamp;
        private String transactionType = "CustomerPayBillOnline";
        private String amount;
        private String partyA;
        private String partyB;
        private String phoneNumber;
        private String callBackURL;
        private String accountReference;
        private String transactionDesc;

        public Builder businessShortCode(String businessShortCode) {
            this.businessShortCode = businessShortCode;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder transactionType(String transactionType) {
            this.transactionType = transactionType;
            return this;
        }

        public Builder amount(String amount) {
            this.amount = amount;
            return this;
        }

        public Builder partyA(String partyA) {
            this.partyA = partyA;
            return this;
        }

        public Builder partyB(String partyB) {
            this.partyB = partyB;
            return this;
        }

        public Builder phoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public Builder callBackURL(String callBackURL) {
            this.callBackURL = callBackURL;
            return this;
        }

        public Builder accountReference(String accountReference) {
            this.accountReference = accountReference;
            return this;
        }

        public Builder transactionDesc(String transactionDesc) {
            this.transactionDesc = transactionDesc;
            return this;
        }

        public STKPushRequest build() {
            return new STKPushRequest(
                businessShortCode,
                password,
                timestamp,
                transactionType,
                amount,
                partyA,
                partyB,
                phoneNumber,
                callBackURL,
                accountReference,
                transactionDesc
            );
        }
    }
}
