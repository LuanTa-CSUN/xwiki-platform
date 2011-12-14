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
 *
 */
package org.xwiki.security.internal;

import java.util.Collections;

import org.jmock.Expectations;
import org.junit.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.observation.EventListener;
import org.xwiki.security.AccessLevel;
import org.xwiki.security.RightCache;
import org.xwiki.security.RightCacheEntry;
import org.xwiki.security.RightLoader;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertTrue;
import static org.xwiki.security.Right.COMMENT;
import static org.xwiki.security.Right.EDIT;

public class DefaultRightLoaderTest extends AbstractTestCase
{
    @Test 
    public void testRightLoader()
    {
        DocumentReference userX = docRefResolver.resolve("wikiY:XWiki.userX");
        DocumentReference userY = docRefResolver.resolve("wikiY:XWiki.userY");

        MockDocument wikiDocument = new MockDocument("xwiki:XWiki.XWikiPreferences", "xwiki:XWiki.Admin");
        MockDocument allGroupDocument = MockDocument.newGroupDocument("xwiki:XWiki.XWikiAllGroup", 
                                                                      new String[]{"wikiY:XWiki.userX", 
                                                                                   "wikiY:XWiki.userY" });
        wiki.add(new MockDocument(userX, "xwiki:XWiki.Admin")
                 .allowLocal(asList(EDIT),
                             asList("wikiY:XWiki.userX"),
                             Collections.<String>emptyList()))
            .add(new MockDocument(userY, "xwiki:XWiki.Admin")
                 .allowLocal(asList(EDIT),
                             asList("wikiY:XWiki.userY"),
                             Collections.<String>emptyList()))
            .add(wikiDocument)
            .add(allGroupDocument);

        try {
            mockery.checking(new Expectations() {{
                allowing(mockGroupService)
                    .getAllGroupsNamesForMember("wikiY:XWiki.userX", Integer.MAX_VALUE, 0, xwikiContext);
                will(returnValue(asList("XWiki.XWikiAllGroup")));
                allowing(mockGroupService)
                    .getAllGroupsNamesForMember("wikiY:XWiki.userY", Integer.MAX_VALUE, 0, xwikiContext);
                will(returnValue(asList("XWiki.XWikiAllGroup")));
            }});

            RightLoader loader = getComponentManager().lookup(RightLoader.class);
            RightCache  cache  = getComponentManager().lookup(RightCache.class);
            RightCacheInvalidator invalidator = getComponentManager().lookup(RightCacheInvalidator.class);

            AccessLevel edit = AccessLevel.DEFAULT_ACCESS_LEVEL.clone();
            edit.allow(EDIT);

            AccessLevel level = loader.load(userX, userX);
            System.out.println("Level is " + level + ", expected " + edit);
            assertTrue(level.equals(edit));

            RightCacheEntry entry = cache.get(cache.getRightCacheKey(userX), cache.getRightCacheKey(userX));
            assertTrue(entry != null);
            assertTrue(entry.equals(level));

            entry = cache.get(cache.getRightCacheKey(userX));
            assertTrue(entry != null);
            assertTrue(entry.getType() == RightCacheEntry.Type.HAVE_OBJECTS);

            entry = cache.get(cache.getRightCacheKey(userX.getParent()));
            assertTrue(entry != null);
            assertTrue(entry.getType() == RightCacheEntry.Type.HAVE_NO_OBJECTS);

            entry = cache.get(cache.getRightCacheKey(userX.getParent().getParent()));
            assertTrue(entry != null);
            assertTrue(entry.getType() == RightCacheEntry.Type.HAVE_OBJECTS);

            ((EventListener) invalidator).onEvent(null, wikiDocument, null);

            entry = cache.get(cache.getRightCacheKey(userX), cache.getRightCacheKey(userX));
            assertTrue(entry == null);

            entry = cache.get(cache.getRightCacheKey(userX));
            assertTrue(entry == null);

            entry = cache.get(cache.getRightCacheKey(userX.getParent()));
            assertTrue(entry == null);

            entry = cache.get(cache.getRightCacheKey(userX.getParent().getParent()));
            assertTrue(entry == null);

            wikiDocument.denyGlobal(asList(COMMENT),
                                    Collections.<String>emptyList(),
                                    asList("wikiY:XWiki.XWikiAllGroup"));

            AccessLevel editNoComment = edit.clone();
            editNoComment.deny(COMMENT);
            level = loader.load(userX, userX);
            assertTrue(level.equals(editNoComment));

            mockery.checking(new Expectations() {{
                allowing(mockGroupService).getAllMembersNamesForGroup("xwiki:XWiki.GroupX", 100, 0, xwikiContext);
                will(returnValue(asList("wikiY:XWiki.userX")));
            }});
            MockDocument group = MockDocument.newGroupDocument("XWiki.GroupX", new String[] {"wikiY:XWiki.userX" } );
            wiki.add(group);
            ((EventListener) invalidator).onEvent(null, group, null);

            entry = cache.get(cache.getRightCacheKey(userX));
            assertTrue("Invalidating cache after group update", entry == null);

            entry = cache.get(cache.getRightCacheKey(userX), cache.getRightCacheKey(userX));
            assertTrue("Invalidating cache after group update", entry == null);

            entry = cache.get(cache.getRightCacheKey(userX.getParent()));
            assertTrue("Invalidating cache after group update", entry != null);

            entry = cache.get(cache.getRightCacheKey(userX.getParent().getParent()));
            assertTrue("Invalidating cache after group update", entry != null);
            

        } catch (Exception e) {
            LOG.error("Caught exception.", e);
            assert false;
        }
    }

}
