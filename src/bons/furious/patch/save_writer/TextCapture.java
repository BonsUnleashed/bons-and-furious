package bons.furious.patch.save_writer;

import java.io.Writer;
import java.util.function.Consumer;

/**
 * Bons and Furious switch vanilla_background_saves: the Writer under the BufferedWriter that PlayerAdvancements.save gets
 * instead of the file's writer. It collects the characters Gson writes (through the BufferedWriter's 8192-char buffer)
 * and hands the complete text to the writer thread when the BufferedWriter is closed, which Minecraft's
 * try-with-resources does also when Gson throws (the file then gets the text written so far, as in Minecraft).
 * Server thread only.
 */
final class TextCapture extends Writer {
    private final StringBuilder text = new StringBuilder(8192);
    private final Consumer<String> onClose;
    private boolean closed;

    TextCapture(Consumer<String> onClose) {
        this.onClose = onClose;
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        this.text.append(cbuf, off, len);
    }

    @Override
    public void write(String str, int off, int len) {
        this.text.append(str, off, off + len);
    }

    @Override
    public void write(int c) {
        this.text.append((char) c);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.onClose.accept(this.text.toString());
    }
}
