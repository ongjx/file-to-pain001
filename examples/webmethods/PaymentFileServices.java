package example.webmethods;

import com.example.payments.conversion.PaymentConversionException;
import com.example.payments.conversion.PaymentFileConverter;
import com.wm.app.b2b.server.ServiceException;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataUtil;

/** Example only: create a Java service in Designer and use its generated service wrapper. */
public final class PaymentFileServices {
    private PaymentFileServices() { }

    public static void convertFile(IData pipeline) throws ServiceException {
        IDataCursor cursor = pipeline.getCursor();
        try {
            IDataUtil.remove(cursor, "pain001Xml");
            Object input = IDataUtil.get(cursor, "inputPath");
            if (!(input instanceof String inputPath) || inputPath.isBlank()) {
                throw new ServiceException("inputPath must be a nonblank String naming a server-readable CSV file.");
            }
            // Check field presence separately so an explicitly supplied null is invalid.
            boolean profilePresent = cursor.first("profileId");
            Object profile = profilePresent ? cursor.getValue() : null;
            if (profilePresent && (!(profile instanceof String) || ((String) profile).isBlank())) {
                throw new ServiceException("profileId must be a nonblank String naming a known input profile.");
            }
            PaymentFileConverter converter = new PaymentFileConverter();
            String xml = profilePresent ? converter.convertToXml(inputPath, (String) profile) : converter.convertToXml(inputPath);
            IDataUtil.put(cursor, "pain001Xml", xml);
        } catch (PaymentConversionException e) {
            throw new ServiceException("Payment conversion failed: " + e.getMessage());
        } catch (RuntimeException | LinkageError e) {
            ServiceException failure = new ServiceException("Payment conversion could not run ("
                    + e.getClass().getSimpleName() + "); check Java 21, the packaged schema, and runtime dependencies.");
            failure.initCause(e);
            throw failure;
        } finally {
            cursor.destroy();
        }
    }
}
