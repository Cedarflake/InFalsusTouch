package dev.cedarflake.ift.video

internal data class CodecOutput(val index: Int, val presentationUs: Long)

internal class CodecBufferEvents(private val wake: () -> Unit) {
  private val inputs = ArrayDeque<Int>()
  private val outputs = ArrayDeque<CodecOutput>()
  private var accepting = true
  private var closed = false
  private var failure: Exception? = null

  @Synchronized fun input(index: Int) {
    if (!accepting) return
    inputs.addLast(index)
    wake()
  }

  @Synchronized fun output(index: Int, presentationUs: Long) {
    if (!accepting) return
    outputs.addLast(CodecOutput(index, presentationUs))
    wake()
  }

  @Synchronized fun failed(error: Exception) {
    if (closed) return
    if (failure == null) failure = error
    wake()
  }

  @Synchronized fun checkFailure() { failure?.let { throw it } }
  // Error callbacks can invalidate direct codec buffers while the worker is copying input.
  @Synchronized fun <T> useBuffer(action: () -> T): T {
    checkFailure()
    check(!closed) { "Decoder session closed" }
    return action()
  }
  @Synchronized fun takeInput(): Int? = inputs.removeFirstOrNull()
  @Synchronized fun takeOutput(): CodecOutput? = outputs.removeFirstOrNull()

  @Synchronized fun suspendCallbacks() {
    accepting = false
    inputs.clear()
    outputs.clear()
  }

  @Synchronized fun resumeCallbacks() {
    if (!closed) accepting = true
  }

  @Synchronized fun close() {
    closed = true
    suspendCallbacks()
  }
}
