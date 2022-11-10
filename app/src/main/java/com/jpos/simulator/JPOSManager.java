package com.jpos.simulator;

import org.jdom2.JDOMException;
import org.jpos.core.Configuration;
import org.jpos.core.ConfigurationException;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.ISOUtil;
import org.jpos.iso.packager.GenericPackager;
import org.jpos.iso.packager.XMLPackager;
import org.jpos.q2.QBeanSupport;
import org.jpos.space.Space;
import org.jpos.space.SpaceFactory;
import org.jpos.space.SpaceUtil;
import org.jpos.util.FSDMsg;
import org.jpos.util.LogEvent;
import org.jpos.util.Logger;
import org.jpos.util.NameRegistrar;
import org.jpos.util.TPS;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.util.StringTokenizer;

/**
 * Esta clase se encarga de realizar la conexion por un metodo seguro
 * Administra la conexion y tambien el byteo del ISOMsg para envio al
 * ip y puerto definidio emulando space's, q2, channels y demas utilizados en
 * la arquitectura de jPOS
 */
public class JPOSManager extends QBeanSupport implements Runnable {

    public static final long DEFAULT_TIMEOUT = 5000;
    public static final int SO_TIMEOUT = 900000;
    private final Space sp;
    private final int headerLength = 0;
    private long relax;
    private String queue, requestVersion, responseVersion;
    private boolean trace = false;
    private long timeout;
    private TPS tps;
    private long lastSuccessfullOperation;
    private long statusTimeout;
    private long diagnosticsTimeout = 300000L;

    public JPOSManager() {
        super();
        sp = SpaceFactory.getSpace();
    }

    public void setConfiguration(Configuration cfg) throws ConfigurationException {
        super.setConfiguration(cfg);
        setRelax(cfg.getLong("relax", 1000L));
        this.trace = cfg.getBoolean("trace", false);
        timeout = cfg.getLong("timeout", DEFAULT_TIMEOUT);
        diagnosticsTimeout = cfg.getLong("diagnostics-timeout", 300000L); // default to 5 minutes
    }

    public synchronized void setRelax(long relax) {
        this.relax = Math.max(relax, 1000);          // don't hog full Q2
        setModified(true);
    }

    public void startService() {
        statusTimeout = cfg.getLong("status-timeout", 180000L);
        tps = new TPS(true);
        queue = getName() + ".queue.";
        String[] hosts = cfg.getAll("host:port");
        SpaceUtil.wipe(sp, "host:port");
        for (String host : hosts) {
            Thread t = new Thread(this);
            t.setName("ThalesAdapter-" + getName() + "-" + host);
            t.start();
            sp.out("host:port", host);
        }
        NameRegistrar.register(getName(), this);
    }

    public void stopService() {
        NameRegistrar.unregister(getName());
        tps.stop();
    }

    public void destroyService() throws Exception {
        new Thread() {
            public void run() {
                try {
                    ISOUtil.sleep(6000);   // let the threads die
                } catch (Exception e) {
                    getLog().error(e);
                }
            }
        }.start();
    }

    @Override
    public void run() {
        long lastTick = 0L;
        Socket socket = null;
        DataInputStream serverIn = null;
        DataOutputStream serverOut = null;
        String hostport = (String) sp.in("host:port", 60000L);
        if (hostport == null)
            return;
        StringTokenizer st = new StringTokenizer(hostport, ":");
        String host = st.nextToken();
        int port = Integer.parseInt(st.nextToken());
        long lastSessionSuccess = System.currentTimeMillis();

        while (running()) {
            try {
                socket = new Socket(host, port);
                socket.setSoTimeout(SO_TIMEOUT);
                serverIn = new DataInputStream(
                        new BufferedInputStream(socket.getInputStream())
                );
                serverOut = new DataOutputStream(
                        new BufferedOutputStream(socket.getOutputStream(), 4096)
                );
                getLog().info("connected " + socket);
                while (running()) {
                    byte[] request = (byte[]) sp.in(queue, 500L);
                    if (request != null) {
                        if (trace)
                            System.out.println("Request: " + ISOUtil.dumpString(request));
                        send(serverOut, request);
                    } else if (System.currentTimeMillis() - lastSessionSuccess > diagnosticsTimeout) {
                        request = createDiagnosticsRequest();
                        send(serverOut, request);
                        lastSessionSuccess = System.currentTimeMillis();
                        getLog().info("HSM ping " + socket);
                    }
                    if (request != null || serverIn.available() > 0) {
                        do {
                            byte[] r = receive(serverIn);
                            if (r != null) {
                                if (trace)
                                    System.out.println("Response: " + ISOUtil.dumpString(r));
                                tps.tick();
                                lastSuccessfullOperation = lastSessionSuccess = System.currentTimeMillis();
                                sp.out(getResponseQueue(r), r, 60000L);
                                long now = System.currentTimeMillis();
                            }
                        } while (serverIn.available() > 0);
                    }
                }
            } catch (ConnectException e) {
                String msg = e.getMessage() + " (" + host + ":" + port + ")";
                getLog().warn(msg);
            } catch (EOFException e) {
                getLog().warn("EOF ", e);
            } catch (SocketException e) {
                getLog().warn(e.getMessage());
            } catch (Throwable e) {
                getLog().warn("unexpected exception", e);
            } finally {
                close(serverIn);
                close(serverOut);
                close(socket);
            }
            relax();
        }
    }

