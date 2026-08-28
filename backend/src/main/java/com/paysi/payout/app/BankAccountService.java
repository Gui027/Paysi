package com.paysi.payout.app;

import com.paysi.core.error.*;
import com.paysi.payout.domain.*;
import com.paysi.payout.port.*;
import com.paysi.security.mfa.port.SecretProtector;
import com.paysi.security.mfa.app.MfaGuard;
import com.paysi.security.mfa.domain.SensitiveOperation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;

@Service
public class BankAccountService {
    private final PayoutRepository repository;private final BankVerificationProvider provider;private final SecretProtector protector;private final MfaGuard mfa;private final Clock clock;
    public BankAccountService(PayoutRepository r,BankVerificationProvider p,SecretProtector s,MfaGuard m){this(r,p,s,m,Clock.systemUTC());}
    BankAccountService(PayoutRepository r,BankVerificationProvider p,SecretProtector s,MfaGuard m,Clock c){repository=r;provider=p;protector=s;mfa=m;clock=c;}
    @Transactional public BankAccount create(UUID accountId,BankAccountCommand command,UUID challengeId){
        mfa.consume(accountId,challengeId,SensitiveOperation.BANK_ACCOUNT_CHANGE);
        var holder=repository.accountHolder(accountId).orElseThrow(()->new ValidationException("ACCOUNT_UNAVAILABLE","Conta indisponível",null));
        if(!digits(command.holderTaxId()).equals(digits(holder.taxId()))||!provider.verifyOwnership(command,holder.taxId()))throw new ValidationException("BANK_HOLDER_MISMATCH","A conta bancária precisa pertencer ao mesmo titular","holderTaxId");
        String number=digits(command.accountNumber())+digits(command.digit());if(number.length()<4)throw new ValidationException("BANK_NUMBER_INVALID","Número bancário inválido","accountNumber");
        var bank=new BankAccount(UUID.randomUUID(),accountId,command.bankCode(),command.branch(),number.substring(number.length()-4),digits(command.holderTaxId()),command.holderType(),command.holderName(),command.accountType(),command.pixKeyType(),clock.instant(),null);
        repository.insertBank(bank,protector.encrypt(number.getBytes(StandardCharsets.UTF_8)),protector.encrypt(command.pixKey().getBytes(StandardCharsets.UTF_8)));return bank;
    }
    @Transactional public void archive(UUID accountId,UUID bankId,UUID challengeId){mfa.consume(accountId,challengeId,SensitiveOperation.BANK_ACCOUNT_CHANGE);var bank=repository.lockBank(bankId).orElseThrow(BankAccountService::missing);if(!bank.accountId().equals(accountId))throw missing();repository.archiveBank(accountId,bankId,clock.instant());}
    private static ValidationException missing(){return new ValidationException("BANK_ACCOUNT_NOT_FOUND","Conta bancária não encontrada","bankAccountId");}
    private static String digits(String value){return value==null?"":value.replaceAll("\\D","");}
}
