package org.brightify.hyperdrive

import co.touchlab.kermit.BaseLogger
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.LoggerConfig
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import org.brightify.hyperdrive.utils.AtomicReference
import kotlin.reflect.KClass

public class Logger private constructor(
    @PublishedApi
    internal val tag: String,
) {
    public inline fun verbose(throwable: Throwable? = null, crossinline entryBuilder: () -> String) {
        logIfEnabled(Severity.Verbose, throwable, entryBuilder)
    }

    public inline fun debug(throwable: Throwable? = null, crossinline entryBuilder: () -> String) {
        logIfEnabled(Severity.Debug, throwable, entryBuilder)
    }

    public inline fun info(throwable: Throwable? = null, crossinline entryBuilder: () -> String) {
        logIfEnabled(Severity.Info, throwable, entryBuilder)
    }

    public inline fun warning(throwable: Throwable? = null, crossinline entryBuilder: () -> String) {
        logIfEnabled(Severity.Warn, throwable, entryBuilder)
    }

    public inline fun error(throwable: Throwable? = null, crossinline entryBuilder: () -> String) {
        logIfEnabled(Severity.Error, throwable, entryBuilder)
    }

    public inline fun logIfEnabled(severity: Severity, throwable: Throwable? = null, crossinline entryBuilder: () -> String) {
        kermitLogger.logBlock(severity, tag, throwable, entryBuilder)
    }

    public class Configuration(
        public override val minSeverity: Severity,
        public override val logWriterList: List<LogWriter>,
    ): LoggerConfig {
        public class Builder {
            private var minSeverity: Severity = Severity.Warn
            private val logWriters = mutableListOf<LogWriter>()

            public fun setMinSeverity(severity: Severity): Builder {
                minSeverity = severity
                return this
            }

            public fun addLogWriter(logWriter: LogWriter): Builder {
                logWriters.add(logWriter)
                return this
            }

            public fun clearLogWriters(): Builder {
                logWriters.clear()
                return this
            }

            public fun build(): Configuration = Configuration(
                minSeverity = minSeverity,
                logWriterList = logWriters.toList(),
            )
        }
    }

    public companion object {
        private object KermitLoggerConfig: LoggerConfig {
            override val logWriterList: List<LogWriter>
                get() = configuration.logWriterList
            override val minSeverity: Severity
                get() = configuration.minSeverity
        }

        private val configurationReference = AtomicReference(Configuration.Builder().addLogWriter(platformLogWriter()).build())
        private val mainLogger = Logger("h.t.Logger")


        @PublishedApi
        internal val kermitLogger: BaseLogger = BaseLogger(KermitLoggerConfig)

        public val configuration: Configuration
            get() = configurationReference.value

        public fun configure(block: Configuration.Builder.() -> Unit) {
            val builder = Configuration.Builder()
            block(builder)
            configurationReference.value = builder.build()
        }

        public inline operator fun <reified T: Any> invoke(): Logger {
            return Logger(T::class)
        }

        public operator fun <T: Any> invoke(kclass: KClass<T>): Logger {
            val name = kclass.simpleName ?: run {
                mainLogger.error { "Couldn't get `simpleName` of class <$kclass> for a new logger. Using `toString` as name." }
                kclass.toString()
            }
            return Logger(name)
        }

        public operator fun invoke(tag: String): Logger {
            return Logger(tag)
        }
    }
}
