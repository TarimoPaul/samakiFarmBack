package com.samaki.farm.common.ratelimit;

import com.samaki.farm.common.exception.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kikomo rahisi cha maombi kwa dirisha lisilobadilika (fixed window),
 * kinachotumika kwa endpoints ambazo mtu YEYOTE anaweza kuzifikia bila
 * token: kujisajili na kuingia.
 *
 * MIPAKA - ijulikane wazi kabla ya production:
 *  * Iko KWENYE KUMBUKUMBU YA INSTANCE MOJA. Ukiendesha instance zaidi ya
 *    moja (mfano ECS yenye task mbili), kila moja ina hesabu yake - kikomo
 *    halisi kinakuwa mara mbili. Kwa hilo inahitajika Redis au sawa.
 *  * Ufunguo ni anwani ya IP, ambayo mtandao wa NAT unaweza kuishirikisha
 *    kwa watu wengi.
 *
 * Kwa hatua hii ni kizuizi cha kutosha dhidi ya majaribio ya nguvu
 * (brute force) na usajili wa kiotomatiki - si ulinzi kamili.
 */
@Component
public class RateLimiter {

    /** Zaidi ya hapa, tunasafisha rekodi zilizoisha muda kabla ya kuongeza mpya. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(long startMillis, int count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * Inahesabu ombi moja kwa ufunguo husika, na kutupa
     * TooManyRequestsException kama kikomo kimezidiwa ndani ya dirisha.
     *
     * @param key         kitambulisho cha mwombaji, mfano "login:41.79.x.x"
     * @param maxRequests maombi yanayoruhusiwa ndani ya dirisha moja
     * @param window      urefu wa dirisha
     */
    public void check(String key, int maxRequests, Duration window) {
        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();

        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(e -> now - e.getValue().startMillis() > windowMillis);
        }

        Window updated = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startMillis() > windowMillis) {
                return new Window(now, 1);
            }
            return new Window(existing.startMillis(), existing.count() + 1);
        });

        if (updated.count() > maxRequests) {
            throw new TooManyRequestsException(
                    "Maombi mengi mno. Subiri kidogo kisha ujaribu tena.");
        }
    }

    /** Inafuta hesabu ya ufunguo - inaitwa baada ya login iliyofanikiwa. */
    public void reset(String key) {
        windows.remove(key);
    }
}
