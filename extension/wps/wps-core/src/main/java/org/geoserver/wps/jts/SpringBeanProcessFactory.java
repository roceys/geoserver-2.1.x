/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wps.jts;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geotools.factory.FactoryIteratorProvider;
import org.geotools.feature.NameImpl;
import org.geotools.process.ProcessFactory;
import org.geotools.process.Processors;
import org.geotools.util.SimpleInternationalString;
import org.opengis.feature.type.Name;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * A {@link ProcessFactory} meant to be declared in a Spring context. It will look in the
 * application context for beans implementing the specified marker interface and annotated with
 * {@link DescribeProcess}, {@link DescribeParameter} and {@link DescribeResult}. Each bean will
 * have a "execute" method taking the process parameters as arguments and returning the results
 */
public class SpringBeanProcessFactory extends AnnotationDrivenProcessFactory implements
        ApplicationContextAware, ApplicationListener {

    Map<String, Class> classMap;

    Map<String, String> beanMap;

    Class markerInterface;

    ApplicationContext applicationContext;

    FactoryIteratorProvider iterator;

    public SpringBeanProcessFactory(String title, String namespace, Class markerInterface) {
        super(new SimpleInternationalString(title), namespace);
        this.markerInterface = markerInterface;

        // create an iterator that will register this factory into SPI
        iterator = new FactoryIteratorProvider() {

            public <T> Iterator<T> iterator(Class<T> category) {
                if (ProcessFactory.class.isAssignableFrom(category)) {
                    return (Iterator<T>) Collections.singletonList(SpringBeanProcessFactory.this)
                            .iterator();
                } else {
                    return null;
                }
            }
        };
        
        Processors.addProcessFactory(this);

    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;

        // loads all of the beans implementing the marker interface
        String[] beanNames = applicationContext.getBeanNamesForType(markerInterface, true, true);
        // build a name to class and name to bean name maps
        classMap = new HashMap<String, Class>();
        beanMap = new HashMap<String, String>();
        for (String beanName : beanNames) {
            Class c = applicationContext.getType(beanName);
            String name = c.getSimpleName();
            if (name.endsWith("Process")) {
                name = name.substring(0, name.indexOf("Process"));
            }
            classMap.put(name, c);
            beanMap.put(name, beanName);
        }

    }

    @Override
    protected DescribeProcess getProcessDescription(Name name) {
        Class c = classMap.get(name.getLocalPart());
        if (c == null) {
            return null;
        } else {
            return (DescribeProcess) c.getAnnotation(DescribeProcess.class);
        }
    }

    @Override
    protected Method method(String className) {
        Class c = classMap.get(className);
        if (c != null) {
            for (Method m : c.getMethods()) {
                if ("execute".equals(m.getName())) {
                    return m;
                }
            }
        }
        return null;
    }

    public Set<Name> getNames() {
        Set<Name> result = new LinkedHashSet<Name>();
        List<String> names = new ArrayList<String>(classMap.keySet());
        Collections.sort(names);
        for (String name : names) {
            result.add(new NameImpl(namespace, name));
        }
        return result;
    }

    @Override
    protected Object createProcessBean(Name name) {
        String beanName = beanMap.get(name.getLocalPart());
        if (beanName == null) {
            return null;
        }
        return applicationContext.getBean(beanName);
    }

    public void onApplicationEvent(ApplicationEvent event) {
        // add and remove the process factory as necessary
        if (event instanceof ContextRefreshedEvent) {
            Processors.addProcessFactory(this);
        } else if (event instanceof ContextClosedEvent) {
            Processors.removeProcessFactory(this);
        }
    }
}
