package com.only4.cap4k.reference.payment.adapter.reference

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/** Makes the fixture's logical clock the one injected into all reference business handlers. */
@Configuration(proxyBeanMethods = false)
class ReferenceFixtureInfrastructureConfiguration {
    @Bean
    @Primary
    fun referenceLogicalClock(): ReferenceLogicalClock = ReferenceLogicalClock()
}
