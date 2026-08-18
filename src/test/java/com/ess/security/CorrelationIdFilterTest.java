package com.ess.security;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CorrelationIdFilterTest {

    @Test
    void preservesSafeRequestIdAndClearsMdc() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.REQUEST_ID_HEADER, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdInChain = new AtomicReference<>();

        filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> requestIdInChain.set(MDC.get("requestId")));

        assertEquals("request-123", requestIdInChain.get());
        assertEquals("request-123", response.getHeader(CorrelationIdFilter.REQUEST_ID_HEADER));
        assertNull(MDC.get("requestId"));
    }

    @Test
    void replacesUnsafeRequestId() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.REQUEST_ID_HEADER, "unsafe\r\nforged-header");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        assertNotEquals("unsafe\r\nforged-header", response.getHeader(CorrelationIdFilter.REQUEST_ID_HEADER));
    }
}
