package com.nordia.tasks.util;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class TimestampsTest {
    @Test
    void alwaysKeepsThreeDecimals() {
        assertThat(Timestamps.format(Instant.parse("2026-10-05T21:50:27Z")))
                .isEqualTo("2026-10-05T21:50:27.000Z");
        assertThat(Timestamps.format(Instant.parse("2026-10-05T21:50:27.120Z")))
                .isEqualTo("2026-10-05T21:50:27.120Z");
    }
}