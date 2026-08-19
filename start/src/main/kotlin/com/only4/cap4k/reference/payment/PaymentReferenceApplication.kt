package com.only4.cap4k.reference.payment

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PaymentReferenceApplication

fun main(args: Array<String>) {
    runApplication<PaymentReferenceApplication>(*args)
}
