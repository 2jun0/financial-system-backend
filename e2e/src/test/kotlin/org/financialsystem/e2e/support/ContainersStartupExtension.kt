package org.financialsystem.e2e.support

import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File
import java.time.Duration

class ContainersStartupExtension : BeforeAllCallback {
    override fun beforeAll(context: ExtensionContext) {
        val store =
            context.root.getStore(
                ExtensionContext.Namespace.create(ContainersStartupExtension::class.java),
            )

        store.computeIfAbsent(
            "compose",
            { ComposeResource().apply { start() } },
            ComposeResource::class.java,
        )
    }

    class ComposeResource : AutoCloseable {
        val compose: ComposeContainer =
            ComposeContainer("financial-e2e-", File("../docker/docker-compose-dev.yaml"))
                .withEnv("COMPOSE_PROFILES", "all")
                .withPull(false)
                .withBuild(true)
                .waitingFor("bank-a-backend", applicationStarted())
                .waitingFor("bank-b-backend", applicationStarted())
                .waitingFor("settlement-backend", applicationStarted())

        override fun close() {
            compose.stop()
        }

        fun start() {
            compose.start()
        }

        private fun applicationStarted() =
            Wait
                .forLogMessage(".*Started .* in .* seconds.*\\n", 1)
                .withStartupTimeout(Duration.ofMinutes(2))
    }
}
