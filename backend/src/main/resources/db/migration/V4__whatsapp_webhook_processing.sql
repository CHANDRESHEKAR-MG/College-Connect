ALTER TABLE whatsapp_webhook_event
    ADD COLUMN payload_json VARCHAR(12000);

ALTER TABLE whatsapp_webhook_event
    ADD COLUMN processing_status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED';

ALTER TABLE whatsapp_webhook_event
    ADD COLUMN processing_attempts INT NOT NULL DEFAULT 0;

ALTER TABLE whatsapp_webhook_event
    ADD COLUMN processing_error VARCHAR(1000);

ALTER TABLE whatsapp_webhook_event
    ADD COLUMN processed_at TIMESTAMP(6);

CREATE INDEX ix_whatsapp_webhook_processing
    ON whatsapp_webhook_event (processing_status, received_at);
