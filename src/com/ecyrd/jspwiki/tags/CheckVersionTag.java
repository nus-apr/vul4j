/* 
    JSPWiki - a JSP-based WikiWiki clone.

    Copyright (C) 2001-2002 Janne Jalkanen (Janne.Jalkanen@iki.fi)

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 2.1 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.ecyrd.jspwiki.tags;

import java.io.IOException;

import com.ecyrd.jspwiki.WikiEngine;
import com.ecyrd.jspwiki.WikiPage;
import com.ecyrd.jspwiki.providers.ProviderException;

/**
 *  Does a version check on the page.  Mode is as follows:
 *  <UL>
 *   <LI>latest = Include page content, if the page is the latest version.
 *   <LI>notlatest = Include page content, if the page is NOT the latest version.
 *  </UL>
 *  If the page does not exist, body content is never included.
 *
 *  @author Janne Jalkanen
 *  @since 2.0
 */
public class CheckVersionTag
    extends WikiTagBase
{
    public static final int LATEST    = 0;
    public static final int NOTLATEST = 1;
    public static final int FIRST     = 2;
    public static final int NOTFIRST  = 3;

    private int m_mode;

    public void setMode( String arg )
    {
        if( "latest".equals(arg) )
        {
            m_mode = LATEST;
        }
        else if( "notfirst".equals(arg) )
        {
            m_mode = NOTFIRST;
        }
        else if( "first".equals(arg) )
        {
            m_mode = FIRST;
        }
        else
        {
            m_mode = NOTLATEST;
        }
    }

    public final int doWikiStartTag()
        throws IOException,
               ProviderException
    {
        WikiEngine engine = m_wikiContext.getEngine();
        WikiPage   page   = m_wikiContext.getPage();

        if( page != null && engine.pageExists(page.getName()) )
        {
            int version = page.getVersion();
            boolean include = false;

            WikiPage latest = engine.getPage( page.getName() );

            //log.debug("Doing version check: this="+page.getVersion()+
            //          ", latest="+latest.getVersion());

            switch( m_mode )
            {
              case LATEST:
                include = (version < 0) || (latest.getVersion() == version);
                break;

              case NOTLATEST:
                include = (version > 0) && (latest.getVersion() != version);
                break;

              case FIRST:
                include = (version == 1 ) || (version < 0 && latest.getVersion() == 1);
                break;

              case NOTFIRST:
                include = (version > 1);
                break;
            }

            if( include )
            {
                // log.debug("INCLD");
                return EVAL_BODY_INCLUDE;
            }
        }

        return SKIP_BODY;
    }
}
