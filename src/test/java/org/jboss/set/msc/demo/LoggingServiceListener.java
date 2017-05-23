package org.jboss.set.msc.demo;

import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceListener;

/**
 * @author Tomas Hofman (thofman@redhat.com)
 */
public class LoggingServiceListener<T> implements ServiceListener<T> {

    private Logger logger = Logger.getLogger(getClass());

    @Override
    public void transition(ServiceController controller, ServiceController.Transition transition) {
        logger.infof("transition %s %s", controller.getName(), transition);
    }

    @Override
    public void serviceRemoveRequested(ServiceController controller) {
        logger.infof("serviceRemoveRequested for %s", controller.getName());
    }

    @Override
    public void serviceRemoveRequestCleared(ServiceController controller) {
        logger.infof("serviceRemoveRequestCleared for %s", controller.getName());
    }

    @Override
    public void immediateDependencyUnavailable(ServiceController controller) {
        logger.infof("immediateDependencyUnavailable to %s", controller.getName());
    }

    @Override
    public void immediateDependencyAvailable(ServiceController controller) {
        logger.infof("immediateDependencyAvailable to %s", controller.getName());
    }

    @Override
    public void listenerAdded(ServiceController<? extends T> controller) {
        logger.infof("listenerAdded to %s", controller.getName());
    }

    @Override
    public void dependencyFailed(ServiceController<? extends T> controller) {
        logger.infof("dependencyFailed %s", controller.getName());
    }

    @Override
    public void dependencyFailureCleared(ServiceController<? extends T> controller) {
        logger.infof("dependencyFailureCleared %s", controller.getName());
    }

    @Override
    public void transitiveDependencyUnavailable(ServiceController<? extends T> controller) {
        logger.infof("transitiveDependencyUnavailable %s", controller.getName());
    }

    @Override
    public void transitiveDependencyAvailable(ServiceController<? extends T> controller) {
        logger.infof("transitiveDependencyAvailable %s", controller.getName());
    }
}