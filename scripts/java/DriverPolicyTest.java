import com.rodriguesacai.updriver.RequestPolicy;
import com.rodriguesacai.updriver.GpsPolicy;

public final class DriverPolicyTest {
    private static void check(boolean condition, String message) {
        if(!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for(String action : new String[]{"accept","stage","pickup","finish","history","offer"}) {
            check(RequestPolicy.mayRetry(action,503,0),action+" retries unavailable server");
            check(RequestPolicy.mayRetry(action,0,1),action+" retries lost response");
            check(!RequestPolicy.mayRetry(action,503,2),"bounded attempts");
            for(int status : new int[]{400,401,403,409,422})
                check(!RequestPolicy.mayRetry(action,status,0),"no retry of semantic/auth failures");
        }
        for(String action : new String[]{"occurrence","submit","presence","location"})
            check(!RequestPolicy.mayRetry(action,503,0),"no replay without idempotency: "+action);
        check(RequestPolicy.delayMillis(0,null)==1000,"first backoff");
        check(RequestPolicy.delayMillis(1,null)==3000,"second backoff");
        check(RequestPolicy.delayMillis(0,"60")==30000,"bounded Retry-After");
        check(RequestPolicy.delayMillis(0,Long.toString(Long.MAX_VALUE))==30000,"no Retry-After overflow");
        check(GpsPolicy.shouldSend(20000,20000,100,1000,10,true),"moving point");
        check(!GpsPolicy.shouldSend(5000,70000,100,1000,10,true),"throttle attempts");
        check(!GpsPolicy.shouldSend(20000,20000,0,1000,10,true),"stationary economical");
        check(GpsPolicy.shouldSend(20000,65000,0,1000,10,true),"stationary heartbeat");
        check(!GpsPolicy.shouldSend(20000,70000,100,65000,10,true),"reject cached point");
        check(!GpsPolicy.shouldSend(20000,70000,100,1000,Float.NaN,true),"reject invalid accuracy");
        check(GpsPolicy.shouldSend(20000,90000,0,1000,10,true),"retry fresh stationary point after outage");
        check(GpsPolicy.deliveryInterval("TO_CUSTOMER",101)==30000,"far");
        check(GpsPolicy.deliveryInterval("TO_CUSTOMER",100)==15000,"near");
        check(GpsPolicy.deliveryInterval("TO_CUSTOMER",51)==15000,"near above 50");
        check(GpsPolicy.deliveryInterval("TO_CUSTOMER",50)==5000,"very near");
        check(GpsPolicy.deliveryInterval("TO_CUSTOMER",0)==5000,"destination");
        check(GpsPolicy.deliveryInterval("AT_CUSTOMER",0)==30000,"arrived");
        check(GpsPolicy.deliveryInterval("TO_CUSTOMER",Double.NaN)==30000,"unknown destination");
        check(!GpsPolicy.validDeliveryFix(16000,10),"reject stale delivery fix");
        System.out.println("PASS Android policies: retries, idempotency, backoff, GPS throttle, freshness and reconnect");
    }
}
