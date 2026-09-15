package com.aurevia.authz.observability;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SafeErrorBodySerializerTest {
  ObjectMapper json=new ObjectMapper();
  @Test void recursivelyRedactsSensitiveFields(){
    var serializer=new SafeErrorBodySerializer(json,new SensitiveDataRedactor(),1024);
    var result=serializer.serialize(400,"application/json",("""
      {"password":"p","nested":{"access_token":"a","refresh_token":"r","cookie":"c"},
       "items":[{"client_secret":"s"}],"message":"safe"}
      """).getBytes(StandardCharsets.UTF_8));
    assertThat(result.body()).contains("[REDACTED]","message","safe")
        .doesNotContain("\"p\"","\"a\"","\"r\"","\"c\"","\"s\"");
    assertThat(result.redacted()).isTrue();
  }
  @Test void successfulAndBinaryBodiesAreNeverStored(){
    var serializer=new SafeErrorBodySerializer(json,new SensitiveDataRedactor(),1024);
    assertThat(serializer.serialize(200,"application/json","{\"x\":1}".getBytes()).body()).isNull();
    assertThat(serializer.serialize(500,"application/octet-stream",new byte[]{0,1,2}).body()).isNull();
  }
  @Test void oversizedSafeJsonIsTruncated(){
    var serializer=new SafeErrorBodySerializer(json,new SensitiveDataRedactor(),256);
    var result=serializer.serialize(500,"application/problem+json",
        ("{\"message\":\""+"x".repeat(1000)+"\"}").getBytes(StandardCharsets.UTF_8));
    assertThat(result.truncated()).isTrue();assertThat(result.body().getBytes(StandardCharsets.UTF_8)).hasSize(256);
  }
}
