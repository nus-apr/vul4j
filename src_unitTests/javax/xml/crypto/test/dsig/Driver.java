/*
 * Copyright 2009 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package javax.xml.crypto.test.dsig;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;

/**
 * Used by ClassLoaderTest
 */
public class Driver {

    public void dsig() throws Exception {

        XMLSignatureFactory fac = XMLSignatureFactory.getInstance
            ("DOM", new org.jcp.xml.dsig.internal.dom.XMLDSigRI());
        long start = System.currentTimeMillis();
        for (int i=0; i<100; i++) {
        fac.newCanonicalizationMethod
            (CanonicalizationMethod.EXCLUSIVE, (C14NMethodParameterSpec) null);
        }
        long end = System.currentTimeMillis();
        long elapsed = end-start;
        System.out.println("Elapsed:"+elapsed);
        System.out.println("dsig succeeded");
    }
}
