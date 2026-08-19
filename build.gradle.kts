plugins {
    id("io.github.ldmoxeii.cap4k.pipeline")
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
}

val schemaScriptPath = layout.projectDirectory.file("design/schema.sql").asFile.absolutePath.replace("\\", "/")
val dbFilePath = layout.buildDirectory.file("h2/payment-design").get().asFile.absolutePath.replace("\\", "/")

cap4k {
    project {
        basePackage.set("com.only4.cap4k.reference.payment")
        contractModulePath.set("contract")
        domainModulePath.set("domain")
        applicationModulePath.set("application")
        adapterModulePath.set("adapter")
    }
    types {
        valueObjectManifest {
            files.from("design/value-objects.json")
        }
        enumManifest {
            files.from("design/enums.json")
        }
    }
    sources {
        designJson {
            files.from("design/design.json")
        }
        irAnalysis {
            inputDirs.from(
                project(":contract").layout.buildDirectory.dir("cap4k-code-analysis"),
                project(":domain").layout.buildDirectory.dir("cap4k-code-analysis"),
                project(":application").layout.buildDirectory.dir("cap4k-code-analysis"),
                project(":adapter").layout.buildDirectory.dir("cap4k-code-analysis"),
            )
        }
        db {
            enabled.set(true)
            url.set(
                "jdbc:h2:file:$dbFilePath;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;INIT=RUNSCRIPT FROM '$schemaScriptPath'"
            )
            username.set("sa")
            password.set("secret")
            schema.set("PUBLIC")
            includeTables.set(listOf("payment", "payment_attempt", "payment_notification_receipt", "refund", "refund_attempt", "refund_notification_receipt", "merchant_channel_configuration"))
            excludeTables.set(emptyList())
        }
    }
    managedFields {
        identifierDefaultPolicy.set("identifier.uuid7")
    }
    generators {
        aggregate {
            unsupportedTablePolicy.set("FAIL")
        }
        flow { }
        drawingBoard { }
    }
}
