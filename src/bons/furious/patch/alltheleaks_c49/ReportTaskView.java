package bons.furious.patch.alltheleaks_c49;

/**
 * alltheleaks_unread_summary_skip: read access to an AllTheLeaks ReportManager$ReportTask's schedule (implemented on that
 * package-private class by bons.furious.mixin.alltheleaks_c49.ReportTaskMixin). Reads only.
 */
public interface ReportTaskView {
    String bons$atlName();

    int bons$atlInterval();

    int bons$atlLastRun();

    boolean bons$atlStopped();
}
