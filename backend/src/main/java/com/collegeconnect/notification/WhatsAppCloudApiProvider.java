package com.collegeconnect.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class WhatsAppCloudApiProvider implements WhatsAppProvider {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final WhatsAppProperties properties;

    public WhatsAppCloudApiProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            WhatsAppProperties properties) {
        this.restClient = restClientBuilder.baseUrl("https://graph.facebook.com").build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public SendResult send(SendRequest request) {
        if (!properties.isEnabled()
                || properties.getAccessToken().isBlank()
                || properties.getPhoneNumberId().isBlank()) {
            throw new WhatsAppDeliveryException(
                    "WhatsApp Cloud API is not configured",
                    WhatsAppDeliveryException.Outcome.PERMANENT,
                    "WHATSAPP_NOT_CONFIGURED",
                    null,
                    null);
        }
        String configuredTemplate = properties.getTemplates().get(request.templateName());
        if (configuredTemplate == null || !configuredTemplate.matches("[a-z0-9_]{1,120}")) {
            throw new WhatsAppDeliveryException(
                    "No approved WhatsApp template mapping is configured",
                    WhatsAppDeliveryException.Outcome.PERMANENT,
                    "TEMPLATE_NOT_CONFIGURED",
                    null,
                    null);
        }
        String recipient = request.recipient().replaceAll("\\D", "");
        if (!recipient.matches("[0-9]{8,15}")) {
            throw new WhatsAppDeliveryException(
                    "Recipient phone number is invalid",
                    WhatsAppDeliveryException.Outcome.PERMANENT,
                    "INVALID_RECIPIENT",
                    null,
                    null);
        }

        try {
            Map<String, Object> template = new LinkedHashMap<>();
            template.put("name", configuredTemplate);
            template.put("language", Map.of("code", properties.getTemplateLanguage()));
            List<Map<String, Object>> components = templateParameters(request.payloadJson());
            if (!components.isEmpty()) {
                template.put("components", components);
            }
            Map<String, Object> body = Map.of(
                    "messaging_product", "whatsapp",
                    "to", recipient,
                    "type", "template",
                    "biz_opaque_callback_data", request.correlationId(),
                    "template", template);
            var responseEntity = restClient.post()
                    .uri("/{version}/{phoneNumberId}/messages",
                            properties.getApiVersion(), properties.getPhoneNumberId())
                    .headers(headers -> headers.setBearerAuth(properties.getAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(JsonNode.class);
            JsonNode response = responseEntity.getBody();
            String messageId = response == null || response.path("messages").isEmpty()
                    ? null
                    : response.path("messages").get(0).path("id").asText(null);
            if (messageId == null || messageId.isBlank()) {
                throw new WhatsAppDeliveryException(
                        "Provider response did not include a message id",
                        WhatsAppDeliveryException.Outcome.AMBIGUOUS,
                        "MISSING_PROVIDER_MESSAGE_ID",
                        responseEntity.getStatusCode().value(),
                        null);
            }
            return new SendResult(messageId, responseEntity.getStatusCode().value());
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            WhatsAppDeliveryException.Outcome outcome = status == 429 || status >= 500
                    ? WhatsAppDeliveryException.Outcome.RETRYABLE
                    : WhatsAppDeliveryException.Outcome.PERMANENT;
            throw new WhatsAppDeliveryException(
                    "WhatsApp API rejected the request",
                    outcome,
                    "HTTP_" + status,
                    status,
                    exception);
        } catch (WhatsAppDeliveryException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new WhatsAppDeliveryException(
                    "WhatsApp API delivery outcome is ambiguous",
                    WhatsAppDeliveryException.Outcome.AMBIGUOUS,
                    "TRANSPORT_OUTCOME_UNKNOWN",
                    null,
                    exception);
        }
    }

    private List<Map<String, Object>> templateParameters(String payloadJson) {
        try {
            JsonNode parameters = objectMapper.readTree(payloadJson).path("templateParameters");
            if (!parameters.isArray() || parameters.isEmpty()) {
                return List.of();
            }
            List<Map<String, Object>> bodyParameters = new ArrayList<>();
            for (JsonNode parameter : parameters) {
                if (!parameter.isTextual()) {
                    throw new WhatsAppDeliveryException(
                            "Template parameters must be text values",
                            WhatsAppDeliveryException.Outcome.PERMANENT,
                            "INVALID_TEMPLATE_PARAMETERS",
                            null,
                            null);
                }
                bodyParameters.add(Map.of("type", "text", "text", parameter.asText()));
            }
            return List.of(Map.of("type", "body", "parameters", bodyParameters));
        } catch (WhatsAppDeliveryException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new WhatsAppDeliveryException(
                    "Outbox payload is not valid JSON",
                    WhatsAppDeliveryException.Outcome.PERMANENT,
                    "INVALID_OUTBOX_PAYLOAD",
                    null,
                    exception);
        }
    }
}
