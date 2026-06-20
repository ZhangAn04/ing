package it.polimi.Network.Client;

/**
 * A simple launcher class to bypass the JavaFX module path check.
 * <p>
 * Since Java 11, JavaFX is no longer bundled with the JDK. When running a class
 * that extends {@code javafx.application.Application} directly, the JVM expects
 * the JavaFX modules to be explicitly added to the module path. By using this
 * separate launcher class that does not extend {@code Application}, we trick the
 * JVM into loading JavaFX from the classpath (managed by Maven) without errors.
 * </p>
 */
public class Launcher {

    /** Creates a JavaFX launcher entry point. */
    public Launcher() {
    }
    /**
     * The main entry point that delegates to the JavaFX application.
     *
     * @param args Command-line arguments.
     */
    public static void main(String[] args) {
        ClientGuiMain.main(args);
    }
}
