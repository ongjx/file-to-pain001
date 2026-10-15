package com.example.payments.xml;

import com.example.payments.conversion.PaymentConversionException;
import com.example.payments.pain001.v09.Document;
import com.example.payments.pain001.v09.ObjectFactory;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.ValidationEvent;
import java.io.StringWriter;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.xml.sax.SAXException;

public final class Pain001XmlWriter {
    private static final Pattern CONSTRAINT = Pattern.compile("^(cvc-[\\w.-]+):");

    // These two objects are reusable; a marshaller is created for each request.
    private static final class Binding {
        static final JAXBContext CONTEXT = context();
        static final Schema SCHEMA = schema();

        private static JAXBContext context() {
            try {
                return JAXBContext.newInstance(Document.class);
            } catch (JAXBException e) {
                throw new IllegalStateException("Cannot initialize pain.001 JAXB binding.", e);
            }
        }

        private static Schema schema() {
            URL resource = Pain001XmlWriter.class.getResource("/xsd/pain.001.001.09.xsd");
            if (resource == null) {
                throw new IllegalStateException("Bundled pain.001 schema is missing.");
            }
            try {
                // Host servers can register older Xerces providers that lack JAXP security properties.
                // Select the Java runtime's provider without changing host-wide JAXP configuration.
                SchemaFactory factory = SchemaFactory.newDefaultInstance();
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                return factory.newSchema(resource);
            } catch (SAXException e) {
                throw new IllegalStateException("Cannot initialize bundled pain.001 schema.", e);
            }
        }
    }

    public String write(Document document) throws PaymentConversionException {
        StringBuilder validationFailure = new StringBuilder();
        try {
            Marshaller marshaller = Binding.CONTEXT.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.setSchema(Binding.SCHEMA);
            marshaller.setEventHandler(event -> {
                if (event.getSeverity() == ValidationEvent.WARNING) {
                    return true;
                }
                // The provider's full diagnostic can contain account/payment values.
                Matcher code = CONSTRAINT.matcher(event.getMessage());
                validationFailure.append("pain.001 schema validation failed");
                if (code.find()) {
                    validationFailure.append(" (").append(code.group(1)).append(')');
                }
                if (event.getLocator() != null && event.getLocator().getObject() != null) {
                    validationFailure.append(" near ").append(event.getLocator().getObject().getClass().getSimpleName());
                }
                validationFailure.append("; check required elements, lengths, and schema constraints.");
                return false;
            });
            StringWriter xml = new StringWriter();
            marshaller.marshal(new ObjectFactory().createDocument(document), xml);
            return xml.toString();
        } catch (JAXBException e) {
            if (!validationFailure.isEmpty()) {
                throw new PaymentConversionException(PaymentConversionException.Kind.VALIDATION, validationFailure.toString());
            }
            throw new PaymentConversionException(PaymentConversionException.Kind.PROCESSING, "Cannot serialize the payment document.", e);
        }
    }
}