    private int getMessageLength(DataInputStream serverIn) throws IOException {
        byte[] b = new byte[2];
        serverIn.readFully(b, 0, 2);
        return ((int) b[0] & 0xFF) << 8 |
                (int) b[1] & 0xFF;
    }

    private String getResponseQueue(byte[] b) {
        StringBuffer sb = new StringBuffer(queue);
        sb.append(new String(b, 0, headerLength));
        return sb.toString();
    }

    private byte[] receive(DataInputStream serverIn) throws IOException {
        int len = getMessageLength(serverIn);
        byte[] response = new byte[len];
        serverIn.readFully(response, 0, response.length);
        return response;
    }

    private byte[] createDiagnosticsRequest() throws JDOMException, IOException, ISOException {
        String request = createRequest("NC").pack();
        request = request.replaceAll(" ", "");
        StringBuilder sb = new StringBuilder();
        sb.append(buildHeader());
        sb.append(request);
        return sb.toString().getBytes();
    }

    private String buildHeader() {
        long l = (long) Math.pow(10, headerLength);
        return Long.toString(l + (SpaceUtil.nextLong(sp, this + ".seq") % l)
        ).substring(1);
    }

    public FSDMsg createRequest(String command) {
        FSDMsg req = new FSDMsg("jar:hsm/hsm-");
        if (command != null)
            req.set("command", command);
        return req;
    }

    public void relax() {
        ISOUtil.sleep(relax);
    }

    protected void sendMessageLength(DataOutputStream serverOut, int len) throws IOException {
        serverOut.write(len >> 8);
        serverOut.write(len);
    }

    private void send(DataOutputStream serverOut, byte[] request) throws IOException {
        sendMessageLength(serverOut, request.length);
        serverOut.write(request);
        serverOut.flush();
    }

    public ISOMsg sendISOMsg(ISOMsg request, String xmlRequest) throws ISOException {
        return command(request, xmlRequest);
    }

    private ISOMsg command(ISOMsg request, String xmlRequest) throws ISOException {
        return command(request, timeout, xmlRequest);
    }

    public ISOMsg command(ISOMsg request, long timeout, String xmlRequest) throws ISOException {
        LogEvent evt = trace ? getLog().createTrace() : null;
        ISOMsg resp;
        if (trace)
            evt.addMessage(request);

        try {
//            String s = command(String.valueOf(request.pack()), evt, timeout);
            System.out.println("ISOMsg request: " + request);
            System.out.println("ISOMsg request.pack(): " + request.pack());
            System.out.println("ISOMsg request.pack().toString(): " + request.pack().toString());
            System.out.println("ISOMsg requestToXML: " + xmlRequest);
            String s = command(request.pack().toString(), evt, timeout, xmlRequest);
            GenericPackager gp = new GenericPackager("jar:assets/verifone.xml");
            ISOPackager ip = (ISOPackager) gp;
            resp = new ISOMsg();
            resp.setPackager(getDefaultPackager());
//            resp.setPackager(ip);
            resp.unpack(s.getBytes());
            if (trace && evt != null)
                evt.addMessage(resp);
        } catch (Exception e) {
            if (trace && evt != null)
                evt.addMessage(e);
            else
                getLog().error(e);
            if (e instanceof ISOException) {
                throw (ISOException) e;
            }
            throw new ISOException("ERROR", e);
        } finally {
            if (trace && evt != null)
                Logger.log(evt);
        }
        return resp;
    }

    private String command(String request, LogEvent evt, long timeout, String requestToXml) throws ISOException {
//    private String command(byte [] request, LogEvent evt, long timeout) throws ISOException {
        request = request.replaceAll(" ", "");
        StringBuffer sb = new StringBuffer();
        String header = buildHeader();
//        sb.append("6000000000");
//        sb.append(request);
        sb.append(requestToXml);
        System.out.println("request.toString(): " + request);
        System.out.println("sb.toString(): " + sb);
        System.out.println("toXml: " + requestToXml);
        long start = System.currentTimeMillis();
        if (trace)
            evt.addMessage(" request: '" + request + "'");
        String response = null;
        byte[] b = request(sb.toString().getBytes(), timeout);
        if (b != null && b.length > headerLength) {
            if (!header.equals(new String(b, 0, headerLength))) {
                getLog().error(
                        "warning: expected header='" + header +
                                "', received header='" + new String(b, 0, headerLength) + "'"
                );
            }
            response = new String(b, headerLength, b.length - headerLength);
            if (trace)
                evt.addMessage("response: '" + response + "'");
        }
        if (trace)
            evt.addMessage(" elapsed: " + (System.currentTimeMillis() - start) + "ms");

        if (response == null) {
            throw new ISOException("Timeout");
        }
        return response;
    }

    private byte[] request(byte[] command, long timeout) {
        sp.out(queue, command, timeout);
        return (byte[]) sp.in(getResponseQueue(command), timeout);
    }

    private XMLPackager getDefaultPackager() throws ISOException, NoSuchFieldException, IllegalAccessException {
        XMLPackager p = new XMLPackager();
        try {
            p.setXMLParserFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            p.setXMLParserFeature("http://xml.org/sax/features/external-general-entities", true);
            p.setXMLParserFeature("http://xml.org/sax/features/external-parameter-entities", true);
            return p;
        } catch (SAXException e) {
            throw new ISOException("Error creating XMLPackager", e);
        }
    }
}
