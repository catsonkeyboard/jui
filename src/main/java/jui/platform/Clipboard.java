package jui.platform;

/** The system clipboard's text, on the main thread. */
public interface Clipboard {
    /** The clipboard's text, or null when it holds none. */
    String read();

    void write(String s);
}
