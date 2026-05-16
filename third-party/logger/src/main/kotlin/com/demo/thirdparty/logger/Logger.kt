package com.demo.thirdparty.logger

/**
 * Minimal pluggable logger that stands in for a real third-party SDK
 * (Timber, Logback, etc.) in this demo.
 *
 * Lives in `:third-party:*` so any module — including foundations — can
 * depend on it. Must NOT depend on any business module.
 */
object Logger {

    enum class Level { VERBOSE, DEBUG, INFO, WARN, ERROR }

    fun interface Sink {
        fun write(level: Level, tag: String, message: String)
    }

    @Volatile
    private var sink: Sink = Sink { level, tag, message ->
        println("[$level] $tag: $message")
    }

    fun install(sink: Sink) {
        this.sink = sink
    }

    fun v(tag: String, msg: String) = sink.write(Level.VERBOSE, tag, msg)
    fun d(tag: String, msg: String) = sink.write(Level.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = sink.write(Level.INFO, tag, msg)
    fun w(tag: String, msg: String) = sink.write(Level.WARN, tag, msg)
    fun e(tag: String, msg: String) = sink.write(Level.ERROR, tag, msg)
}
