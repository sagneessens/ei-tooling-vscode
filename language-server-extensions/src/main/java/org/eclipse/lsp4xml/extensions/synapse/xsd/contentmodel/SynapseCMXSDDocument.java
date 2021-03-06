/*
Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
* WSO2 Inc. licenses this file to you under the Apache License,
* Version 2.0 (the "License"); you may not use this file except
* in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.eclipse.lsp4xml.extensions.synapse.xsd.contentmodel;

import org.apache.xerces.impl.dv.XSSimpleType;
import org.apache.xerces.xs.StringList;
import org.apache.xerces.xs.XSConstants;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSNamedMap;
import org.apache.xerces.xs.XSObject;
import org.apache.xerces.xs.XSObjectList;
import org.apache.xerces.xs.XSSimpleTypeDefinition;
import org.eclipse.lsp4xml.dom.DOMElement;
import org.eclipse.lsp4xml.extensions.contentmodel.model.CMDocument;
import org.eclipse.lsp4xml.extensions.contentmodel.model.CMElementDeclaration;
import org.eclipse.lsp4xml.utils.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * XSD document implementation.
 */
public class SynapseCMXSDDocument implements CMDocument {

    private final XSModel model;

    private final Map<XSElementDeclaration, SynapseCMXSDElementDeclaration> elementMappings;

    private Collection<CMElementDeclaration> elements;

    SynapseCMXSDDocument(XSModel model) {
        this.model = model;
        this.elementMappings = new HashMap<>();
    }

    @Override
    public Collection<CMElementDeclaration> getElements() {
        if (elements == null) {
            elements = new ArrayList<>();
            XSNamedMap map = model.getComponents(XSConstants.ELEMENT_DECLARATION);
            for (int j = 0; j < map.getLength(); j++) {
                XSElementDeclaration elementDeclaration = (XSElementDeclaration) map.item(j);
                collectElement(elementDeclaration, elements);
            }
        }
        return elements;
    }

    /**
     * Fill the given elements list from the given Xerces elementDeclaration.
     *
     * @param elementDeclaration elementDeclaration
     * @param elements           elements
     */
    void collectElement(XSElementDeclaration elementDeclaration, Collection<CMElementDeclaration> elements) {
        if (elementDeclaration.getAbstract()) {
            // element declaration is marked as abstract
            // ex with xsl: <xs:element name="declaration" type="xsl:generic-element-type"
            // abstract="true"/>
            XSObjectList list = model.getSubstitutionGroup(elementDeclaration);
            if (list != null) {
                // it exists elements list bind with this abstract declaration with
                // substitutionGroup
                // ex xsl : <xs:element name="template" substitutionGroup="xsl:declaration">
                for (int i = 0; i < list.getLength(); i++) {
                    XSObject object = list.item(i);
                    if (object.getType() == XSConstants.ELEMENT_DECLARATION) {
                        XSElementDeclaration subElementDeclaration = (XSElementDeclaration) object;
                        collectElement(subElementDeclaration, elements);
                    }
                }
            }
        } else {
            CMElementDeclaration cmElement = getXSDElement(elementDeclaration);
            // check element declaration is not already added (ex: xs:annotation)
            if (!elements.contains(cmElement)) {
                elements.add(cmElement);
            }
        }
    }

    @Override
    public CMElementDeclaration findCMElement(DOMElement element, String namespace) {
        List<DOMElement> paths = new ArrayList<>();
        while (element != null && (namespace == null || namespace.equals(element.getNamespaceURI()))) {
            paths.add(0, element);
            element = element.getParentNode() instanceof DOMElement ? (DOMElement) element.getParentNode() : null;
        }
        CMElementDeclaration declaration = null;
        for (int i = 0; i < paths.size(); i++) {
            DOMElement elt = paths.get(i);
            if (i == 0) {
                declaration = findElementDeclaration(elt.getLocalName(), namespace);
            } else {
                declaration = declaration.findCMElement(elt.getLocalName(), namespace);
            }
            if (declaration == null) {
                break;
            }
        }
        return declaration;
    }

    public CMElementDeclaration findElementDeclaration(String tag, String namespace) {
        for (CMElementDeclaration cmElement : getElements()) {
            if (cmElement.getName().equals(tag)) {
                return cmElement;
            }
        }
        return null;
    }

    private CMElementDeclaration getXSDElement(XSElementDeclaration elementDeclaration) {
        SynapseCMXSDElementDeclaration element = elementMappings.get(elementDeclaration);
        if (element == null) {
            element = new SynapseCMXSDElementDeclaration(this, elementDeclaration);
            elementMappings.put(elementDeclaration, element);
        }
        return element;
    }

    static Collection<String> getEnumerationValues(XSSimpleTypeDefinition typeDefinition) {
        if (typeDefinition != null) {
            if (isBooleanType(typeDefinition)) {
                return StringUtils.TRUE_FALSE_ARRAY;
            }
            StringList enumerations = typeDefinition.getLexicalEnumeration();
            if (enumerations != null) {
                return enumerations;
            }
        }
        return Collections.emptyList();
    }

    static boolean isBooleanType(XSSimpleTypeDefinition typeDefinition) {
        if (typeDefinition instanceof XSSimpleType) {
            return ((XSSimpleType) typeDefinition).getPrimitiveKind() == XSSimpleType.PRIMITIVE_BOOLEAN;
        }
        return false;
    }
}
