import com.rodriguesacai.updriver.OfferPushPolicy;
import java.util.HashMap;
import java.util.Map;

/** Pure JVM regression checks for data-only FCM messages from up-offer-push. */
public final class OfferPushPolicyTest {
    private static final String JOB = "00000000-0000-4000-8000-000000000001";
    private static final String OFFER = "00000000-0000-4000-8000-000000000002";
    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        Map<String,String> event = new HashMap<>();
        event.put("upSource","independent");
        event.put("rideId","supabase:"+JOB);
        event.put("offerId",OFFER);
        event.put("title","Nova corrida UP");
        event.put("body","Uma entrega está disponível. Abra o UP Entregas.");
        check(OfferPushPolicy.isIndependentOffer(event),"server data-only offer accepted");
        check("Nova corrida UP".equals(OfferPushPolicy.title(event.get("title"))),"title from data payload");
        check(OfferPushPolicy.body(event.get("body")).startsWith("Uma entrega"),"body from data payload");
        check(!OfferPushPolicy.isIndependentOffer(null),"no data message is not a UP offer");
        event.put("upSource","legacy");check(!OfferPushPolicy.isIndependentOffer(event),"reject legacy Firebase events");
        event.put("upSource","independent");
        event.put("offerId","untrusted");check(!OfferPushPolicy.isIndependentOffer(event),"reject forged offer identifier");
        event.put("offerId",OFFER);
        event.put("rideId","legacy:"+JOB);check(!OfferPushPolicy.isIndependentOffer(event),"reject legacy job identifier");
        event.put("rideId","supabase:"+JOB);
        check(OfferPushPolicy.isIndependentOffer(event),"accept valid source and both ids");
        check(!OfferPushPolicy.title("\n\r ").isEmpty(),"fallback for blank title");
        check(!OfferPushPolicy.title("a\nb").contains("\n"),"strip control characters");
        check(OfferPushPolicy.title("x".repeat(250)).length()==80,"cap title");
        check(OfferPushPolicy.body("x".repeat(250)).length()==180,"cap message");
        check(OfferPushPolicy.notificationId(OFFER)==OfferPushPolicy.notificationId(OFFER),"stable id suppresses repeated alert");
        check(OfferPushPolicy.notificationId(OFFER)>0,"valid Android notification id");
        System.out.println("PASS data-only UP FCM policy: source isolation, ids, content limits and stable notifications");
    }
}
