package com.paysi.identity.kyc.port;

import com.paysi.identity.kyc.domain.KycProcess;
import java.util.UUID;

public interface KycProvider { KycProcess createProcess(UUID accountId); }
