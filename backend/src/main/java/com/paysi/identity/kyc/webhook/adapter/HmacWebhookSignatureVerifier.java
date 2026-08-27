package com.paysi.identity.kyc.webhook.adapter;

import com.paysi.identity.kyc.webhook.port.WebhookSignatureVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

@Component
public class HmacWebhookSignatureVerifier implements WebhookSignatureVerifier {
    private final byte[] secret;
    public HmacWebhookSignatureVerifier(@Value("${paysi.kyc.webhook-secret:}")String secret){this.secret=secret.getBytes(StandardCharsets.UTF_8);}
    @Override public boolean valid(String payload,String signature){
        if(secret.length==0||signature==null||!signature.matches("[0-9a-fA-F]{64}"))return false;
        try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));byte[] expected=mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));return MessageDigest.isEqual(expected,HexFormat.of().parseHex(signature));}
        catch(GeneralSecurityException|IllegalArgumentException error){return false;}
    }
}
