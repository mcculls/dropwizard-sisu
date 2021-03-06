package io.tesla.dropwizard.sisu;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Path;
import javax.ws.rs.ext.Provider;

import org.eclipse.sisu.BeanEntry;
import org.eclipse.sisu.inject.BeanLocator;
import org.eclipse.sisu.space.BeanScanning;
import org.eclipse.sisu.space.ClassSpace;
import org.eclipse.sisu.space.SpaceModule;
import org.eclipse.sisu.space.URLClassSpace;
import org.eclipse.sisu.wire.WireModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.sun.jersey.spi.inject.InjectableProvider;
import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.config.Bootstrap;
import com.yammer.dropwizard.config.Configuration;
import com.yammer.dropwizard.config.Environment;
import com.yammer.dropwizard.lifecycle.Managed;
import com.yammer.dropwizard.tasks.Task;
import com.yammer.metrics.core.HealthCheck;

public abstract class SisuService<T extends Configuration> extends Service<T> {

  private static final Logger logger = LoggerFactory.getLogger(SisuService.class);

  @Override
  public void initialize(Bootstrap<T> bootstrap) {
  }

  @Override
  public void run(T configuration, Environment environment)
      throws Exception {
    Injector injector = createInjector(configuration, environment);
    injector.injectMembers(this);
    runWithInjector(configuration, environment, injector);
  }

  private Injector createInjector(final T configuration, final Environment environment) {
    ClassSpace space = new URLClassSpace(getClass().getClassLoader());
    SpaceModule spaceModule = new SpaceModule(space, BeanScanning.CACHE);
    List<Module> modules = new ArrayList<Module>();
    modules.add(spaceModule);
    modules.add(new Module() {
      public void configure(Binder binder) {
        binder.bind(Environment.class).toInstance(environment);
      }
    });

    for (Module m : modules(configuration)) {
      System.out.println("Adding " + m);
      modules.add(m);
    }
    return Guice.createInjector(new WireModule(modules));
  }

  //
  // Allow the application to customize the modules
  //
  protected Module[] modules(T configuration) {
    return new Module[] {};
  }

  protected void customize(T configuration, Environment environment) {
  }

  private void runWithInjector(T configuration, Environment environment, Injector injector)
      throws Exception {
    //
    // Allow customization of the environment
    //
    customize(configuration, environment);
    BeanLocator locator = injector.getInstance(BeanLocator.class);
    addHealthChecks(environment, locator);
    addProviders(environment, locator);
    addInjectableProviders(environment, locator);
    addResources(environment, locator);
    addTasks(environment, locator);
    addManaged(environment, locator);
  }

  private void addManaged(Environment environment, BeanLocator locator) {
    for (BeanEntry<Annotation, Managed> managedBeanEntry : locator.locate(Key.get(Managed.class))) {
      Managed managed = managedBeanEntry.getValue();
      environment.manage(managed);
      logger.info("Added managed: " + managed);
    }
  }

  private void addTasks(Environment environment, BeanLocator locator) {
    for (BeanEntry<Annotation, Task> taskBeanEntry : locator.locate(Key.get(Task.class))) {
      Task task = taskBeanEntry.getValue();
      environment.addTask(task);
      logger.info("Added task: " + task);
    }
  }

  private void addHealthChecks(Environment environment, BeanLocator locator) {
    for (BeanEntry<Annotation, HealthCheck> healthCheckBeanEntry : locator.locate(Key.get(HealthCheck.class))) {
      HealthCheck healthCheck = healthCheckBeanEntry.getValue();
      environment.addHealthCheck(healthCheck);
      logger.info("Added healthCheck: " + healthCheck);
    }
  }

  @SuppressWarnings("rawtypes")
  private void addInjectableProviders(Environment environment, BeanLocator locator) {
    for (BeanEntry<Annotation, InjectableProvider> injectableProviderBeanEntry : locator.locate(Key.get(InjectableProvider.class))) {
      InjectableProvider injectableProvider = injectableProviderBeanEntry.getValue();
      environment.addProvider(injectableProvider);
      logger.info("Added injectableProvider: " + injectableProvider);
    }
  }

  private void addProviders(Environment environment, BeanLocator locator) {
    for (BeanEntry<Annotation, Provider> providerBeanEntry : locator.locate(Key.get(Provider.class))) {
      Provider provider = providerBeanEntry.getValue();
      environment.addProvider(provider);
      logger.info("Added provider class: " + provider);
    }
  }

  private void addResources(Environment environment, BeanLocator locator) {
    //
    // This is probably not the most efficient way to collect all classes annotated with Path.class
    //
    for (BeanEntry<Annotation, Object> resourceBeanEntry : locator.locate(Key.get(Object.class))) {
      Class<?> impl = resourceBeanEntry.getImplementationClass();
      if (impl != null && impl.isAnnotationPresent(Path.class)) {
        Object resource = resourceBeanEntry.getValue();
        environment.addResource(resource);
        logger.info("Added resource class: " + resource);
      }
    }
  }
}
