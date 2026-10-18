package com.adgn.backend.identity.security;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Bounded process-local admission control, never an authorization/qualification cache. */
public final class LoginRateLimiter {
    private record Window(long started,int count) { }
    private final Map<String,Window> windows=new HashMap<>();
    private final Clock clock;
    private final int ipLimit,accountLimit;
    public LoginRateLimiter(Clock clock,int ipLimit,int accountLimit) {
        if(ipLimit<1 || accountLimit<1)throw new IllegalArgumentException("Positive login limits required");
        this.clock=clock; this.ipLimit=ipLimit; this.accountLimit=accountLimit;
    }
    public synchronized void admit(String address,String account) {
        long now=clock.instant().getEpochSecond();
        windows.entrySet().removeIf(e -> now-e.getValue().started()>=60);
        take("ip:"+address,ipLimit,now);
        take("account:"+account,accountLimit,now);
    }
    private void take(String key,int limit,long now) {
        var old=windows.get(key);
        if(old==null && windows.size()>=10000) throw new IdentityFailure(429,"RATE_LIMITED",60);
        if(old!=null && old.count()>=limit)
            throw new IdentityFailure(429,"RATE_LIMITED",Math.max(1,60-(now-old.started())));
        windows.put(key,new Window(old==null ? now : old.started(),old==null ? 1 : old.count()+1));
    }
}
