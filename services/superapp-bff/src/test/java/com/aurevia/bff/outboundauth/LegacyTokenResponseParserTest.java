package com.aurevia.bff.outboundauth;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;import org.junit.jupiter.api.Test;
class LegacyTokenResponseParserTest {
 private final LegacyTokenResponseParser parser=new LegacyTokenResponseParser(new ObjectMapper());
 private OutboundAuthProfile profile(long max){return new OutboundAuthProfile("p",OutboundAuthMode.LEGACY_SERVICE_TOKEN,"connection://x","/token","JSON","secret://x",null,null,"/access_token","/expires_in",null,"/token_type","Bearer","INTERNAL_LEGACY_HEADER",30,1000,1000,max,1);}
 @Test void parsesAllowlistedPointersWithoutExposingToken(){var result=parser.parse("{\"access_token\":\"very-secret\",\"expires_in\":300,\"token_type\":\"Bearer\"}".getBytes(),profile(1024));assertEquals("very-secret",result.accessToken());assertFalse(result.toString().contains("very-secret"));}
 @Test void rejectsMissingMalformedAndOversizedResponses(){assertThrows(IllegalStateException.class,()->parser.parse("{}".getBytes(),profile(1024)));assertThrows(IllegalStateException.class,()->parser.parse("not-json".getBytes(),profile(1024)));assertThrows(IllegalStateException.class,()->parser.parse(new byte[1025],profile(1024)));}
 @Test void rejectsUnboundedLifetime(){assertThrows(IllegalStateException.class,()->parser.parse("{\"access_token\":\"x\",\"expires_in\":999999}".getBytes(),profile(1024)));}
 @Test void credentialStringIsAlwaysRedacted(){var value=new OutboundCredential("Bearer","very-secret",true);assertFalse(value.toString().contains("very-secret"));}
}
