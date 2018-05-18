/** Copyright 2012 University of Washington. All Rights Reserved.
 *  @author Sai Zhang, Reed Oei
 */
package edu.washington.cs.dt.util;

import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * This class is in too low level. I made it package-visible
 * on purpose.
 * */
class JUnitTestExecutor {
    private static final PrintStream EMPTY_STREAM = new PrintStream(new OutputStream() {
        public void write(int b) {
            //DO NOTHING
        }
    });

	private final List<JUnitTest> testOrder = new ArrayList<>();
    private final Map<String, JUnitTest> testMap = new HashMap<>();
	private final Set<Class<?>> allClasses = new HashSet<>();
	private final Set<JUnitTestResult> missingTests = new HashSet<>();

    public JUnitTestExecutor(final JUnitTest test) {
        this(Collections.singletonList(test));
    }

    public JUnitTestExecutor(final List<JUnitTest> tests) {
        this(tests, new HashSet<JUnitTestResult>());
    }

    public JUnitTestExecutor(final List<JUnitTest> tests, final Set<JUnitTestResult> missingTests) {
        for (final JUnitTest test : tests) {
            if (test.isClassCompatible()) {
                testOrder.add(test);
                allClasses.add(test.testClass());
                testMap.put(test.name(), test);
            } else {
                System.out.println("  Detected incompatible test case with RunWith annotation.");
            }
        }

        this.missingTests.addAll(missingTests);
    }

    //package.class.method
    public static JUnitTestExecutor singleton(final String fullMethodName) throws ClassNotFoundException {
        return new JUnitTestExecutor(new JUnitTest(fullMethodName));
    }

    public static JUnitTestExecutor singleton(String className, String junitMethod) {
        return new JUnitTestExecutor(new JUnitTest(className, junitMethod));
    }

    public static JUnitTestExecutor singleton(Class<?> junitTest, String junitMethod) {
        return new JUnitTestExecutor(new JUnitTest(junitTest, junitMethod));
    }

    public static JUnitTestExecutor skipMissing(final List<String> testOrder) {
        final List<JUnitTest> tests = new ArrayList<>();
        final Set<JUnitTestResult> missingTests = new HashSet<>();

        for (int i = 0; i < testOrder.size(); i++) {
            final String fullMethodName = testOrder.get(i);
            try {
                final JUnitTest test = new JUnitTest(fullMethodName, i);

                tests.add(test);
            } catch (ClassNotFoundException e) {
                missingTests.add(JUnitTestResult.missing(fullMethodName));
                System.out.println("  Skipped missing test : " + fullMethodName);
            }
        }

        return new JUnitTestExecutor(tests, missingTests);
    }

    public static JUnitTestExecutor testOrder(final List<String> testOrder) throws ClassNotFoundException {
        final List<JUnitTest> tests = new ArrayList<>();

        for (int i = 0; i < testOrder.size(); i++) {
            final String fullMethodName = testOrder.get(i);
            tests.add(new JUnitTest(fullMethodName, i));
        }

        return new JUnitTestExecutor(tests);
    }

    public Set<JUnitTestResult> executeWithJUnit4Runner(final boolean skipIncompatible) {
        final JUnitCore core = new JUnitCore();

        final Request r = Request.classes(allClasses.toArray(new Class<?>[0]))
                .filterWith(new TestOrderFilter())
                .sortWith(new TestOrderComparator());

        final PrintStream currOut = System.out;
		final PrintStream currErr = System.err;

		System.setOut(EMPTY_STREAM);
		System.setErr(EMPTY_STREAM);

		final Map<String, Long> testRuntimes = new HashMap<>();
		core.addListener(new TestTimeListener(testRuntimes));

		System.setOut(currOut);
		System.setErr(currErr);

		final Set<JUnitTestResult> results = new HashSet<>(missingTests);
		final Map<String, JUnitTest> passingTests = new HashMap<>(testMap);

        final Result re = core.run(r);
		for (final Failure failure : re.getFailures()) {
		    // If the description is a test (that is, a single test), then handle it normally.
            // Otherwise, the ENTIRE class failed during initialization or some such thing.
		    if (failure.getDescription().isTest()) {
                final String fullTestName = TestExecUtils.fullName(failure.getDescription());

                if (!testRuntimes.containsKey(fullTestName)) {
                    System.out.println("[ERROR]: No running time measured for test name '" + fullTestName + "'");
                    continue;
                }

                if (!passingTests.containsKey(fullTestName)) {
                    System.out.println("[ERROR]: No JUnitTest found for test name '" + fullTestName + "'");
                    continue;
                }

                results.add(JUnitTestResult.failOrError(failure,
                        testRuntimes.get(fullTestName),
                        passingTests.get(fullTestName)));
                passingTests.remove(fullTestName);
            } else {
		        // The ENTIRE class failed, so we need to mark every test from this class as failing.
                final String className = failure.getDescription().getClassName();

                // Make a set first so that we can modify the original hash map
                final Set<JUnitTest> tests = new HashSet<>(passingTests.values());
                for (final JUnitTest test : tests) {
                    if (test.testClass().getCanonicalName().equals(className)) {
                        results.add(JUnitTestResult.failOrError(failure, 0, test));
                        passingTests.remove(test.name());
                    }
                }
            }
        }

        for (final String fullMethodName : passingTests.keySet()) {
            if (!testRuntimes.containsKey(fullMethodName)) {
                System.out.println("[ERROR]: No running time measured for test name '" + fullMethodName + "'");
                continue;
            }

            if (!passingTests.containsKey(fullMethodName)) {
                System.out.println("[ERROR]: No JUnitTest found for test name '" + fullMethodName + "'");
                continue;
            }

            results.add(JUnitTestResult.passing(testRuntimes.get(fullMethodName), passingTests.get(fullMethodName)));
        }

        return results;
	}

    private static class TestTimeListener extends RunListener {
        private final Map<String, Long> times;
        private final Map<String, Long> testRuntimes;

        public TestTimeListener(Map<String, Long> testRuntimes) {
            this.testRuntimes = testRuntimes;
            times = new HashMap<>();
        }

        @Override
        public void testStarted(Description description) throws Exception {
            System.out.println("Test being executed: " + TestExecUtils.fullName(description));
            times.put(TestExecUtils.fullName(description), System.nanoTime());
        }

        @Override
        public void testFinished(Description description) throws Exception {
            final String fullTestName = TestExecUtils.fullName(description);

            if (times.containsKey(fullTestName)) {
                final long startTime = times.get(fullTestName);
                testRuntimes.put(fullTestName, System.nanoTime() - startTime);
                times.remove(fullTestName);
            } else {
                System.out.println("Test finished but did not start: " + fullTestName);
            }
        }
    }

    private class TestOrderFilter extends Filter {
        @Override
        public boolean shouldRun(final Description description) {
            for (final JUnitTest test : testOrder) {
                if (Filter.matchMethodDescription(test.description()).shouldRun(description)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public String describe() {
            // Return the order we are running in.
            final List<String> testNames = new ArrayList<>();
            for (final JUnitTest test : testOrder) {
                testNames.add(test.name());
            }

            return testNames.toString();
        }
    }

    private class TestOrderComparator implements Comparator<Description> {
        @Override
        public int compare(Description a, Description b) {
            if (testMap.containsKey(TestExecUtils.fullName(a))) {
                if (testMap.containsKey(TestExecUtils.fullName(b))) {
                    return Integer.compare(
                            testMap.get(TestExecUtils.fullName(a)).index(),
                            testMap.get(TestExecUtils.fullName(b)).index());
                }
            }

            return 0;
        }
    }
}
