package org.hermesprd.workbench.shared;

import org.hermesprd.workbench.telemetry.TelemetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiExceptionHandlerTest {
    private MockMvc mvc;
    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new UploadFailureProbe())
                .setControllerAdvice(new ApiExceptionHandler(mock(TelemetryService.class))).build();
    }
    @Test
    void malformedUploadIs400WithoutLeakingParserContent() throws Exception {
        mvc.perform(get("/invalid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(content().string(not(containsString("private-content-marker"))));
    }
    @Test
    void missingUploadPartIs400() throws Exception {
        mvc.perform(get("/missing")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    @Test
    void oversizedUploadStillSelects413HandlerInsteadOfMultipartParent() throws Exception {
        mvc.perform(get("/oversize")).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }
    @RestController
    static class UploadFailureProbe {
        @GetMapping("/invalid")
        void invalid() { throw new MultipartException("private-content-marker"); }
        @GetMapping("/missing")
        void missing() throws MissingServletRequestPartException { throw new MissingServletRequestPartException("file"); }
        @GetMapping("/oversize")
        void oversized() { throw new MaxUploadSizeExceededException(1); }
    }
}
