package com.custos.modules.externalnotify.client;

import com.custos.modules.execution.ProcessSanitizer;
import com.custos.shared.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * ป้องกัน SSRF เบื้องต้นสำหรับ URL ปลายทางที่ผู้ดูแลระบบตั้งค่าใน Vault
 * - รับเฉพาะ http/https และไม่มี user-info ใน URL
 * - ปฏิเสธ link-local (รวม cloud metadata 169.254.169.254), wildcard และ multicast
 * - อนุญาต loopback / private network โดยตั้งใจ เพราะระบบภายในและ dev ใช้ localhost
 */
@Component
@RequiredArgsConstructor
public class OutboundUrlValidator {

    private final ProcessSanitizer processSanitizer;

    public void validate(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new BadRequestException("Domain ต้องใช้ http หรือ https เท่านั้น");
        }
        if (uri.getUserInfo() != null) {
            throw new BadRequestException("Domain ต้องไม่มี username/password ใน URL");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BadRequestException("รูปแบบ Domain ไม่ถูกต้อง");
        }
        try {
            processSanitizer.validateHostname(host);
        } catch (SecurityException e) {
            throw new BadRequestException("รูปแบบ Domain ไม่ถูกต้อง");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new BadRequestException("ไม่พบ Domain ที่ระบุ");
        }
        for (InetAddress address : addresses) {
            if (address.isLinkLocalAddress() || address.isAnyLocalAddress() || address.isMulticastAddress()) {
                throw new BadRequestException("ไม่อนุญาตให้เรียก Domain ปลายทางนี้");
            }
        }
    }
}
