package com.collegeconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.collegeconnect.notification.WhatsAppCloudApiProvider;
import com.collegeconnect.notification.WhatsAppProperties;
import com.collegeconnect.notification.WhatsAppProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WhatsAppCloudApiProviderTest {

    @Test
    void sendsApprovedTemplateAndReturnsProviderMessageId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WhatsAppProperties properties = new WhatsAppProperties();
        properties.setEnabled(true);
        properties.setAccessToken("test-token");
        properties.setPhoneNumberId("12345");
        properties.getTemplates().put("TPL-ATT-ABSENT", "attendance_absent");

        server.expect(requestTo("https://graph.facebook.com/v22.0/12345/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andRespond(withSuccess(
                        "{\"messages\":[{\"id\":\"wamid.test-message\"}]}",
                        MediaType.APPLICATION_JSON));

        WhatsAppCloudApiProvider provider =
                new WhatsAppCloudApiProvider(builder, new ObjectMapper(), properties);
        WhatsAppProvider.SendResult result = provider.send(new WhatsAppProvider.SendRequest(
                "+919876543210", "TPL-ATT-ABSENT", "{\"studentId\":1}", "correlation-1"));

        assertThat(result.providerMessageId()).isEqualTo("wamid.test-message");
        server.verify();
    }
}
