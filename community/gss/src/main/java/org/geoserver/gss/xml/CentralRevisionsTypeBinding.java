/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gss.xml;

import java.util.List;

import javax.xml.namespace.QName;

import org.geoserver.gss.CentralRevisionsType;
import org.geotools.xml.AbstractComplexBinding;
import org.geotools.xml.ElementInstance;
import org.geotools.xml.Node;

public class CentralRevisionsTypeBinding extends AbstractComplexBinding {

    public QName getTarget() {
        return GSS.CentralRevisionsType;
    }

    public Class getType() {
        return CentralRevisionsType.class;
    }
    
    @Override
    public Object parse(ElementInstance instance, Node node, Object value) throws Exception {
        List<CentralRevisionsType.LayerRevision> layerRevisions = node.getChildValues(CentralRevisionsType.LayerRevision.class);
        CentralRevisionsType result = new CentralRevisionsType();
        result.getLayerRevisions().addAll(layerRevisions);
        
        return result;
    }
    
    @Override
    public Object getProperty(Object object, QName name) throws Exception {
        if(name.getLocalPart().equals("LayerRevision")) {
            return ((CentralRevisionsType) object).getLayerRevisions();
        }
        return super.getProperty(object, name);
    }

}
