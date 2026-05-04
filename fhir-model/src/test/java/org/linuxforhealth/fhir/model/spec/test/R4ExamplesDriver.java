/**
 * (C) Copyright IBM Corp. 2019, 2022
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.linuxforhealth.fhir.model.spec.test;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.linuxforhealth.fhir.examples.ExamplesUtil;
import org.linuxforhealth.fhir.examples.Index;
import org.linuxforhealth.fhir.model.format.Format;
import org.linuxforhealth.fhir.model.parser.FHIRParser;
import org.linuxforhealth.fhir.model.parser.exception.FHIRParserException;
import org.linuxforhealth.fhir.model.resource.Resource;

/**
 * Run through all the examples from the R4 specification
 */
public class R4ExamplesDriver {
    private static final Logger logger = Logger.getLogger(R4ExamplesDriver.class.getName());

    // Call this processor for each of the examples, if given
    private IExampleProcessor processor;

    // Validate the resource
    private IExampleProcessor validator;

    // track some simple metrics
    private AtomicInteger testCount = new AtomicInteger();
    private AtomicInteger successCount = new AtomicInteger();
    private Exception firstException = null;

    // Optional pool if we want to process the examples in parallel
    private ExecutorService pool;

    // Limit the number of requests we submit to the pool
    private int maxInflight;
    private Lock lock = new ReentrantLock();
    private Condition runningCondition = lock.newCondition();
    private Condition inflightCondition = lock.newCondition();

    // The number of requests submitted but not yet completed (queued + running)
    private int currentlySubmittedCount;

    // optional metrics collection
    private DriverMetrics metrics;

    /**
     * Setter for the processor
     *
     * @param p
     */
    public void setProcessor(IExampleProcessor p) {
        this.processor = p;
    }

