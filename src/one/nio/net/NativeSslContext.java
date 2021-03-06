/*
 * Copyright 2015 Odnoklassniki Ltd, Mail.Ru Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package one.nio.net;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ServiceConfigurationError;
import java.util.StringTokenizer;

class NativeSslContext extends SslContext {
    static final NativeSslContext DEFAULT;

    long ctx;

    NativeSslContext() throws SSLException {
        this.ctx = ctxNew();
    }

    @Override
    public void close() {
        if (ctx != 0) {
            ctxFree(ctx);
            ctx = 0;
        }
    }

    @Override
    public void setProtocols(String protocols) {
        int enabled = 0;

        StringTokenizer st = new StringTokenizer(protocols.toLowerCase(), " ,:+", false);
        while (st.hasMoreTokens()) {
            String protocol = st.nextToken();
            if (protocol.equals("compression")) {
                enabled |= 0x00020000;
            } else if (protocol.equals("sslv2")) {
                enabled |= 0x01000000;
            } else if (protocol.equals("sslv3")) {
                enabled |= 0x02000000;
            } else if (protocol.equals("tlsv1")) {
                enabled |= 0x04000000;
            } else if (protocol.equals("tlsv1.1")) {
                enabled |= 0x08000000;
            } else if (protocol.equals("tlsv1.2")) {
                enabled |= 0x10000000;
            }
        }

        int all = 0x00020000 + 0x01000000 + 0x02000000 + 0x04000000 + 0x08000000 + 0x10000000;
        clearOptions(enabled);
        setOptions(all - enabled);
    }

    @Override
    public native void setCiphers(String ciphers) throws SSLException;

    @Override
    public native void setCertificate(String certFile, String privateKeyFile) throws SSLException;

    @Override
    public native void setTicketKey(byte[] ticketKey) throws SSLException;

    private native void setOptions(int options);
    private native void clearOptions(int options);

    private static native void init();
    private static native long ctxNew() throws SSLException;
    private static native void ctxFree(long ctx);

    private static byte[] readFile(String fileName) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(fileName, "r");
        try {
            byte[] data = new byte[(int) raf.length()];
            raf.readFully(data);
            return data;
        } finally {
            raf.close();
        }
    }

    static {
        init();

        try {
            DEFAULT = new NativeSslContext();

            String certFile = System.getProperty("one.nio.ssl.certFile");
            String privateKeyFile = System.getProperty("one.nio.ssl.privateKeyFile");
            if (certFile != null || privateKeyFile != null) {
                DEFAULT.setCertificate(certFile, privateKeyFile);
            }

            String protocols = System.getProperty("one.nio.ssl.protocols");
            if (protocols != null) {
                DEFAULT.setProtocols(protocols);
            }

            String ciphers = System.getProperty("one.nio.ssl.ciphers");
            if (ciphers != null) {
                DEFAULT.setCiphers(ciphers);
            }

            String ticketKeyFile = System.getProperty("one.nio.ssl.ticketKeyFile");
            if (ticketKeyFile != null) {
                DEFAULT.setTicketKey(readFile(ticketKeyFile));
            }
        } catch (IOException e) {
            throw new ServiceConfigurationError("Could not create OpenSSL context", e);
        }
    }
}
