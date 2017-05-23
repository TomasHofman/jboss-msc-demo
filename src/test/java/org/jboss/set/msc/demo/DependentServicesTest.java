package org.jboss.set.msc.demo;

import static org.jboss.msc.service.ServiceController.Mode.NEVER;
import static org.jboss.msc.service.ServiceController.State.DOWN;
import static org.jboss.msc.service.ServiceController.State.REMOVED;
import static org.jboss.msc.service.ServiceController.State.UP;
import static org.jboss.msc.service.ServiceController.Substate.PROBLEM;

import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.CircularDependencyException;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Tomas Hofman (thofman@redhat.com)
 */
public class DependentServicesTest extends AbstractServiceContainerTest {

    private static final ServiceName firstServiceName = ServiceName.of("firstService");
    private static final ServiceName secondServiceName = ServiceName.of("secondService");
    private static final ServiceName thirdServiceName = ServiceName.of("thirdService");
    private ServiceListener listener;
    private CountDownLatch latch = new CountDownLatch(1);

    @Before
    public void setUpTestListener() {
        listener = new LoggingServiceListener();
        serviceContainer.addListener(listener);
    }

    /**
     * Service dependencies
     *
     * When dependency service is brought down, dependency service is brought down too.
     */
    @Test
    public void testDependentServices() throws InterruptedException {
        // install two dependent services
        ServiceController<Void> firstService = serviceContainer.addService(firstServiceName, Service.NULL).install();
        ServiceController<Void> secondService = serviceContainer.addService(secondServiceName, Service.NULL)
                .addDependency(firstServiceName)
                .install();

        // wait for all transitions to complete
        serviceContainer.awaitStability();
        logger.infof("Stability reached.");
        Assert.assertEquals(UP, firstService.getState());
        Assert.assertEquals(UP, secondService.getState());

        // remove dependency service, and when it's removed, install replacement for it
        firstService.addListener(new AbstractServiceListener<Object>() {
            @Override
            public void transition(ServiceController<?> controller, ServiceController.Transition transition) {
                if (transition.enters(ServiceController.State.REMOVED)) {
                    // now can a new "firstService" be installed
                    latch.countDown();
                }
            }
        });
        firstService.setMode(ServiceController.Mode.REMOVE);

        // wait for the dependency service to be removed
        latch.await();
        HashSet<Object> failed = new HashSet<>();
        HashSet<Object> problem = new HashSet<>();
        serviceContainer.awaitStability(failed, problem); // redundant since the latch was used
        Assert.assertEquals(ServiceController.State.REMOVED, firstService.getState());
        Assert.assertEquals(ServiceController.State.DOWN, secondService.getState());
        Assert.assertEquals(PROBLEM, secondService.getSubstate());
        Assert.assertEquals(1, problem.size());

        // install new dependency service
        ServiceController<Void> newFirstService = serviceContainer.addService(firstServiceName, Service.NULL).install();

        // wait for the installation to complete
        serviceContainer.awaitStability();
        logger.infof("Stability reached.");
        Assert.assertEquals(ServiceController.State.REMOVED, firstService.getState());
        Assert.assertEquals(UP, newFirstService.getState());
        Assert.assertEquals(UP, secondService.getState());
    }

    /**
     * Optional service dependencies
     *
     * When dependency service is brought down, dependent service is restarted and goes UP again.
     *
     * Optional dependencies are deprecated.
     */
    @Test
    public void testOptionallyDependentServices() throws InterruptedException {
        // install two dependent services, dependency is optional
        ServiceController<Void> firstService = serviceContainer.addService(firstServiceName, Service.NULL).install();
        ServiceController<Void> secondService = serviceContainer.addService(secondServiceName, Service.NULL)
                .addDependency(ServiceBuilder.DependencyType.OPTIONAL, firstServiceName)
                .install();

        // wait for all transitions to complete
        serviceContainer.awaitStability();
        logger.infof("Stability reached.");
        Assert.assertEquals(UP, firstService.getState());
        Assert.assertEquals(UP, secondService.getState());

        // add a listener that monitors whether dependent service went down
        AtomicBoolean secondRestarted = new AtomicBoolean(false);
        secondService.addListener(new AbstractServiceListener<Void>() {
            @Override
            public void transition(ServiceController<? extends Void> controller, ServiceController.Transition transition) {
                if (transition.enters(ServiceController.State.DOWN)) {
                    secondRestarted.set(true);
                }
            }
        });
        
        // remove the dependency service
        firstService.setMode(ServiceController.Mode.REMOVE);

        // wait for the removal to complete
        serviceContainer.awaitStability();
        logger.infof("Stability reached.");
        Assert.assertEquals(ServiceController.State.REMOVED, firstService.getState()); // dependency service was removed
        Assert.assertTrue(secondRestarted.get()); // dependent service should have been restarted
        Assert.assertEquals(UP, secondService.getState()); // dependent service should be UP again
    }

