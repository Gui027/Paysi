package com.paysi.identity.kyc.webhook.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.core.error.*;
import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.kyc.port.KycProvider;
import com.paysi.identity.kyc.webhook.domain.*;
import com.paysi.identity.kyc.webhook.port.*;
import com.paysi.ledger.app.LedgerService;
import com.paysi.ledger.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class KycWebhookService {
    static final long VERIFICATION_FEE_CENTS=1200;
    private static final UUID PLATFORM_REVENUE=UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private final ObjectMapper json;private final WebhookSignatureVerifier signatures;private final KycWebhookStore store;private final KycProvider provider;private final LedgerService ledger;
    public KycWebhookService(ObjectMapper json,WebhookSignatureVerifier signatures,KycWebhookStore store,KycProvider provider,LedgerService ledger){this.json=json;this.signatures=signatures;this.store=store;this.provider=provider;this.ledger=ledger;}

    @Transactional
    public KycWebhookResult handle(String payload,String signature){
        if(!signatures.valid(payload,signature))throw new UnauthorizedException("KYC_SIGNATURE_INVALID","Assinatura do webhook inválida");
        KycWebhookEvent event=parse(payload);store.receive(event.providerEventId(),payload);
        if("PROCESSED".equals(store.lockStatus(event.providerEventId())))return new KycWebhookResult(event.providerEventId(),true);
        var account=store.lockAccount(event.accountReference()).orElseThrow(()->new ValidationException("KYC_ACCOUNT_UNKNOWN","Referência de conta desconhecida","accountReference"));
        if(account.status()==KycStatus.APPROVED){store.markProcessed(event.providerEventId());return new KycWebhookResult(event.providerEventId(),true);}
        if(event.status()==KycWebhookStatus.REJECTED){store.apply(event.accountReference(),KycStatus.REJECTED,null,event.requirements());store.markProcessed(event.providerEventId());return new KycWebhookResult(event.providerEventId(),false);}
        String providerAccount=account.providerAccountId()==null?provider.ensureSubaccount(event.accountReference()):account.providerAccountId();
        store.apply(event.accountReference(),KycStatus.APPROVED,providerAccount,event.requirements());
        ledger.write(new LedgerCommand(TransactionType.PLATFORM_FEE,new LedgerReference(ReferenceType.VERIFICATION,event.accountReference().toString()),"Taxa de verificação KYC",List.of(
                new LedgerEntry(event.accountReference(),Bucket.DEBT,Direction.DEBIT,VERIFICATION_FEE_CENTS,Origin.FEE,null),
                new LedgerEntry(PLATFORM_REVENUE,Bucket.SYSTEM,Direction.CREDIT,VERIFICATION_FEE_CENTS,Origin.FEE,null))));
        store.markProcessed(event.providerEventId());return new KycWebhookResult(event.providerEventId(),false);
    }

    private KycWebhookEvent parse(String payload){try{return json.readValue(payload,KycWebhookEvent.class);}catch(JsonProcessingException|IllegalArgumentException error){throw new ValidationException("KYC_PAYLOAD_INVALID","Payload do webhook inválido","payload");}}
}
