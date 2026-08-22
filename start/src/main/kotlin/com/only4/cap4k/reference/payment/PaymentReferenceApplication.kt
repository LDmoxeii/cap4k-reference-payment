package com.only4.cap4k.reference.payment

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EntityScan(
    basePackages = [
        "com.only4.cap4k.reference.payment.domain",
        "com.only4.cap4k.ddd.domain.event.persistence",
    ]
)
@EnableScheduling
class PaymentReferenceApplication

fun main(args: Array<String>) {
    runApplication<PaymentReferenceApplication>(*args)
}
