/* Copyright (c) 2001 - 2011 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.monitor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.geoserver.monitor.MonitorServletRequest.MonitorInputStream;

import com.mockrunner.mock.web.MockServletInputStream;

import junit.framework.TestCase;

public class MonitorServletRequestTest extends TestCase {

    
    public void testInputStream() throws Exception {
        byte[] data = data();
        MockServletInputStream mock = new MockServletInputStream(data);
        
        MonitorInputStream in = new MonitorInputStream(mock);
        byte[] read = read(in);
        
        assertEquals(data.length, read.length);
        
        byte[] buffer = in.getData();
        assertEquals(1024, buffer.length);
        
        for (int i = 0; i < buffer.length; i++) {
            assertEquals(data[i], buffer[i]);
        }
    }
    
    static byte[] data() throws IOException {
        InputStream in = MonitorServletRequest.class.getResourceAsStream("wms.xml");
        return read(in);
    }
    
    static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n = 0;
        while ((n = in.read(buf)) > 0) {
            bytes.write(buf, 0, n);
        }
        
        in.close();
        return bytes.toByteArray();
    }
}
