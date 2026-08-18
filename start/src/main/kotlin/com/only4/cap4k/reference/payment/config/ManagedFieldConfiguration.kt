package com.only4.cap4k.reference.payment.config

import com.only4.cap4k.ddd.application.JpaAggregateChange
import com.only4.cap4k.ddd.application.JpaManagedOperation
import com.only4.cap4k.ddd.application.JpaManagedFieldSet
import com.only4.cap4k.ddd.application.JpaPersistenceEnricher
import com.only4.cap4k.ddd.application.JpaPersistenceEnrichmentContext
import com.only4.cap4k.ddd.core.domain.managed.ManagedValueAdapter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.reflect.KClass

@Configuration(proxyBeanMethods = false)
class ManagedFieldConfiguration {
    @Bean
    fun auditTimeManagedValueAdapter(): ManagedValueAdapter = object : ManagedValueAdapter {
        override val qualifier: String = "enrichment.audit-time"
        override val sourceType: KClass<*> = Instant::class

        override fun supports(targetType: KClass<*>): Boolean = targetType == LocalDateTime::class

        override fun adapt(value: Any, targetType: KClass<*>): Any =
            LocalDateTime.ofInstant(value as Instant, ZoneOffset.UTC)
    }

    @Bean
    fun auditActorPersistenceEnricher(): JpaPersistenceEnricher = object : JpaPersistenceEnricher {
        override val qualifiers: Set<String> = setOf("enrichment.audit-actor")

        override fun enrich(
            change: JpaAggregateChange,
            context: JpaPersistenceEnrichmentContext,
            fields: JpaManagedFieldSet,
        ) {
            fields.forEach { entityFields ->
                entityFields.handles.forEach { handle ->
                    when (handle.handlerSlot) {
                        "created-by" -> check(entityFields.operation == JpaManagedOperation.CREATE) {
                            "audit created-by handle must only participate in CREATE"
                        }
                        "updated-by" -> Unit
                        else -> error("unsupported audit-actor slot '${handle.handlerSlot}' for ${handle.policyKey}")
                    }
                    handle.assignSemantic(ACTOR)
                }
            }
        }
    }

    private companion object {
        const val ACTOR = "payment-reference-system"
    }
}
