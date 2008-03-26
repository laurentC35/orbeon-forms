/**
 *  Copyright (C) 2006 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.control.controls;

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.processor.XFormsResourceServer;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.saxon.om.NodeInfo;

import java.util.List;

/**
 * Represents an xforms:output control.
 */
public class XFormsOutputControl extends XFormsValueControl {

    // Optional display format
    private String format;

    // Value attribute
    private String valueAttribute;

    // XForms 1.1 mediatype attribute
    private String mediatypeAttribute;

    private boolean urlNorewrite;

    public XFormsOutputControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String id) {
        super(containingDocument, parent, element, name, id);
        this.format = element.attributeValue(new QName("format", XFormsConstants.XXFORMS_NAMESPACE));
        this.mediatypeAttribute = element.attributeValue("mediatype");
        this.valueAttribute = element.attributeValue("value");
        this.urlNorewrite = XFormsUtils.resolveUrlNorewrite(element);
    }

    protected void evaluateValue(PipelineContext pipelineContext) {
        final String value;
        if (valueAttribute == null) {
            // Get value from single-node binding
            final NodeInfo currentSingleNode = bindingContext.getSingleNode();
            if (currentSingleNode != null)
                value = XFormsInstance.getValueForNodeInfo(currentSingleNode);
            else
                value = "";
        } else {
            // Value comes from the XPath expression within the value attribute
            final List currentNodeset = bindingContext.getNodeset();
            if (currentNodeset != null && currentNodeset.size() > 0) {

                value = XPathCache.evaluateAsString(pipelineContext,
                        currentNodeset, bindingContext.getPosition(),
                        valueAttribute, containingDocument.getNamespaceMappings(getControlElement()), bindingContext.getInScopeVariables(),
                        XFormsContainingDocument.getFunctionLibrary(), getContextStack().getFunctionContext(), null, getLocationData());
            } else {
                value = "";
            }
        }
        setValue(value);
    }

    protected void evaluateExternalValue(PipelineContext pipelineContext) {

        final String internalValue = getValue(pipelineContext);
        final String updatedValue;
        if (mediatypeAttribute != null && mediatypeAttribute.startsWith("image/")) {
            // Handle image mediatype
            final String type = getType();
            if (internalValue != null && internalValue.length() > 0 && internalValue.trim().length() > 0) {
                if (type == null || type.equals(XMLConstants.XS_ANYURI_EXPLODED_QNAME) || type.equals(XFormsConstants.XFORMS_ANYURI_EXPLODED_QNAME)) {
                    // xs:anyURI type
                    if (!urlNorewrite) {
                        // We got a URI and we need to rewrite it to an absolute URI since XFormsResourceServer will have to read and stream
                        final String rewrittenURI = XFormsUtils.resolveResourceURL(pipelineContext, getControlElement(), internalValue, true);
                        updatedValue = proxyURI(pipelineContext, rewrittenURI);
                    } else {
                        // Otherwise we leave the value as is
                        updatedValue = internalValue;
                    }
                } else if (XMLConstants.XS_BASE64BINARY_EXPLODED_QNAME.equals(type)) {
                    // xs:base64Binary type

                    final String uri = NetUtils.base64BinaryToAnyURI(pipelineContext, internalValue, NetUtils.SESSION_SCOPE);
                    updatedValue = proxyURI(pipelineContext, uri);

                } else {
                    updatedValue = "";
                }
            } else {
                updatedValue = "";
            }
        } else {
            // Not an image
            updatedValue = internalValue;
        }

        setExternalValue(updatedValue);
    }

    /**
     * Transform an URI accessible from the server into a URI accessible from the client. The mapping expires with the
     * session.
     *
     * @param pipelineContext   PipelineContext to obtain session
     * @param uri               server URI to transform
     * @return                  client URI
     */
    private String proxyURI(PipelineContext pipelineContext, String uri) {

        // Create a digest, so that for a given URI we always get the same key
        final String digest = SecureUtils.digestString(uri, "MD5", "hex");

        // Get session
        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        final ExternalContext.Session session = externalContext.getSession(true);// NOTE: We force session creation here. Should we? What's the alternative?

        if (session != null) {
            // Store mapping into session
            session.getAttributesMap().put(XFormsResourceServer.DYNAMIC_RESOURCES_SESSION_KEY + digest,
                    new XFormsResourceServer.DynamicResource(uri, mediatypeAttribute, -1, System.currentTimeMillis()));
        }

        // Rewrite new URI to absolute path without the context
        return externalContext.getResponse().rewriteResourceURL(XFormsResourceServer.DYNAMIC_RESOURCES_PATH + digest,
                ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT);
    }

    public void evaluateDisplayValue(PipelineContext pipelineContext) {
        if (valueAttribute == null) {
            evaluateDisplayValueUseFormat(pipelineContext, format);
        } else {
            setDisplayValue(null);
        }
    }

    public String getEscapedExternalValue(PipelineContext pipelineContext) {
        if (mediatypeAttribute != null && mediatypeAttribute.startsWith("image/")) {
            // We just need to prepend the context
            final String externalValue = getExternalValue(pipelineContext);
            if (externalValue != null && !externalValue.trim().equals("")) {
                final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                return externalContext.getRequest().getContextPath() + externalValue;
            } else {
                return externalValue;
            }
        } else if (mediatypeAttribute != null && mediatypeAttribute.equals("text/html")) {
            // Rewrite the HTML value
            return rewriteHTMLValue(pipelineContext, getExternalValue(pipelineContext));
        } else {
            return getExternalValue(pipelineContext);
        }
    }

    public String getMediatypeAttribute() {
        return mediatypeAttribute;
    }

    public String getValueAttribute() {
        return valueAttribute;
    }

    public String getType() {
        // No type information is returned when there is a value attribute

        // Question: what if we have both @ref and @value? Should a type still be provided? This is not supported in
        // XForms 1.1 but we do support it, with the idea that the bound node does not provide the value but provides
        // mips. Not sure if the code below makes sense after all then.
        return (valueAttribute == null) ? super.getType() : null;
    }
}
