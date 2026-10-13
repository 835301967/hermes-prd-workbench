package org.hermesprd.workbench.shared;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CorsRequestTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Fixture.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void sameOriginPostAcceptsBothLoopbackNamesAndCustomPorts() throws Exception {
        for (String host : new String[]{"localhost", "127.0.0.1", "192.0.2.10"}) {
            for (int port : new int[]{8180, 9080}) {
                mvc.perform(address(post("/api/v1/cors-probe"), host, port)
                                .header("Origin", "http://" + host + ":" + port))
                        .andExpect(status().isOk()).andExpect(content().string("ok"));
            }
        }
    }

    @Test
    void strippedProxyPortReproducesInvalidCorsRequest() throws Exception {
        mvc.perform(address(post("/api/v1/cors-probe"), "localhost", 80)
                        .header("Origin", "http://localhost:8180"))
                .andExpect(status().isForbidden()).andExpect(content().string("Invalid CORS request"));
    }

    @Test
    void foreignAndOpaqueOriginsRemainForbidden() throws Exception {
        for (String origin : new String[]{"https://untrusted.example", "null", "http://localhost:8181"}) {
            mvc.perform(address(post("/api/v1/cors-probe"), "localhost", 8180).header("Origin", origin))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void requestsWithoutOriginRemainSupported() throws Exception {
        mvc.perform(address(post("/api/v1/cors-probe"), "localhost", 8180))
                .andExpect(status().isOk());
    }

    @Test
    void configuredDevelopmentOriginAllowsDraftPreflight() throws Exception {
        mvc.perform(address(options("/api/v1/cors-probe"), "localhost", 18100)
                        .header("Origin", "http://localhost:5174")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5174"));
    }

    @Test
    void foreignPreflightRemainsForbidden() throws Exception {
        mvc.perform(address(options("/api/v1/cors-probe"), "localhost", 8180)
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void developmentPreflightCannotRequestInternalPluginHeader() throws Exception {
        mvc.perform(address(options("/api/v1/cors-probe"), "localhost", 18100)
                        .header("Origin", "http://localhost:5174")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "X-Plugin-Key"))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder address(MockHttpServletRequestBuilder request, String host, int port) {
        return request.with(servlet -> {
            servlet.setScheme("http");
            servlet.setServerName(host);
            servlet.setServerPort(port);
            return servlet;
        });
    }

    @Configuration
    @EnableWebMvc
    static class Fixture {
        @Bean
        WebConfig webConfig() {
            return new WebConfig("http://127.0.0.1:5174,http://localhost:5174");
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {
        @RequestMapping(path = "/api/v1/cors-probe", method = {RequestMethod.POST, RequestMethod.PUT})
        String probe() {
            return "ok";
        }
    }
}
