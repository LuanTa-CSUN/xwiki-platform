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
package com.xpn.xwiki.doc;

import java.net.URL;

import org.jmock.Mock;
import org.xwiki.bridge.DocumentAccessBridge;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.test.AbstractBridgedXWikiComponentTestCase;
import com.xpn.xwiki.web.XWikiURLFactory;

/**
 * Unit tests for {@link DefaultDocumentAccessBridge}.
 * 
 * @version $Id$
 */
public class DefaultDocumentAccessBridgeTest extends AbstractBridgedXWikiComponentTestCase
{
    private DocumentAccessBridge documentAccessBridge;

    private Mock mockXWiki;

    private Mock mockURLFactory;

    protected void setUp() throws Exception
    {
        super.setUp();

        this.mockXWiki = mock(XWiki.class);
        this.mockURLFactory = mock(XWikiURLFactory.class);

        getContext().setURLFactory((XWikiURLFactory) mockURLFactory.proxy());
        getContext().setDoc(new XWikiDocument("Space", "Page"));
        getContext().setWiki((XWiki) mockXWiki.proxy());

        this.documentAccessBridge = (DocumentAccessBridge) getComponentManager().lookup(DocumentAccessBridge.ROLE);
    }

    public void testGetUrl() throws Exception
    {
        this.mockXWiki.stubs().method("getDocument").will(returnValue(new XWikiDocument("Space", "Page")));
        this.mockURLFactory.stubs().method("createURL").will(returnValue(new URL("http://127.0.0.1/xwiki/bn/view/Space/Page#id")));
        this.mockURLFactory.stubs().method("getURL").will(returnValue("/xwiki/bn/view/Space/Page#id"));

        assertEquals("/xwiki/bn/view/Space/Page#id", this.documentAccessBridge.getURL("", "view", "", "id"));
        assertEquals("/xwiki/bn/view/Space/Page#id", this.documentAccessBridge.getURL(null, "view", "", "id"));
    }
}
