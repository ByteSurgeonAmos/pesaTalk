-- PesaTalk Initial Database Schema
-- PostgreSQL

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone_number_hash VARCHAR(64) NOT NULL UNIQUE,
    whatsapp_id VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_activity_at TIMESTAMP WITH TIME ZONE,
    daily_transaction_count INTEGER DEFAULT 0,
    daily_transaction_amount BIGINT DEFAULT 0,
    last_transaction_date TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT DEFAULT 0,
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'BLOCKED'))
);

CREATE INDEX idx_users_phone_hash ON users(phone_number_hash);
CREATE INDEX idx_users_whatsapp_id ON users(whatsapp_id);
CREATE INDEX idx_users_status ON users(status);

-- Contacts table
CREATE TABLE contacts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    alias VARCHAR(50) NOT NULL,
    alias_lowercase VARCHAR(50) NOT NULL,
    phone_number_hash VARCHAR(64) NOT NULL,
    phone_number_encrypted TEXT NOT NULL,
    is_favorite BOOLEAN DEFAULT FALSE,
    transaction_count INTEGER DEFAULT 0,
    last_transaction_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT DEFAULT 0,
    CONSTRAINT uk_contacts_user_alias UNIQUE (user_id, alias_lowercase)
);

CREATE INDEX idx_contacts_user_id ON contacts(user_id);
CREATE INDEX idx_contacts_alias_lower ON contacts(alias_lowercase);

-- Transactions table
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    idempotency_key VARCHAR(64) NOT NULL UNIQUE,
    sender_id UUID NOT NULL REFERENCES users(id),
    transaction_type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'INITIATED',
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    recipient_phone_hash VARCHAR(64) NOT NULL,
    recipient_phone_encrypted TEXT,
    recipient_name VARCHAR(100),
    account_reference VARCHAR(50),
    description VARCHAR(200),
    merchant_request_id VARCHAR(50),
    checkout_request_id VARCHAR(100),
    mpesa_receipt_number VARCHAR(30),
    result_code INTEGER,
    result_description VARCHAR(500),
    confirmation_expires_at TIMESTAMP WITH TIME ZONE,
    stk_pushed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    failure_reason VARCHAR(500),
    retry_count INTEGER DEFAULT 0,
    whatsapp_message_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT DEFAULT 0,
    CONSTRAINT chk_transactions_type CHECK (transaction_type IN ('SEND_MONEY', 'BUY_AIRTIME', 'PAY_BILL', 'BUY_GOODS')),
    CONSTRAINT chk_transactions_status CHECK (status IN ('INITIATED', 'PENDING_CONFIRMATION', 'CONFIRMED', 'PROCESSING', 'STK_PUSHED', 'COMPLETED', 'FAILED', 'CANCELLED', 'EXPIRED', 'REFUNDED')),
    CONSTRAINT chk_transactions_amount CHECK (amount > 0)
);

CREATE INDEX idx_transactions_sender_id ON transactions(sender_id);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_mpesa_ref ON transactions(mpesa_receipt_number);
CREATE INDEX idx_transactions_checkout_id ON transactions(checkout_request_id);
CREATE INDEX idx_transactions_idempotency ON transactions(idempotency_key);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);

-- Audit tables for Hibernate Envers
CREATE TABLE revinfo (
    rev SERIAL PRIMARY KEY,
    revtstmp BIGINT
);

CREATE TABLE users_audit (
    id UUID NOT NULL,
    rev INTEGER NOT NULL REFERENCES revinfo(rev),
    revtype SMALLINT,
    phone_number_hash VARCHAR(64),
    whatsapp_id VARCHAR(50),
    display_name VARCHAR(100),
    status VARCHAR(20),
    last_activity_at TIMESTAMP WITH TIME ZONE,
    daily_transaction_count INTEGER,
    daily_transaction_amount BIGINT,
    last_transaction_date TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    version BIGINT,
    PRIMARY KEY (id, rev)
);

CREATE TABLE contacts_audit (
    id UUID NOT NULL,
    rev INTEGER NOT NULL REFERENCES revinfo(rev),
    revtype SMALLINT,
    user_id UUID,
    alias VARCHAR(50),
    alias_lowercase VARCHAR(50),
    phone_number_hash VARCHAR(64),
    phone_number_encrypted TEXT,
    is_favorite BOOLEAN,
    transaction_count INTEGER,
    last_transaction_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    version BIGINT,
    PRIMARY KEY (id, rev)
);

CREATE TABLE transactions_audit (
    id UUID NOT NULL,
    rev INTEGER NOT NULL REFERENCES revinfo(rev),
    revtype SMALLINT,
    idempotency_key VARCHAR(64),
    sender_id UUID,
    transaction_type VARCHAR(20),
    status VARCHAR(30),
    amount NUMERIC(12, 2),
    currency VARCHAR(3),
    recipient_phone_hash VARCHAR(64),
    recipient_phone_encrypted TEXT,
    recipient_name VARCHAR(100),
    account_reference VARCHAR(50),
    description VARCHAR(200),
    merchant_request_id VARCHAR(50),
    checkout_request_id VARCHAR(100),
    mpesa_receipt_number VARCHAR(30),
    result_code INTEGER,
    result_description VARCHAR(500),
    confirmation_expires_at TIMESTAMP WITH TIME ZONE,
    stk_pushed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    failure_reason VARCHAR(500),
    retry_count INTEGER,
    whatsapp_message_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    version BIGINT,
    PRIMARY KEY (id, rev)
);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for updated_at
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_contacts_updated_at
    BEFORE UPDATE ON contacts
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
