package com.example.payments;

import java.io.StringReader;
import java.util.Collections;
import java.util.Iterator;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

final class XmlChecks {
    static final String NS = "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09";
    static final String ROOT = "/p:Document/p:CstmrCdtTrfInitn";

    static void independentlyValidate(String xml) throws Exception {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        // Independent validator and parsing path, rather than calling the production XML writer again.
        var schema = factory.newSchema(XmlChecks.class.getResource("/xsd/pain.001.001.09.xsd"));
        schema.newValidator().validate(new StreamSource(new StringReader(xml)));
    }

    static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    static String value(Document xml, String path) throws Exception {
        var xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String prefix) { return "p".equals(prefix) ? NS : XMLConstants.NULL_NS_URI; }
            public String getPrefix(String uri) { return NS.equals(uri) ? "p" : null; }
            public Iterator<String> getPrefixes(String uri) { return NS.equals(uri) ? Collections.singleton("p").iterator() : Collections.emptyIterator(); }
        });
        return xpath.evaluate(path, xml);
    }
}
