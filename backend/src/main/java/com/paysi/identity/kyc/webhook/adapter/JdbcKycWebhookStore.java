package com.paysi.identity.kyc.webhook.adapter;

import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.kyc.domain.KycRequirement;
import com.paysi.identity.kyc.webhook.port.KycWebhookStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.*;

@Repository
public class JdbcKycWebhookStore implements KycWebhookStore {
    private static final String PROVIDER="KYC";private final JdbcTemplate jdbc;
    public JdbcKycWebhookStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override public boolean receive(String id,String payload){return jdbc.update("insert into provider_events(provider,provider_event_id,event_type,payload,signature_valid) values (?,?, 'KYC_STATUS',?::jsonb,true) on conflict do nothing",PROVIDER,id,payload)==1;}
    @Override public String lockStatus(String id){return jdbc.queryForObject("select status from provider_events where provider=? and provider_event_id=? for update",String.class,PROVIDER,id);}
    @Override public Optional<AccountKycState> lockAccount(UUID id){return jdbc.query("select kyc_status,provider_account_id from accounts where id=? for update",(rs,row)->new AccountKycState(KycStatus.valueOf(rs.getString(1)),rs.getString(2)),id).stream().findFirst();}
    @Override public void apply(UUID id,KycStatus status,String providerAccountId,List<KycRequirement> requirements){
        jdbc.update("update accounts set kyc_status=?,provider_account_id=coalesce(provider_account_id,?) where id=?",status.name(),providerAccountId,id);
        jdbc.update("delete from kyc_requirements where account_id=?",id);
        requirements.forEach(r->jdbc.update("insert into kyc_requirements(account_id,code,label,status,reason,estimated_at) values (?,?,?,?,?,?)",id,r.code(),r.label(),r.status(),r.reason(),r.estimatedAt()==null?null:Timestamp.from(r.estimatedAt())));
    }
    @Override public void markProcessed(String id){jdbc.update("update provider_events set status='PROCESSED',processed_at=now(),attempt_count=attempt_count+1,error=null where provider=? and provider_event_id=?",PROVIDER,id);}
}
