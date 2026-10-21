package com.northbridge.coremock.service;

import com.northbridge.coremock.domain.PaymentStatus;
import com.northbridge.coremock.repo.PaymentStatusRepository;
import com.northbridge.coremock.security.ScopeToken;
import com.northbridge.coremock.web.ApiException;
import com.northbridge.coremock.web.dto.PaymentStatusResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment status lookup. Any valid scope (company or bank) may query a status
 * by core reference; authentication is enforced by the interceptor and the
 * reference itself is an unguessable core-generated identifier.
 */
@Service
public class PaymentStatusService {

    private final PaymentStatusRepository paymentStatusRepository;

    public PaymentStatusService(PaymentStatusRepository paymentStatusRepository) {
        this.paymentStatusRepository = paymentStatusRepository;
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse find(ScopeToken scopeToken, String coreRef) {
        PaymentStatus status = paymentStatusRepository.findByCoreRef(coreRef)
                .orElseThrow(ApiException::recordNotFound);
        return PaymentStatusResponse.from(status);
    }
}
