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
            String xml = new PaymentFileConverter().convertToXml(inputPath);
            IDataUtil.put(cursor, "pain001Xml", xml);
        } catch (PaymentConversionException e) {
            throw new ServiceException("Payment conversion failed: " + e.getMessage());
        } catch (RuntimeException | LinkageError e) {
            throw new ServiceException("Payment conversion could not run; check Java 21, the packaged schema, and runtime dependencies.");
        } finally {
            cursor.destroy();
        }
    }
}
