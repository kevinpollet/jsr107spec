/**
 *  Copyright (c) 2011 Terracotta, Inc.
 *  Copyright (c) 2011 Oracle and/or its affiliates.
 *
 *  All rights reserved. Use is subject to license terms.
 */

package javax.cache;

import java.security.AccessController;
import java.security.PrivilegedAction;
import javax.cache.spi.AnnotationProvider;
import javax.cache.spi.CachingProvider;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * A factory for creating CacheManagers using the SPI conventions in the JDK's {@link ServiceLoader}
 * <p/>
 * For a provider to be discovered, it's jar must contain a resource called:
 * <pre>
 *   META-INF/services/javax.cache.spi.CachingProvider
 * </pre>
 * containing the class name implementing {@link javax.cache.spi.CachingProvider}
 * <p/>
 * e.g. For the reference implementation:
 * <p/>
 * "javax.cache.implementation.RIServiceFactory"
 * <p/>
 * Also keeps track of all CacheManagers created by the factory. Subsequent calls
 * to {@link #getCacheManager()} return the same CacheManager.
 *
 * @author Yannis Cosmadopoulos
 * @see java.util.ServiceLoader
 * @see javax.cache.spi.CachingProvider
 * @since 1.0
 */
public final class Caching {
    /**
     * The name of the default cache manager.
     * This is the name of the CacheManager returned when {@link #getCacheManager()} is invoked.
     * The default CacheManager is always created.
     */
    public static final String DEFAULT_CACHE_MANAGER_NAME = "__default__";

    /**
     * No public constructor as all methods are static.
     */
    private Caching() {
    }

    /**
     * Get the default cache manager with the default classloader.
     * The default cache manager is named {@link #DEFAULT_CACHE_MANAGER_NAME}
     *
     * @return the default cache manager
     * @throws IllegalStateException if no CachingProvider was found
     */
    public static CacheManager getCacheManager() {
        return getCacheManager(DEFAULT_CACHE_MANAGER_NAME);
    }

    /**
     * Get the default cache manager.
     * The default cache manager is named {@link #DEFAULT_CACHE_MANAGER_NAME}
     *
     * @param classLoader the ClassLoader that should be used in converting values into Java Objects. May be null.
     * @return the default cache manager
     * @throws IllegalStateException if no CachingProvider was found
     */
    public static CacheManager getCacheManager(ClassLoader classLoader) {
        return getCacheManager(classLoader, DEFAULT_CACHE_MANAGER_NAME);
    }

    /**
     * Get a named cache manager using the default cache loader as specified by
     * the implementation (see {@link javax.cache.spi.CachingProvider#getDefaultClassLoader()}
     *
     * @param name the name of the cache manager
     * @return the named cache manager
     * @throws NullPointerException  if name is null
     * @throws IllegalStateException if no CachingProvider was found
     */
    public static CacheManager getCacheManager(String name) {
        return CachingSingleton.INSTANCE.getCacheManager(name);
    }

    /**
     * Get a named cache manager.
     * <p/>
     * The first time a name is used, a new CacheManager is created.
     * Subsequent calls will return the same cache manager.
     * <p/>
     * During creation, the name of the CacheManager is passed through to {@link javax.cache.spi.CachingProvider}
     * so that an implementation it to concrete implementations may use it to point to a specific configuration
     * used to configure the CacheManager. This allows CacheManagers to have different configurations. For example,
     * one CacheManager might be configured for standalone operation and another might be configured to participate
     * in a cluster.
     * <p/>
     * Generally, It makes sense that a CacheManager is associated with a ClassLoader. I.e. all caches emanating
     * from the CacheManager, all code including key and value classes must be present in that ClassLoader.
     * <p/>
     * Secondly, the Caching may be in a different ClassLoader than the
     * CacheManager (i.e. the Caching may be shared in an application server setting).
     * <p/>
     * For this purpose a ClassLoader may be specified. If specified it will be used for all conversion between
     * values and Java Objects. While Java's in-built serialization may be used other schemes may also be used.
     * Either way the specified ClassLoader will be used.
     * <p/>
     * The name parameter may be used to associate a configuration with this CacheManager instance.
     *
     * @param classLoader the ClassLoader that should be used in converting values into Java Objects.
     * @param name        the name of this cache manager
     * @return the new cache manager
     * @throws NullPointerException  if classLoader or name is null
     * @throws IllegalStateException if no CachingProvider was found
     */
    public static CacheManager getCacheManager(ClassLoader classLoader, String name) {
        return CachingSingleton.INSTANCE.getCacheManager(classLoader, name);
    }

    /**
     * Reclaims all resources obtained from this factory.
     * <p/>
     * All cache managers obtained from the factory are shutdown.
     * <p/>
     * Subsequent requests from this factory will return different cache managers than would have been obtained before
     * shutdown. So for example
     * <pre>
     *  CacheManager cacheManager = CacheFactory.getCacheManager();
     *  assertSame(cacheManager, CacheFactory.getCacheManager());
     *  CacheFactory.close();
     *  assertNotSame(cacheManager, CacheFactory.getCacheManager());
     * </pre>
     *
     * @throws CachingShutdownException if any of the individual shutdowns failed
     */
    public static void close() throws CachingShutdownException {
        CachingSingleton.INSTANCE.close();
    }

    /**
     * Reclaims all resources for a ClassLoader from this factory.
     * <p/>
     * All cache managers linked to the specified CacheLoader obtained from the factory are shutdown.
     *
     * @param classLoader the class loader for which managers will be shut down
     * @return true if found, false otherwise
     * @throws CachingShutdownException if any of the individual shutdowns failed
     */
    public static boolean close(ClassLoader classLoader) throws CachingShutdownException {
        return CachingSingleton.INSTANCE.close(classLoader);
    }

    /**
     * Reclaims all resources for a ClassLoader from this factory.
     * <p/>
     * the named cache manager obtained from the factory is closed.
     *
     * @param classLoader the class loader for which managers will be shut down
     * @param name        the name of the cache manager
     * @return true if found, false otherwise
     */
    public static boolean close(ClassLoader classLoader, String name) throws CachingShutdownException {
        return CachingSingleton.INSTANCE.close(classLoader, name);
    }

    /**
     * Indicates whether a optional feature is supported by this implementation
     *
     * @param optionalFeature the feature to check for
     * @return true if the feature is supported
     */
    public static boolean isSupported(OptionalFeature optionalFeature) {
        switch (optionalFeature) {
            case ANNOTATIONS: {
                final AnnotationProvider annotationProvider = ServiceFactoryHolder.INSTANCE.getAnnotationProvider();
                return annotationProvider != null && annotationProvider.isSupported(optionalFeature);
            }
            default: {
                return ServiceFactoryHolder.INSTANCE.getServiceFactory().isSupported(optionalFeature);
            }
        }
    }

    /**
     * Holds the ServiceFactory
     */
    private enum ServiceFactoryHolder {
        /**
         * The singleton.
         */
        INSTANCE;

        private final CachingProvider serviceFactory;
        private final AnnotationProvider annotationProvider;

        private ServiceFactoryHolder() {
            serviceFactory = AccessController.doPrivileged(new PrivilegedAction<CachingProvider>() {

                @Override
                public CachingProvider run() {
                    ServiceLoader<CachingProvider> serviceLoader = ServiceLoader.load(CachingProvider.class);
                    Iterator<CachingProvider> it = serviceLoader.iterator();
                    if (it.hasNext()) {
                        return it.next();
                    } else {
                        return null;
                    }
                }
            });

            annotationProvider = AccessController.doPrivileged(new PrivilegedAction<AnnotationProvider>() {

                @Override
                public AnnotationProvider run() {
                    ServiceLoader<AnnotationProvider> serviceLoader = ServiceLoader.load(AnnotationProvider.class);
                    Iterator<AnnotationProvider> it = serviceLoader.iterator();
                    if (it.hasNext()) {
                        return it.next();
                    } else {
                        return null;
                    }
                }
            });
        }

        public CachingProvider getServiceFactory() {
            if (serviceFactory == null) {
                throw new IllegalStateException("No CachingProvider found in classpath.");
            }
            return serviceFactory;
        }

        public AnnotationProvider getAnnotationProvider() {
            return annotationProvider;
        }
    }

    /**
     * Used to track CacheManagers created using Caching.
     */
    private static final class CachingSingleton {
        /**
         * The singleton
         */
        public static final CachingSingleton INSTANCE = new CachingSingleton(ServiceFactoryHolder.INSTANCE.getServiceFactory());

        private final Map<ClassLoader, Map<String, CacheManager>> cacheManagers = new HashMap<ClassLoader, Map<String, CacheManager>>();
        private final CachingProvider cachingProvider;

        private CachingSingleton(CachingProvider cachingProvider) {
            this.cachingProvider = cachingProvider;
        }

        /**
         * Get a named cache manager using the default cache loader as specified by
         * the implementation (see {@link javax.cache.spi.CachingProvider#getDefaultClassLoader()}
         *
         * @param name the name of the cache manager
         * @return the named cache manager
         * @throws NullPointerException  if name is null
         * @throws IllegalStateException if no CachingProvider was found
         */
        public CacheManager getCacheManager(String name) {
            ClassLoader classLoader = cachingProvider.getDefaultClassLoader();
            return getCacheManager(classLoader, name);
        }

        /**
         * Get the cache manager for the specified name and class loader.
         * <p/>
         * If there is no cache manager associated, it is created.
         *
         * @param classLoader associated with the cache manager.
         * @param name        the name of the cache manager
         * @return the new cache manager
         * @throws NullPointerException  if classLoader or name is null
         * @throws IllegalStateException if no CachingProvider was found
         */
        public CacheManager getCacheManager(ClassLoader classLoader, String name) {
            if (classLoader == null) {
                throw new NullPointerException("classLoader");
            }
            if (name == null) {
                throw new NullPointerException("name");
            }
            synchronized (cacheManagers) {
                Map<String, CacheManager> map = cacheManagers.get(classLoader);
                if (map == null) {
                    map = new HashMap<String, CacheManager>();
                    cacheManagers.put(classLoader, map);
                }
                CacheManager cacheManager = map.get(name);
                if (cacheManager == null) {
                    cacheManager = cachingProvider.createCacheManager(classLoader, name);
                    map.put(name, cacheManager);
                }
                return cacheManager;
            }
        }

        /**
         * Reclaims all resources obtained from this factory.
         * <p/>
         * All cache managers obtained from the factory are shutdown.
         * <p/>
         * Subsequent requests from this factory will return different cache managers than would have been obtained before
         * shutdown.
         *
         * @throws CachingShutdownException if any of the individual shutdowns failed
         */
        public void close() throws CachingShutdownException {
            synchronized (cacheManagers) {
                IdentityHashMap<CacheManager, Exception> failures = new IdentityHashMap<CacheManager, Exception>();
                for (Map<String, CacheManager> cacheManagerMap : cacheManagers.values()) {
                    try {
                        shutdown(cacheManagerMap);
                    } catch (CachingShutdownException e) {
                        failures.putAll(e.getFailures());
                    }
                }
                cacheManagers.clear();
                if (!failures.isEmpty()) {
                    throw new CachingShutdownException(failures);
                }
            }
        }

        /**
         * Reclaims all resources for a ClassLoader from this factory.
         * <p/>
         * All cache managers linked to the specified CacheLoader obtained from the factory are shutdown.
         *
         * @param classLoader the class loader for which managers will be shut down
         * @return true if found, false otherwise
         * @throws CachingShutdownException if any of the individual shutdowns failed
         */
        public boolean close(ClassLoader classLoader) throws CachingShutdownException {
            Map<String, CacheManager> cacheManagerMap;
            synchronized (cacheManagers) {
                cacheManagerMap = cacheManagers.remove(classLoader);
            }
            if (cacheManagerMap == null) {
                return false;
            } else {
                shutdown(cacheManagerMap);
                return true;
            }
        }

        /**
         * Reclaims all resources for a ClassLoader from this factory.
         * <p/>
         * the named cache manager obtained from the factory is shutdown.
         *
         * @param classLoader the class loader for which managers will be shut down
         * @param name        the name of the cache manager
         * @return true if found, false otherwise
         * @throws CachingShutdownException if there is a problem shutting down a CacheManager
         */
        public boolean close(ClassLoader classLoader, String name) throws CachingShutdownException {
            CacheManager cacheManager;
            synchronized (cacheManagers) {
                Map<String, CacheManager> cacheManagerMap = cacheManagers.get(classLoader);
                cacheManager = cacheManagerMap.remove(name);
                if (cacheManagerMap.isEmpty()) {
                    cacheManagers.remove(classLoader);
                }
            }
            if (cacheManager == null) {
                return false;
            } else {
                cacheManager.shutdown();
                return true;
            }
        }

        private void shutdown(Map<String, CacheManager> cacheManagerMap) throws CachingShutdownException {
            IdentityHashMap<CacheManager, Exception> failures = new IdentityHashMap<CacheManager, Exception>();
            for (CacheManager cacheManager : cacheManagerMap.values()) {
                try {
                    cacheManager.shutdown();
                } catch (Exception e) {
                    failures.put(cacheManager, e);
                }
            }
            if (!failures.isEmpty()) {
                throw new CachingShutdownException(failures);
            }
        }
    }
}