    /**
     * Child services
     *
     * When parent service goes down, child service is removed.
     */
    @Test
    public void testChildServices() throws InterruptedException {
        // install parent service
        final ServiceControllerValue<Void> childServiceValue = new ServiceControllerValue<>();
        ServiceController<Void> parentService = serviceContainer.addService(firstServiceName, new AbstractService<Void>() {
            @Override
            public void start(StartContext context) throws StartException {
                // install child service
                childServiceValue.setValue(context.getChildTarget().addService(secondServiceName, Service.NULL).install());
            }
        }).install();
        serviceContainer.awaitStability();

        // both services are expected to be UP
        Assert.assertEquals(UP, parentService.getState());
        Assert.assertEquals(UP, childServiceValue.getValue().getState());

        // bring child service down
        childServiceValue.getValue().setMode(NEVER);
        serviceContainer.awaitStability();

        // child service expected to be DOWN, parent service to be UP
        Assert.assertEquals(UP, parentService.getState());
        Assert.assertEquals(DOWN, childServiceValue.getValue().getState());

        // bring child service up again
        childServiceValue.getValue().setMode(ServiceController.Mode.ACTIVE);
        serviceContainer.awaitStability();

        // both services expected to be UP
        Assert.assertEquals(UP, parentService.getState());
        Assert.assertEquals(UP, childServiceValue.getValue().getState());

        // bring parent service down
        parentService.setMode(NEVER);
        serviceContainer.awaitStability();

        // parent service expected to be DOWN, child REMOVED
        Assert.assertEquals(DOWN, parentService.getState());
        Assert.assertEquals(REMOVED, childServiceValue.getValue().getState());
    }

    /**
     * Child services - parent can not depend on child.
     */
    @Test
    public void testParentDependsOnChild() throws InterruptedException {
        // install parent service
        final ServiceControllerValue<Void> childServiceValue = new ServiceControllerValue<>();
        ServiceController<Void> parentService = serviceContainer.addService(firstServiceName, new AbstractService<Void>() {
            @Override
            public void start(StartContext context) throws StartException {
                // install child service
                childServiceValue.setValue(context.getChildTarget().addService(secondServiceName, Service.NULL).install());
            }
        }).addDependency(secondServiceName).install();
        serviceContainer.awaitStability();

        // parent couldn't start, because dependencies have to be present before starting
        Assert.assertEquals(DOWN, parentService.getState());
        Assert.assertEquals(PROBLEM, parentService.getSubstate());
        Assert.assertNull(childServiceValue.getValue());
    }

    /**
     * On-demand service.
     */
    @Test
    public void testOnDemandService() throws InterruptedException {
        // install on-demand service
        ServiceController<Void> firstService = serviceContainer.addService(firstServiceName, Service.NULL)
                .setInitialMode(ServiceController.Mode.ON_DEMAND)
                .install();
        serviceContainer.awaitStability();

        // service should be down, because it's not required
        Assert.assertEquals(DOWN, firstService.getState());

        // install second service depending on the first service
        ServiceController<Void> secondService = serviceContainer.addService(secondServiceName, Service.NULL)
                .addDependency(firstServiceName)
                .install();
        serviceContainer.awaitStability();

        // both services should be started
        Assert.assertEquals(UP, firstService.getState());
        Assert.assertEquals(UP, secondService.getState());

        // remove second service
        secondService.setMode(NEVER);
        serviceContainer.awaitStability();

        // both services should be DOWN, because the first service is not required again
        Assert.assertEquals(DOWN, firstService.getState());
        Assert.assertEquals(DOWN, secondService.getState());
    }

    /**
     * On-demand parent service - child service creates dependency on parent.
     */
    @Test
    public void testOnDemandParentService() throws InterruptedException {
        // install on-demand parent service
        final ServiceControllerValue<Void> childServiceValue = new ServiceControllerValue<>();
        ServiceController<Void> parentService = serviceContainer.addService(firstServiceName, new AbstractService<Void>() {
            @Override
            public void start(StartContext context) throws StartException {
                // install child service
                childServiceValue.setValue(context.getChildTarget().addService(secondServiceName, Service.NULL).install());
            }
        }).setInitialMode(ServiceController.Mode.ON_DEMAND).install();
        serviceContainer.awaitStability();

        // parent service expected to be DOWN, because no other service requires it
        Assert.assertEquals(DOWN, parentService.getState());

        // install third service that depends on parent service
        ServiceController<Void> thirdService = serviceContainer.addService(thirdServiceName, Service.NULL)
                .addDependency(firstServiceName)
                .install();
        serviceContainer.awaitStability();

        // all services expected to be UP now
        Assert.assertEquals(UP, parentService.getState());
        Assert.assertEquals(UP, childServiceValue.getValue().getState());
        Assert.assertEquals(UP, thirdService.getState());

        // remove third service
        thirdService.setMode(ServiceController.Mode.REMOVE);
        serviceContainer.awaitStability();

        // parent should stay UP because child is dependent on it
        Assert.assertEquals(UP, parentService.getState());
        Assert.assertEquals(UP, childServiceValue.getValue().getState());
        Assert.assertEquals(REMOVED, thirdService.getState());
    }

    /**
     * Circular dependencies not allowed.
     */
    @Test
    public void testCyclicDependency() throws InterruptedException {
        serviceContainer.addService(firstServiceName, Service.NULL)
                .addDependency(secondServiceName)
                .install();
        try {
            serviceContainer.addService(secondServiceName, Service.NULL)
                    .addDependency(firstServiceName)
                    .install();
            Assert.fail("CircularDependencyException expected");
        } catch (CircularDependencyException e) {
            // ok
        }
    }

}
