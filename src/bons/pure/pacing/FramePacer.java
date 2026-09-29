package bons.pure.pacing;
/** Runtime switch for qualification and compatibility; no FPS target is imposed. */
public final class FramePacer {
 private static volatile boolean enabled=Boolean.parseBoolean(System.getProperty("bons_and_furious.framePacing","true"));
 private FramePacer(){}
 public static boolean isEnabled(){return enabled;}
 public static void setEnabled(boolean value){enabled=value;}
}