    /**
     * Setter for the metrics object
     * @param metrics
     */
    public void setMetrics(DriverMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Setter for the validation processor
     *
     * @param p
     */
    public void setValidator(IExampleProcessor p) {
        this.validator = p;
    }

    /**
     * Setter for the thread-pool. Used only if processing the examples in
     * parallel
     * @param pool the threadpool to use
     * @param maxInflight the maximum number of requests submitted to the pool before blocking
     */
    public void setPool(ExecutorService pool, int maxInflight) {
        this.pool = pool;
        this.maxInflight = maxInflight;
    }

    /**
     * Process all examples referenced from the index file.
     *
     * @throws Exception
     */
    public void processIndex(Index index) throws Exception {
        logger.info(String.format("Processing index '%s'", index));
        // reset the state just in case we are called more than once
        this.firstException = null;
        this.testCount.set(0);
        this.successCount.set(0);

        long start = System.nanoTime();

        List<ExampleProcessorException> errors = new ArrayList<>();
        try {
            // Each line of the index file should be a path to an example resource and an expected outcome
            try (BufferedReader br = new BufferedReader(ExamplesUtil.indexReader(index))) {
                String line;

                while ((line = br.readLine()) != null) {
                    String[] tokens = line.split("\\s+");
                    if (tokens.length == 2) {
                        String expectation = tokens[0];
                        String example = tokens[1];
                        if (example.toUpperCase().endsWith(".JSON")) {
                            testCount.incrementAndGet();
                            Expectation exp = Expectation.valueOf(expectation);
                            submitExample(errors, example, Format.JSON, exp);
                        }
                        else if (example.toUpperCase().endsWith(".XML")) {
                            testCount.incrementAndGet();
                            Expectation exp = Expectation.valueOf(expectation);
                            submitExample(errors, example, Format.XML, exp);
                        }
                        else {
                            logger.warning("Unable to infer format from '" + example + "'; example files must end in .json or .xml");
                        }
                    }
                }
            }

            // If we are running with a thread-pool, then we must wait for everything to complete
            if (pool != null) {
                waitForCompletion();
            }

            // propagate the first exception so we fail the test
            if (firstException != null) {
                throw firstException;
            }
        }
        finally {
            if (testCount.get() > 0) {
                long elapsed = (System.nanoTime() - start) / 1000000;

                // We count overall success if we successfully process the resource,
                // or if we got an expected exception earlier on
                logger.info("Overall success rate = " + successCount + "/" + testCount + " = "
                        + (100*successCount.get() / testCount.get()) + "%. Took " + elapsed + " ms");
            }

            // We can access errors here safely because waitForCompletion called lock/unlock in this thread (in case you were wondering)
            for (ExampleProcessorException error : errors) {
                logger.warning(error.toString());
            }
        }
    }

    public void processExample(String file, Expectation expectation) throws ExampleProcessorException {
        processExample(file, Format.JSON, expectation);
    }

    /**
     * Submit the given example for processing
     * @param errors the list of errors to accumulate
     * @param file
     * @param format
     * @param expectation
     * @throws ExampleProcessorException
     */
    public void submitExample(List<ExampleProcessorException> errors, String file, Format format, Expectation expectation) throws ExampleProcessorException {
        if (pool == null) {
            // run in-line
            try {
                processExample(file, format, expectation);
            } catch (ExampleProcessorException e) {
                errors.add(e);
            }
            return;
        }

        // Otherwise:
        // Wait until we have capacity. We do this to throttle the number of requests
        // submitted to pool, hopefully avoiding memory issues if ever we have a really
        // large index to process
        lock.lock();
        while (this.currentlySubmittedCount == this.maxInflight) {
            try {
                this.inflightCondition.await(1000, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException x) {
                // NOP
            }
        }
        currentlySubmittedCount++;
        lock.unlock();

        pool.execute(new Runnable() {

            @Override
            public void run() {
                try {
                    processExample(file, format, expectation);
                } catch (ExampleProcessorException e) {
                    lock.lock();
                    if (firstException == null) {
                        firstException = e;
                    }
                    errors.add(e);
                    lock.unlock();
                }
                finally {
                    lock.lock();
                    int oldCount = currentlySubmittedCount--;
                    if (oldCount == maxInflight) {
                        inflightCondition.signal();
                    }

                    if (currentlySubmittedCount == 0) {
                        runningCondition.signal();
                    }
                    lock.unlock();
                }
            }
        });
    }

    /**
     * Process the example file. If jsonFile is prefixed with "file:" then the file will be read from the filesystem,
     * otherwise it will be treated as a resource on the classpath.
     *
     * @param file
     * @param format
     * @param expectation
     * @throws ExampleProcessorException
     */
    public void processExample(String file, Format format, Expectation expectation)
            throws ExampleProcessorException {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Processing: " + file);
        } else {
            System.out.print("."); // So we know it's not stalled
        }
        Expectation actual;

        try {
            Resource resource = readResource(file, format);
            if (resource == null) {
                // this is bad, because we'd expect a FHIRParserException
                throw new AssertionError("readResource(" + file + ") returned null");
            }

            if (expectation == Expectation.PARSE) {
                // If we parsed the resource successfully but expected it to fail, it's a failed
                // test, so we don't try and process it any further
                actual = Expectation.OK;
                throw new ExampleProcessorException(file, expectation, actual);
            } else {
                // validate and process the example
                actual = processExample(file, resource, expectation);
            }
        } catch (ExampleProcessorException e) {
            if (firstException == null) {
                firstException = e;
            }
            throw e;
        } catch (FHIRParserException fpx) {
            actual = Expectation.PARSE;
            if (expectation == Expectation.PARSE) {
                // successful test, even though we won't be able to validate/process
                successCount.incrementAndGet();
            } else {
                // oops, hit an unexpected parse error
                System.out.println();
                logger.severe("readResource(" + file + ") unexpected failure: " + fpx.getMessage()
                        + ", " + fpx.getPath());

                // continue processing the other files, but capture the first exception so we can fail the test
                // if needed
                ExampleProcessorException error =
                        new ExampleProcessorException(file, expectation, actual, fpx);
                if (firstException == null) {
                    firstException = fpx;
                }
                throw error;
            }
        } catch (Exception e) {
            // continue processing the other files, but capture the first exception so we can fail the test
            // if needed
            ExampleProcessorException error =
                    new ExampleProcessorException(file, expectation, Expectation.PARSE, e);
            if (firstException == null) {
                firstException = e;
            }
            throw error;
        }

        if (actual != expectation) {
            // continue processing the other files, but capture the first exception so we can fail the test
            // if needed
            ExampleProcessorException error = new ExampleProcessorException(file, expectation, actual);
            if (firstException == null) {
                firstException = error;
            }
            throw error;
        } else {
            // another successful test
            successCount.incrementAndGet();
        }
    }

    /**
     * Read the resource from the file
     * @param file
     * @param format
     * @return
     * @throws FHIRParserException
     */
    private Resource readResource(String file, Format format) throws FHIRParserException {
        try (Reader reader = ExamplesUtil.resourceReader(file)) {
            return FHIRParser.parser(format).parse(reader);
        } catch (FHIRParserException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Unhandled exception for file '" + file + "'", e);
        }
    }

    /**
     * Process the example after it has been parsed
     * @param file
     * @param resource
     * @param expectation
     * @return
     */
    private Expectation processExample(String file, Resource resource, Expectation expectation) {
        Expectation result = Expectation.OK;

        if (validator != null) {
            try {
                validator.process(file, resource);
            } catch (Exception e) {
                result = Expectation.VALIDATION;
            }
        }

        if (result == Expectation.OK && processor != null) {
            try {
                processor.process(file, resource);
            } catch (Exception e) {
                result = Expectation.PROCESS;
            }
        }

        return result;
    }

    /**
     * Wait for all the submitted tasks to complete
     */
    public void waitForCompletion() {
        lock.lock();
        while (this.currentlySubmittedCount > 0) {
            try {
                this.runningCondition.await(1000, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException x) {
                // NOP
            }
        }
        lock.unlock();
    }
}
