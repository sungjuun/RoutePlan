package com.routeplan.contentimport.integration;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class WebUrlSecurityPolicy {

    public void requirePublicHttpUrl(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https"))
                || host.isBlank()
                || uri.getUserInfo() != null
                || (port != -1 && port != 80 && port != 443)
                || host.equals("localhost")
                || host.endsWith(".localhost")
                || host.endsWith(".local")) {
            throw unsafe();
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) throw unsafe();
            for (InetAddress address : addresses) {
                if (!isPublic(address)) throw unsafe();
            }
        } catch (UnknownHostException exception) {
            throw unsafe();
        }
    }

    private boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (bytes.length == 16) {
            int first = bytes[0] & 0xff;
            if ((first & 0xfe) == 0xfc) return false; // IPv6 unique-local fc00::/7
        }
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            if (first == 0 || first == 10 || first == 127 || first >= 224) return false;
            if (first == 169 && second == 254) return false;
            if (first == 172 && second >= 16 && second <= 31) return false;
            if (first == 192 && second == 168) return false;
            if (first == 100 && second >= 64 && second <= 127) return false;
        }
        return true;
    }

    private RoutePlanException unsafe() {
        return new RoutePlanException(ErrorCode.CONTENT_IMPORT_UNSUPPORTED_URL,
                "내부 네트워크에 접근할 수 있는 URL은 가져올 수 없습니다.");
    }
}
