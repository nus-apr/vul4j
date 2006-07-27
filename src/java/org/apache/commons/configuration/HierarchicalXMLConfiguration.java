/*
 * Copyright 2004-2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.configuration;

/**
 * A specialized hierarchical configuration class that is able to parse XML
 * documents.
 *
 * <p>The parsed document will be stored keeping its structure. The contained
 * properties can be accessed using all methods supported by the base class
 * <code>HierarchicalConfiguration</code>.
 *
 * @since commons-configuration 1.0
 *
 * @author J&ouml;rg Schaible
 * @author Oliver Heger
 * @version $Revision$, $Date$
 * @deprecated This class is deprecated. Use <code>XMLConfiguration</code>
 * instead, which supports all features this class had offered before.
 */
public class HierarchicalXMLConfiguration extends XMLConfiguration
{
    /**
     * The serial version UID.
     */
    private static final long serialVersionUID = 5597530014798917521L;
}
