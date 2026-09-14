package envelope;

import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import org.junit.platform.engine.discovery.DiscoverySelectors;

/**
 * Minimal JUnit Platform runner: executes named test classes, prints a result
 * summary as JSON, exits nonzero on any failure. Lets fault tests run offline
 * without Maven (all engine jars are cached in ~/.m2).
 */
public class JUnitRunner {
    public static void main(String[] args) {
        var selectors = new java.util.ArrayList<org.junit.platform.engine.DiscoverySelector>();
        for (String cls : args) selectors.add(DiscoverySelectors.selectClass(cls));
        var request = LauncherDiscoveryRequestBuilder.request().selectors(selectors).build();
        var launcher = LauncherFactory.create();
        var listener = new SummaryGeneratingListener();
        launcher.execute(request, listener);
        var s = listener.getSummary();
        long ok = s.getTestsSucceededCount(), fail = s.getTestsFailedCount() + s.getTestsAbortedCount() + s.getTestsSkippedCount();
        StringBuilder out = new StringBuilder("{\"tests\":[");
        boolean first = true;
        for (var f : s.getFailures()) {
            if (!first) out.append(",");
            first = false;
            String msg = String.valueOf(f.getException()).replace("\\", "/").replace("\"", "'");
            out.append("{\"test\":\"").append(f.getTestIdentifier().getUniqueId()).append("\",\"status\":\"FAIL\",\"cause\":\"").append(msg.length() > 300 ? msg.substring(0, 300) : msg).append("\"}");
        }
        out.append("],\"passed\":").append(ok).append(",\"failed\":").append(fail).append("}");
        System.out.println(out);
        if (fail > 0 || ok == 0) System.exit(1);
    }
}
