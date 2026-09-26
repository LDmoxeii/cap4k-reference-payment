package com.only4.cap4k.reference.payment.adapter.endpoints.payment

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.reference.payment.application.commands.payment.expiry.CloseExpiredPaymentCmd
import com.only4.cap4k.reference.payment.contract.endpoints.payment.api.CloseExpiredPaymentEndpoint
import com.only4.cap4k.reference.payment.domain.aggregates.payment.PaymentId
import org.springframework.stereotype.Component

@Component
class CloseExpiredPaymentEndpointHandler :
    EndpointHandler<CloseExpiredPaymentEndpoint.Request, CloseExpiredPaymentEndpoint.Response> {
    override fun handle(request: CloseExpiredPaymentEndpoint.Request): CloseExpiredPaymentEndpoint.Response {
        val result = Mediator.commands.send(
            CloseExpiredPaymentCmd.Request(
                paymentId = PaymentId.parse(request.paymentId),
                merchantId = request.merchantId,
                idempotencyKey = request.idempotencyKey,
            ),
        )
        return CloseExpiredPaymentEndpoint.Response(
            paymentId = result.paymentId,
            paymentStatus = publicPaymentStatus(result.paymentStatus),
            receipt = result.receipt,
        )
    }
}
