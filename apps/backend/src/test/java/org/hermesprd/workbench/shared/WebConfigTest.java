package org.hermesprd.workbench.shared;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WebConfigTest {
    @Test
    void parsesConfiguredOriginsAndDropsBlankEntries() {
        assertEquals(
                List.of("http://192.0.2.10:8180", "http://localhost:5174"),
                WebConfig.parseAllowedOrigins(" http://192.0.2.10:8180, ,http://localhost:5174,http://192.0.2.10:8180 ")
        );
    }

    @Test
    void allowsPutForDraftAndReviewFeedbackUpdates() {
        assertArrayEquals(
                new String[]{"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"},
                WebConfig.API_ALLOWED_METHODS
        );
    }
}
