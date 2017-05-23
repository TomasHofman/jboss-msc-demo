package org.jboss.set.msc.demo;

import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceContainer;
import org.junit.After;
import org.junit.Before;

/**
 * (Copied and pruned from AbstractServiceTest in jboss-msc testsuite.)
 *
 * @author Tomas Hofman (thofman@redhat.com)
 */
public class AbstractServiceContainerTest {

    protected Logger logger = Logger.getLogger(getClass());

    protected volatile ServiceContainer serviceContainer;
    private boolean shutdownOnTearDown;

    @Before
    public void setUp() throws Exception {
        logger.info("Setting up test " + getClass());
        serviceContainer = ServiceContainer.Factory.create();
        shutdownOnTearDown = true;
    }

    @After
    public void tearDown() throws Exception {
        logger.info("Tearing down test " + getClass());
        if (shutdownOnTearDown) {
            serviceContainer.shutdown();
            serviceContainer.awaitTermination();
        }
        serviceContainer = null;
    }

}
