package com.aurevia.authz.observability;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class CorrelationIdsTest{
 @Test void preservesValidAndReplacesMalformed(){assertThat(CorrelationIds.normalize("valid-123")).isEqualTo("valid-123");assertThat(CorrelationIds.normalize("bad value\r\n")).matches("[0-9a-f-]{36}");assertThat(CorrelationIds.normalize("x".repeat(129))).matches("[0-9a-f-]{36}");}
}
