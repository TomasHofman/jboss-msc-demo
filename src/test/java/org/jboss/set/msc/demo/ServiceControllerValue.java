package org.jboss.set.msc.demo;

import org.jboss.msc.service.ServiceController;
import org.jboss.msc.value.Value;

/**
 * @author Tomas Hofman (thofman@redhat.com)
 */
public class ServiceControllerValue<T> implements Value<ServiceController<T>> {

    private ServiceController<T> service;

    @Override
    public ServiceController<T> getValue() throws IllegalStateException, IllegalArgumentException {
        return service;
    }

    public void setValue(ServiceController<T> service) {
        this.service = service;
    }
}
