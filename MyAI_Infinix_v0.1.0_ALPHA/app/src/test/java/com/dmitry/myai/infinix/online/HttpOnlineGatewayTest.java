package com.dmitry.myai.infinix.online;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpOnlineGatewayTest {

    @Test
    public void acceptsHttpsGateway() {
        assertTrue(new HttpOnlineGateway("https://example.com/v1/message").isAvailable());
    }

    @Test
    public void acceptsHttpForLocalGateway() {
        assertTrue(new HttpOnlineGateway("http://192.168.1.10:8080/v1/message").isAvailable());
    }

    @Test
    public void rejectsInvalidScheme() {
        assertFalse(new HttpOnlineGateway("ftp://example.com/message").isAvailable());
    }

    @Test
    public void rejectsGatewayWithCredentials() {
        assertFalse(new HttpOnlineGateway("https://user:pass@example.com/message").isAvailable());
    }

    @Test
    public void rejectsGatewayWithFragment() {
        assertFalse(new HttpOnlineGateway("https://example.com/message#token").isAvailable());
    }

    @Test
    public void rejectsOversizedEndpoint() {
        StringBuilder value = new StringBuilder("https://example.com/");
        while (value.length() <= 2048) value.append('x');
        assertFalse(new HttpOnlineGateway(value.toString()).isAvailable());
    }
}
