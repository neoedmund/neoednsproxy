package neoe.dns.model;

import java.math.BigDecimal;
import java.util.Date;
//import neoe.db.ITable;

/**
 *
 * @author neoe
 */
public class DnsRec /*implements ITable*/ {

    public String domain;
    public byte[] reply;
    public Date updated;
    public BigDecimal hit=BigDecimal.ZERO;
    public BigDecimal access=BigDecimal.ZERO;
    public BigDecimal disabled=BigDecimal.ZERO;

    public boolean canFitToDB() {
        return (domain != null && reply != null) && domain.length() <= 200 && reply.length <= 800;
    }
}
