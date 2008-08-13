/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.cache.jbosscache.internal;

import java.text.MessageFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeEvicted;
import org.jboss.cache.notifications.annotation.NodeModified;
import org.jboss.cache.notifications.event.NodeEvictedEvent;
import org.jboss.cache.notifications.event.NodeModifiedEvent;
import org.jboss.cache.Cache;
import org.jboss.cache.CacheFactory;
import org.jboss.cache.DefaultCacheFactory;
import org.jboss.cache.Fqn;
import org.xwiki.cache.jbosscache.internal.event.JBossCacheCacheEntryEvent;
import org.xwiki.cache.util.AbstractCache;

/**
 * Implements {@link org.xwiki.cache.Cache} based on JBossCache.
 * 
 * @param <T> the class of the data stored in the cache.
 * @version $Id: $
 */
@CacheListener
public class JBossCacheCache<T> extends AbstractCache<T>
{
    /**
     * The root FQN.
     */
    public static final Fqn<String> ROOT_FQN = Fqn.fromString("/xwiki");

    /**
     * The logging tool.
     */
    private static final Log LOG = LogFactory.getLog(JBossCacheCache.class);

    /**
     * The name of the key when data is store in the node.
     */
    private static final String DATA_KEY = "data";

    /**
     * The JBossCache cache configuration.
     */
    private JBossCacheCacheConfiguration jbosscacheConfiguration;

    /**
     * The JBoss cache.
     */
    private Cache<String, T> cache;

    /**
     * The state of the node before modification.
     */
    private ConcurrentMap<String, Map<String, T>> preEventData = new ConcurrentHashMap<String, Map<String, T>>();

    /**
     * Create and initialize the cache.
     * 
     * @param jbosscacheConfiguration the configuration to use to create the cache.
     */
    public void initialize(JBossCacheCacheConfiguration jbosscacheConfiguration)
    {
        this.jbosscacheConfiguration = jbosscacheConfiguration;

        this.configuration = jbosscacheConfiguration.getCacheConfiguration();

        CacheFactory<String, T> factory = new DefaultCacheFactory<String, T>();
        this.cache = factory.createCache(this.jbosscacheConfiguration.getJBossCacheConfiguration());

        this.cache.addCacheListener(this);

        this.cache.create();
        this.cache.start();
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.cache.Cache#remove(java.lang.String)
     */
    public void remove(String key)
    {
        this.cache.removeNode(new Fqn<String>(ROOT_FQN, key));
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.cache.Cache#set(java.lang.String, java.lang.Object)
     */
    public void set(String key, T obj)
    {
        this.cache.put(new Fqn<String>(ROOT_FQN, key), DATA_KEY, obj);
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.cache.Cache#get(java.lang.String)
     */
    public T get(String key)
    {
        return this.cache.get(new Fqn<String>(ROOT_FQN, key), DATA_KEY);
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.cache.Cache#removeAll()
     */
    public void removeAll()
    {
        this.cache.removeNode(ROOT_FQN);
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.cache.util.AbstractCache#dispose()
     */
    @Override
    public void dispose()
    {
        super.dispose();

        this.cache.stop();
        this.cache.destroy();
    }

    // ////////////////////////////////////////////////////////////////
    // Events
    // ////////////////////////////////////////////////////////////////

    /**
     * @param event the eviction event.
     */
    @NodeEvicted
    public void nodeEvicted(NodeEvictedEvent event)
    {
        if (!event.getFqn().isChildOf(ROOT_FQN)) {
            if (LOG.isInfoEnabled()) {
                LOG.info(MessageFormat.format("The node {0} should not has been evicted", event.getFqn()));
            }

            return;
        }

        String key = event.getFqn().getLastElementAsString();

        if (!event.isPre()) {
            // FIXME: Trying to get the evicted value using public api disrupt JBoss eviction process so I can't return
            // and destroy it
            cacheEntryRemoved(key, null);
        }
    }

    /**
     * @param event the modification event.
     */
    @NodeModified
    public void nodeModified(NodeModifiedEvent event)
    {
        if (!event.getFqn().isChildOf(ROOT_FQN)) {
            if (LOG.isInfoEnabled()) {
                LOG.info(MessageFormat.format("The node {0} should not has been modified", event.getFqn()));
            }

            return;
        }

        Map<String, T> data = event.getData();

        String key = event.getFqn().getLastElementAsString();

        if (event.isPre()) {
            this.preEventData.put(key, data);
        } else {
            if (data.containsKey(DATA_KEY)) {
                if (event.getModificationType() == NodeModifiedEvent.ModificationType.REMOVE_DATA) {
                    cacheEntryRemoved(key, this.preEventData.get(key).get(DATA_KEY));
                } else {
                    cacheEntryInserted(key, data.get(DATA_KEY));
                }
            }

            this.preEventData.remove(key);
        }
    }

    /**
     * Dispatch data insertion event.
     * 
     * @param key the entry key.
     * @param value the entry value.
     */
    private void cacheEntryInserted(String key, T value)
    {
        JBossCacheCacheEntryEvent<T> event =
            new JBossCacheCacheEntryEvent<T>(new JBossCacheCacheEntry<T>(this, key, value));

        Map<String, T> preMap = this.preEventData.get(key);

        if (preMap.containsKey(DATA_KEY)) {
            sendEntryModifiedEvent(event);
        } else {
            sendEntryAddedEvent(event);
        }
    }

    /**
     * Dispatch data remove event.
     * 
     * @param key the entry key.
     * @param value the entry value.
     */
    private void cacheEntryRemoved(String key, T value)
    {
        JBossCacheCacheEntryEvent<T> event =
            new JBossCacheCacheEntryEvent<T>(new JBossCacheCacheEntry<T>(this, key, value));

        sendEntryRemovedEvent(event);
    }
}
