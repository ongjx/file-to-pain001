package com.example.payments;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;

/** Host provider reproducing the older server Xerces' unsupported security properties. */
public final class LegacySchemaFactory extends SchemaFactory {
    private final SchemaFactory delegate = SchemaFactory.newDefaultInstance();

    @Override public boolean isSchemaLanguageSupported(String language) {
        return XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(language);
    }
    @Override public void setFeature(String name, boolean value) throws SAXNotRecognizedException, org.xml.sax.SAXNotSupportedException {
        delegate.setFeature(name, value);
    }
    @Override public void setProperty(String name, Object value) throws SAXNotRecognizedException {
        throw new SAXNotRecognizedException("Property '" + name + "' is not recognized.");
    }
    @Override public void setErrorHandler(ErrorHandler handler) { delegate.setErrorHandler(handler); }
    @Override public ErrorHandler getErrorHandler() { return delegate.getErrorHandler(); }
    @Override public void setResourceResolver(LSResourceResolver resolver) { delegate.setResourceResolver(resolver); }
    @Override public LSResourceResolver getResourceResolver() { return delegate.getResourceResolver(); }
    @Override public Schema newSchema(Source[] schemas) throws SAXException { return delegate.newSchema(schemas); }
    @Override public Schema newSchema() throws SAXException { return delegate.newSchema(); }
}
